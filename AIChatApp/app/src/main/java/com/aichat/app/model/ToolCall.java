package com.aichat.app.model;

/**
 * A single tool call requested by the LLM.
 */
public class ToolCall {
    public String id;          // tool call id
    public String name;        // tool name (matches ToolConfig.id or MCP tool name)
    public String arguments;   // JSON string

    public ToolCall() {}

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }
}
