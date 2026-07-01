package com.trae.agent.model;

import java.util.List;

/**
 * Chat message between user/assistant/system and the LLM.
 */
public class Message {
    private String role;    // "system", "user", "assistant"
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId; // response to a tool call

    public Message() {}

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // --- Getters / Setters ---

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public static Message systemMessage(String content) {
        return new Message("system", content);
    }

    public static Message userMessage(String content) {
        return new Message("user", content);
    }

    public static Message assistantMessage(String content) {
        return new Message("assistant", content);
    }

    public static Message toolResultMessage(String toolCallId, String content) {
        Message msg = new Message("tool", content);
        msg.setToolCallId(toolCallId);
        return msg;
    }
}