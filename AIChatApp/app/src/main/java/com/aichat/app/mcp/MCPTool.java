package com.aichat.app.mcp;

import org.json.JSONObject;

import java.util.Map;

/**
 * A tool exposed by an MCP server. Tools are dynamic – discovered from the
 * server's tools/list endpoint and invoked through tools/call.
 */
public final class MCPTool {
    public final String serverId;
    public final String name;
    public final String description;
    /** Raw JSON schema (string) describing the input parameters. */
    public final String inputSchema;

    public MCPTool(String serverId, String name, String description, String inputSchema) {
        this.serverId = serverId;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    /** A tool call from the LLM with the original name. */
    public static final class Call {
        public final String toolName;
        public final Map<String, Object> arguments;
        public Call(String toolName, Map<String, Object> arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String text;
        public final JSONObject raw;
        public Result(boolean ok, String text, JSONObject raw) {
            this.ok = ok;
            this.text = text;
            this.raw = raw;
        }
    }
}
