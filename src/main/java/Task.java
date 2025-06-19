import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class Task implements Comparable<Task> {
    private final UUID id;
    private final String name;
    private final int priority;
    private final Instant createdTimestamp;
    private final String payload;
    private int retryCount = 0;

    public Task(String name, int priority, String payload) {
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("Priority must be between 1 and 10");
        }
        this.id = UUID.randomUUID();
        this.name = name;
        this.priority = priority;
        this.createdTimestamp = Instant.now();
        this.payload = payload;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    @Override
    public int compareTo(Task other) {
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return this.createdTimestamp.compareTo(other.createdTimestamp);
    }

    @Override
    public String toString() {
        return String.format("Task{id=%s, name='%s', priority=%d, created=%s, retries=%d}",
                id, name, priority, createdTimestamp, retryCount);
    }
}