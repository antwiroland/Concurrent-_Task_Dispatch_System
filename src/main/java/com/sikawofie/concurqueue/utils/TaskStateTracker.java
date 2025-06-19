package com.sikawofie.concurqueue.utils;

import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.enums.TaskStatus;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TaskStateTracker {
    private final ConcurrentHashMap<UUID, TaskStatus> taskStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger tasksProcessedCount = new AtomicInteger(0);
    private final AtomicInteger tasksFailedCount = new AtomicInteger(0);
    private final AtomicInteger tasksRetriedCount = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    public void registerNewTask(Task task) {
        taskStates.put(task.getId(), TaskStatus.SUBMITTED);
        tasks.put(task.getId(), task);
    }

    public void updateTaskStatus(UUID taskId, TaskStatus status) {
        taskStates.put(taskId, status);

        if (status == TaskStatus.COMPLETED) {
            tasksProcessedCount.incrementAndGet();
        } else if (status == TaskStatus.FAILED) {
            tasksFailedCount.incrementAndGet();
        } else if (status == TaskStatus.RETRYING) {
            tasksRetriedCount.incrementAndGet();
        }
    }

    public void recordProcessingTime(long durationMillis) {
        totalProcessingTime.addAndGet(durationMillis);
    }

    public TaskStatus getTaskStatus(UUID taskId) {
        return taskStates.get(taskId);
    }

    public Task getTask(UUID taskId) {
        return tasks.get(taskId);
    }

    public int getTasksProcessedCount() {
        return tasksProcessedCount.get();
    }

    public int getTasksFailedCount() {
        return tasksFailedCount.get();
    }

    public int getTasksRetriedCount() {
        return tasksRetriedCount.get();
    }

    public int getTotalTasksSubmitted() {
        return taskStates.size();
    }

    public double getAverageProcessingTime() {
        int processed = tasksProcessedCount.get();
        return processed > 0 ? (double) totalProcessingTime.get() / processed : 0;
    }

    public void incrementTasksRetried() {
        tasksRetriedCount.incrementAndGet();
    }

    public ConcurrentHashMap<UUID, Task> getAllTasks() {
        return new ConcurrentHashMap<>(tasks);
    }
}
