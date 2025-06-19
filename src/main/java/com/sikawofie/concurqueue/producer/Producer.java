package com.sikawofie.concurqueue.producer;

import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int MAX_BACKOFF_TIME_MS = 5000;

    private final String name;
    private final BlockingQueue<Task> taskQueue;
    private final TaskStateTracker stateTracker;
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    private final Random random = new Random();
    private volatile boolean running = true;

    public Producer(String name, BlockingQueue<Task> taskQueue, TaskStateTracker stateTracker) {
        this.name = name;
        this.taskQueue = taskQueue;
        this.stateTracker = stateTracker;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                int tasksToGenerate = random.nextInt(3) + 1;

                for (int i = 0; i < tasksToGenerate; i++) {
                    if (taskQueue.size() >= MAX_QUEUE_SIZE) {
                        logger.warn("Producer {}: Queue full ({}), waiting to submit more tasks...",
                                name, taskQueue.size());
                        int backoffTime = Math.min(
                                (int) Math.pow(2, taskQueue.size() - MAX_QUEUE_SIZE + 1) * 100,
                                MAX_BACKOFF_TIME_MS
                        );
                        Thread.sleep(backoffTime);
                        continue;
                    }

                    int priority = random.nextInt(10) < 7 ?
                            random.nextInt(7) + 1 :
                            random.nextInt(3) + 8;

                    String payload = String.format("%s-%d-data-%d", name, taskCounter.get(), random.nextInt(1000));
                    Task task = new Task(name + "Task-" + taskCounter.incrementAndGet(), priority, payload);

                    stateTracker.registerNewTask(task);
                    taskQueue.put(task);
                    logger.info("[{}] Submitted {}", name, task);
                }

                Thread.sleep(random.nextInt(2000) + 1000);
            }
        } catch (InterruptedException e) {
            logger.warn("Producer {} interrupted. Shutting down.", name);
            Thread.currentThread().interrupt();
        } finally {
            logger.info("Producer {} exiting.", name);
        }
    }

    public void shutdown() {
        running = false;
    }
}