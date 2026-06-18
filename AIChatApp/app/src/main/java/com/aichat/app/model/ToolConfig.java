package com.aichat.app.model;

/**
 * Tool configuration. Tools are actions the LLM can request via function/tool calling.
 * type values:
 *  - "builtin"     built-in tool (e.g. calculator, datetime, web_search)
 *  - "mcp"         proxied from a registered MCP server
 *  - "http"        arbitrary HTTP tool
 */
public class ToolConfig {
    public String id;          // unique id, used by LLM tool calls
    public String name;        // display name
    public String type;        // builtin | mcp | http
    public String description; // shown to the LLM
    public String params;      // JSON string with type-specific params
    public boolean enabled = true;
    public long createdAt;

    public ToolConfig() {
        this.createdAt = System.currentTimeMillis();
    }
}
