package com.sikawofie.concurqueue.test.unit;

import com.sikawofie.concurqueue.entity.Task;
import com.sikawofie.concurqueue.enums.TaskStatus;
import com.sikawofie.concurqueue.utils.TaskStateTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskStateTrackerTest {

    @Test
    public void testRegisterAndStatusUpdate() {
        TaskStateTracker tracker = new TaskStateTracker();
        Task task = new Task("TrackMe", 5, "payload");

        tracker.registerNewTask(task);
        assertEquals(TaskStatus.SUBMITTED, tracker.getTaskStatus(task.getId()));

        tracker.updateTaskStatus(task.getId(), TaskStatus.PROCESSING);
        assertEquals(TaskStatus.PROCESSING, tracker.getTaskStatus(task.getId()));
    }

}
