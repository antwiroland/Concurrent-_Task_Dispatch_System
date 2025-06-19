package com.sikawofie.concurqueue.test.integration;

import com.sikawofie.concurqueue.consumer.Worker;
import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.producer.Producer;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerWorkerIntegrationTest {
    private BlockingQueue<Task> taskQueue;
    private TaskStateTracker stateTracker;
    private ExecutorService executor;

    @BeforeEach
    public void setup() {
        taskQueue = new PriorityBlockingQueue<>();
        stateTracker = new TaskStateTracker();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    public void teardown() {
        executor.shutdownNow();
    }

    @Test
    public void testProducerWorkerIntegration() throws InterruptedException {
        Producer producer = new Producer("IntegrationProducer", taskQueue, stateTracker);
        Worker worker = new Worker("IntegrationWorker", taskQueue, stateTracker);

        executor.submit(producer);
        executor.submit(worker);

        Thread.sleep(5000); // Let it run

        producer.shutdown();
        worker.shutdown();

        Thread.sleep(1000); // Allow shutdown and processing

        int submitted = stateTracker.getTotalTasksSubmitted();
        int processed = stateTracker.getTasksProcessedCount();

        assertTrue(submitted > 0, "No tasks submitted");
        assertTrue(processed > 0, "No tasks processed");
    }
}
