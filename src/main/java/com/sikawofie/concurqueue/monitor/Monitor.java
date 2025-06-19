package com.sikawofie.concurqueue.monitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.enums.TaskStatus;
import com.sikawofie.concurqueue.utils.TaskStateTracker;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

public class Monitor implements Runnable {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    private final BlockingQueue<Task> taskQueue;
    private final ExecutorService executorService;
    private final TaskStateTracker stateTracker;
    private volatile boolean running = true;
    private long lastExportTime = 0;
    private static final long EXPORT_INTERVAL_MS = 60000; // 1 minute

    public Monitor(BlockingQueue<Task> taskQueue, ExecutorService executorService,
                   TaskStateTracker stateTracker) {
        this.taskQueue = taskQueue;
        this.executorService = executorService;
        this.stateTracker = stateTracker;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                printSystemStatus();
                checkForStalledTasks();
                maybeExportStatus();
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            System.out.println("Monitor interrupted. Shutting down.");
            Thread.currentThread().interrupt();
        } finally {
            exportTaskStatus();
            System.out.println("Monitor exiting.");
        }
    }

    private void printSystemStatus() {
        String time = TIME_FORMATTER.format(LocalDateTime.ofInstant(
                Instant.now(), ZoneId.systemDefault()));

        System.out.printf("\n=== System Status [%s] ===%n", time);
        System.out.printf("Queue size: %d (%.1f%% capacity)%n",
                taskQueue.size(),
                (taskQueue.size() / 100.0) * 100);
        System.out.printf("Tasks submitted: %d%n", stateTracker.getTotalTasksSubmitted());
        System.out.printf("Tasks processed: %d (%.1f%%)%n",
                stateTracker.getTasksProcessedCount(),
                percentage(stateTracker.getTasksProcessedCount(), stateTracker.getTotalTasksSubmitted()));
        System.out.printf("Tasks failed: %d (%.1f%%)%n",
                stateTracker.getTasksFailedCount(),
                percentage(stateTracker.getTasksFailedCount(), stateTracker.getTotalTasksSubmitted()));
        System.out.printf("Tasks retried: %d%n", stateTracker.getTasksRetriedCount());
        System.out.printf("Avg processing time: %.1fms%n", stateTracker.getAverageProcessingTime());


        if (executorService.isShutdown()) {
            System.out.println("Executor service: Shutting down");
        } else if (executorService.isTerminated()) {
            System.out.println("Executor service: Terminated");
        } else {
            System.out.println("Executor service: Running");
        }
    }

    private double percentage(int part, int whole) {
        return whole > 0 ? (part * 100.0 / whole) : 0;
    }

    private void checkForStalledTasks() {
        long now = System.currentTimeMillis();
        stateTracker.getAllTasks().forEach((id, task) -> {
            TaskStatus status = stateTracker.getTaskStatus(id);
            if (status == TaskStatus.PROCESSING) {
                long processingTime = now - task.getCreatedTimestamp().toEpochMilli();
                if (processingTime > 10000) {
                    System.out.printf("Task %s has been processing for %dms%n",
                            task, processingTime);
                }
            }
        });
    }

    private void maybeExportStatus() {
        long now = System.currentTimeMillis();
        if (now - lastExportTime > EXPORT_INTERVAL_MS) {
            exportTaskStatus();
            lastExportTime = now;
        }
    }

    private void exportTaskStatus() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .format(LocalDateTime.now());
        String filename = "task_status_" + timestamp + ".json";

        try (FileWriter writer = new FileWriter(filename)) {
            GSON.toJson(stateTracker.getAllTasks(), writer);
            System.out.println("Exported task status to " + filename);
        } catch (IOException e) {
            System.out.println("Failed to export task status: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
    }


    private static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEpochMilli());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == null) {
                return null;
            }
            return Instant.ofEpochMilli(in.nextLong());
        }
    }
}