package com.sikawofie.concurqueue.consumer;

import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.enums.TaskStatus;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class Worker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    private static final int MAX_RETRIES = 3;

    private final String name;
    private final BlockingQueue<Task> taskQueue;
    private final TaskStateTracker stateTracker;
    private final Random random = new Random();
    private volatile boolean running = true;

    public Worker(String name, BlockingQueue<Task> taskQueue, TaskStateTracker stateTracker) {
        this.name = name;
        this.taskQueue = taskQueue;
        this.stateTracker = stateTracker;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                Task task = taskQueue.take();
                processTask(task);
            }
        } catch (InterruptedException e) {
            logger.warn("Worker {} interrupted. Shutting down.", name);
            Thread.currentThread().interrupt();
        } finally {
            logger.info("Worker {} exiting.", name);
        }
    }

    private void processTask(Task task) {
        long startTime = System.currentTimeMillis();
        logger.debug("Started processing {}", task);
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.PROCESSING);

        try {
            int processingTime = random.nextInt(2000) / task.getPriority();
            Thread.sleep(processingTime);

            boolean shouldFail = random.nextInt(100) < 15;

            if (shouldFail && task.getRetryCount() < MAX_RETRIES) {
                handleTaskFailure(task);
            } else if (shouldFail) {
                handlePermanentFailure(task);
            } else {
                handleTaskSuccess(task, startTime);
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while processing {}. Re-queueing.", task);
            taskQueue.offer(task);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error processing {}", task, e);
            handleTaskFailure(task);
        }
    }

    private void handleTaskSuccess(Task task, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        stateTracker.recordProcessingTime(duration);
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.COMPLETED);
        logger.info("Completed {} in {}ms", task, duration);
    }

    private void handleTaskFailure(Task task) {
        task.incrementRetryCount();
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.RETRYING);
        logger.warn("Failed {} (retry {}/{}). Re-queueing.", task, task.getRetryCount(), MAX_RETRIES);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        taskQueue.offer(task);
    }

    private void handlePermanentFailure(Task task) {
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.FAILED);
        logger.error("Permanently failed {} after {} retries", task, MAX_RETRIES);
    }

    public void shutdown() {
        running = false;
    }
}