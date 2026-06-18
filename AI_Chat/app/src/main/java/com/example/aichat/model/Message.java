package com.example.aichat.model;

import java.util.Date;

public class Message {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_SYSTEM = 2;

    public static final int STATUS_SENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_RECEIVING = 2;
    public static final int STATUS_RECEIVED = 3;
    public static final int STATUS_ERROR = 4;
    public static final int STATUS_CANCELLED = 5;

    private String id;
    private int type;
    private String content;
    private Date timestamp;
    private int status;
    private String modelId;

    public Message() {}

    public Message(String id, int type, String content) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.timestamp = new Date();
        this.status = TYPE_USER == type ? STATUS_SENDING : STATUS_RECEIVING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public boolean isUserMessage() {
        return type == TYPE_USER;
    }

    public boolean isAIMessage() {
        return type == TYPE_AI;
    }

    public boolean isSystemMessage() {
        return type == TYPE_SYSTEM;
    }
}
