package com.trae.agent.model;

import java.util.Map;

/**
 * Represents a tool call requested by the LLM.
 */
public class ToolCall {
    private String id;
    private String type;      // "function"
    private String name;      // tool/function name
    private Map<String, Object> arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.type = "function";
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
}