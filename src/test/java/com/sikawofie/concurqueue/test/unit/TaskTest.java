package com.sikawofie.concurqueue.test.unit;

import com.sikawofie.concurqueue.entity.Task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

public class TaskTest {

    @Test
    public void testValidTaskCreation() {
        Task task = new Task("TestTask", 5, "data");
        assertNotNull(task.getId());
        assertEquals("TestTask", task.getName());
        assertEquals(5, task.getPriority());
        assertEquals("data", task.getPayload());
        assertTrue(task.getCreatedTimestamp().isBefore(Instant.now()));
    }

    @Test
    public void testInvalidPriorityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new Task("Invalid", 0, "payload"));
        assertThrows(IllegalArgumentException.class, () -> new Task("Invalid", 11, "payload"));
    }

    @Test
    public void testCompareToByPriorityAndTimestamp() throws InterruptedException {
        Task highPriority = new Task("High", 10, "payload");
        Thread.sleep(10);
        Task lowPriority = new Task("Low", 5, "payload");

        assertTrue(highPriority.compareTo(lowPriority) < 0);
        assertTrue(lowPriority.compareTo(highPriority) > 0);
    }

    @Test
    public void testRetryCountIncrement() {
        Task task = new Task("RetryTask", 5, "payload");
        task.incrementRetryCount();
        assertEquals(1, task.getRetryCount());
    }
}
