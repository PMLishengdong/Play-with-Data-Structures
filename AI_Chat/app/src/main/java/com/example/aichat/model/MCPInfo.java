package com.example.aichat.model;

public class MCPInfo {
    private String id;
    private String name;
    private String description;
    private String baseUrl;
    private boolean enabled;
    private long registeredAt;

    public MCPInfo() {}

    public MCPInfo(String id, String name, String description, String baseUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.baseUrl = baseUrl;
        this.enabled = true;
        this.registeredAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
