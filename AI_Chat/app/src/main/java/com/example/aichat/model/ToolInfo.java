package com.example.aichat.model;

public class ToolInfo {
    private String id;
    private String name;
    private String description;
    private String functionName;
    private boolean enabled;

    public ToolInfo() {}

    public ToolInfo(String id, String name, String description, String functionName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.functionName = functionName;
        this.enabled = true;
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

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return name;
    }
}
