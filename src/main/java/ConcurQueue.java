import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurQueue {
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
        // Create priority-based task queue with capacity
        this.taskQueue = new PriorityBlockingQueue<>(QUEUE_CAPACITY);
        this.stateTracker = new TaskStateTracker();

        // Create producers
        this.producerExecutor = Executors.newFixedThreadPool(PRODUCER_COUNT, new NamedThreadFactory("Producer"));
        for (int i = 1; i <= PRODUCER_COUNT; i++) {
            producerExecutor.submit(new Producer("Producer-" + i, taskQueue, stateTracker));
        }

        // Create worker pool with custom thread factory
        this.workerExecutor = Executors.newFixedThreadPool(WORKER_COUNT, new NamedThreadFactory("Worker"));
        for (int i = 1; i <= WORKER_COUNT; i++) {
            workerExecutor.submit(new Worker("Worker-" + i, taskQueue, stateTracker));
        }

        // Start monitor in a separate thread
        this.monitor = new Monitor(taskQueue, workerExecutor, stateTracker);
        new Thread(monitor, "Monitor").start();

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Shutdown-Hook"));

        System.out.printf("""
            ConcurQueue started with:
            - %d producer threads
            - %d worker threads
            - Queue capacity: %d
            Press Ctrl+C to shutdown...
            %n""", PRODUCER_COUNT, WORKER_COUNT, QUEUE_CAPACITY);
    }

    private void shutdown() {
        System.out.println("\nInitiating shutdown sequence...");

        if (stateTracker.getTasksProcessedCount() >= MAX_TASKS) {
            System.out.println("Reached max tasks. Shutting down...");
            shutdown();
        }

        // Step 1: Shutdown producers (no new tasks)
        producerExecutor.shutdownNow();
        System.out.println("Producers shutdown requested.");

        // Step 2: Shutdown monitor
        monitor.shutdown();
        System.out.println("Monitor shutdown requested.");

        // Step 3: Shutdown workers after current tasks
        workerExecutor.shutdown();
        System.out.println("Worker shutdown requested. Waiting for current tasks to complete...");

        try {
            // Wait for workers to finish with timeout
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("Forcing worker shutdown after timeout...");
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("Shutdown interrupted. Forcing immediate shutdown.");
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final status report
        System.out.println("\n=== Final System Status ===");
        System.out.printf("Total tasks submitted: %d%n", stateTracker.getTotalTasksSubmitted());
        System.out.printf("Tasks processed: %d (%.1f%%)%n",
                stateTracker.getTasksProcessedCount(),
                percentage(stateTracker.getTasksProcessedCount(), stateTracker.getTotalTasksSubmitted()));
        System.out.printf("Tasks failed: %d (%.1f%%)%n",
                stateTracker.getTasksFailedCount(),
                percentage(stateTracker.getTasksFailedCount(), stateTracker.getTotalTasksSubmitted()));
        System.out.printf("Tasks retried: %d%n", stateTracker.getTasksRetriedCount());
        System.out.printf("Average processing time: %.1fms%n", stateTracker.getAverageProcessingTime());
        System.out.println("Shutdown complete.");
    }

    private double percentage(int part, int whole) {
        return whole > 0 ? (part * 100.0 / whole) : 0;
    }

    public static void main(String[] args) {
        new ConcurQueue();
    }

    // Custom thread factory for named threads
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