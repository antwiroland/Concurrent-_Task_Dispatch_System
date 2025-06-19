import java.util.Random;
import java.util.concurrent.BlockingQueue;


public class Worker implements Runnable {
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
            System.out.println("Worker " + name + " interrupted. Shutting down.");
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("Worker " + name + " exiting.");
        }
    }

    private void processTask(Task task) {
        long startTime = System.currentTimeMillis();
        System.out.printf("[%s] Started processing %s%n", name, task);
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
            System.out.printf("[%s] Interrupted while processing %s. Re-queueing.%n", name, task);
            taskQueue.offer(task); // Re-queue the task
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.printf("[%s] Error processing %s: %s%n", name, task, e.getMessage());
            handleTaskFailure(task);
        }
    }

    private void handleTaskSuccess(Task task, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        stateTracker.recordProcessingTime(duration);
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.COMPLETED);
        System.out.printf("[%s] Completed %s in %dms%n", name, task, duration);
    }

    private void handleTaskFailure(Task task) {
        task.incrementRetryCount();
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.RETRYING);
        System.out.printf("[%s] Failed %s (retry %d/%d). Re-queueing.%n",
                name, task, task.getRetryCount(), MAX_RETRIES);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        taskQueue.offer(task);
    }

    private void handlePermanentFailure(Task task) {
        stateTracker.updateTaskStatus(task.getId(), TaskStatus.FAILED);
        System.out.printf("[%s] Permanently failed %s after %d retries%n",
                name, task, MAX_RETRIES);
    }

    public void shutdown() {
        running = false;
    }
}