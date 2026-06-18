package com.aichat.app.model;

/**
 * Persistent chat session/thread metadata.
 */
public class Conversation {
    public String id;
    public String title;
    public String providerId; // last used provider id
    public String model;      // last used model
    public long createdAt;
    public long updatedAt;

    public Conversation() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
