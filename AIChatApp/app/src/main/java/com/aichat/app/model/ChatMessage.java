package com.aichat.app.model;

/**
 * A chat message in a conversation. Used for UI display and for the LLM API call.
 */
public class ChatMessage {
    public String role;       // MessageRole.*
    public String content;    // text content
    public String name;       // tool name (when role=tool)
    public String toolCallId; // for tool messages, the call id
    public long timestamp;

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String role, String content, String name, String toolCallId) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.toolCallId = toolCallId;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isUser() {
        return MessageRole.USER.equals(role);
    }

    public boolean isAssistant() {
        return MessageRole.ASSISTANT.equals(role);
    }

    public boolean isTool() {
        return MessageRole.TOOL.equals(role);
    }
}
