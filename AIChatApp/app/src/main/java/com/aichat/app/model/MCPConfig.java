package com.aichat.app.model;

/**
 * MCP server configuration. MCP = Model Context Protocol style tool provider.
 *
 * type values:
 *  - "http"   Streamable HTTP / SSE transport
 *  - "ws"     WebSocket transport
 */
public class MCPConfig {
    public String id;
    public String name;
    public String type;       // http | ws
    public String endpoint;   // http(s)://... or ws(s)://...
    public String description;
    public boolean enabled = true;
    public long createdAt;

    public MCPConfig() {
        this.createdAt = System.currentTimeMillis();
    }
}
