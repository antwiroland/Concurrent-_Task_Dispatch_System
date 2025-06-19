package com.sikawofie.concurqueue;

import com.sikawofie.concurqueue.consumer.Worker;
import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.monitor.Monitor;
import com.sikawofie.concurqueue.producer.Producer;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurQueue {
    private static final Logger logger = LoggerFactory.getLogger(ConcurQueue.class);
    private static final int PRODUCER_COUNT = 3;
    private static final int WORKER_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 100;
    private static final int MAX_TASKS = 100;

    private final BlockingQueue<Task> taskQueue;
    private final ExecutorService producerExecutor;
    private final ExecutorService workerExecutor;
    private final TaskStateTracker stateTracker;
    private final Monitor monitor;

    public ConcurQueue() {
        this.taskQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY);
        this.stateTracker = new TaskStateTracker();

        this.producerExecutor = Executors.newFixedThreadPool(PRODUCER_COUNT, new NamedThreadFactory("Producer"));
        for (int i = 1; i <= PRODUCER_COUNT; i++) {
            producerExecutor.submit(new Producer("Producer-" + i, taskQueue, stateTracker));
        }

        this.workerExecutor = Executors.newFixedThreadPool(WORKER_COUNT, new NamedThreadFactory("Worker"));
        for (int i = 1; i <= WORKER_COUNT; i++) {
            workerExecutor.submit(new Worker("Worker-" + i, taskQueue, stateTracker));
        }

        this.monitor = new Monitor(taskQueue, workerExecutor, stateTracker);
        new Thread(monitor, "Monitor").start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Shutdown-Hook"));

        logger.info("""
            ConcurQueue started with:
            - {} producer threads
            - {} worker threads
            - Queue capacity: {}
            Press Ctrl+C to shutdown...
            """, PRODUCER_COUNT, WORKER_COUNT, QUEUE_CAPACITY);
    }

    private void shutdown() {
        logger.info("\nInitiating shutdown sequence...");

        if (stateTracker.getTasksProcessedCount() >= MAX_TASKS) {
            logger.info("Reached max tasks. Shutting down...");
            shutdown();
        }

        producerExecutor.shutdownNow();
        logger.info("Producers shutdown requested.");

        monitor.shutdown();
        logger.info("Monitor shutdown requested.");

        workerExecutor.shutdown();
        logger.info("Worker shutdown requested. Waiting for current tasks to complete...");

        try {
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Forcing worker shutdown after timeout...");
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted. Forcing immediate shutdown.");
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("\n=== Final System Status ===");
        logger.info("Total tasks submitted: {}", stateTracker.getTotalTasksSubmitted());
        logger.info("Tasks processed: {} ({}%)",
                stateTracker.getTasksProcessedCount(),
                percentage(stateTracker.getTasksProcessedCount(), stateTracker.getTotalTasksSubmitted()));
        logger.info("Tasks failed: {} ({}%)",
                stateTracker.getTasksFailedCount(),
                percentage(stateTracker.getTasksFailedCount(), stateTracker.getTotalTasksSubmitted()));
        logger.info("Tasks retried: {}", stateTracker.getTasksRetriedCount());
        logger.info("Average processing time: {}ms", stateTracker.getAverageProcessingTime());
        logger.info("Shutdown complete.");
    }

    private double percentage(int part, int whole) {
        return whole > 0 ? (part * 100.0 / whole) : 0;
    }

    public static void main(String[] args) {
        new ConcurQueue();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            return new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
        }
    }
}