package com.sikawofie.concurqueue.test.unit;

import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.producer.Producer;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

import java.util.concurrent.BlockingQueue;

public class ProducerAndWorkerTest {
    @Test
    public void testProducerSubmitsTasks() throws InterruptedException {
        BlockingQueue<Task> queue = mock(BlockingQueue.class);
        TaskStateTracker tracker = mock(TaskStateTracker.class);
        Producer producer = new Producer("TestProducer", queue, tracker);

        Thread thread = new Thread(producer);
        thread.start();
        Thread.sleep(1000);
        producer.shutdown();
        thread.interrupt();

        verify(queue, atLeastOnce()).put(any(Task.class));
    }
}
