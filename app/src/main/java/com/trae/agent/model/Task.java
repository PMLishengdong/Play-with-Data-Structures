package com.trae.agent.model;

/**
 * A single task within a plan, representing one step the agent needs to complete.
 */
public class Task {
    private String id;
    private String description;
    private Status status;

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public Task() {}

    public Task(String id, String description) {
        this.id = id;
        this.description = description;
        this.status = Status.PENDING;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("[%s] %s — %s", id, description, status);
    }
}