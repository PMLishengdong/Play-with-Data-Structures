package com.aichat.app.mcp;

import com.aichat.app.model.MCPConfig;

import java.util.List;
import java.util.Map;

/**
 * A client that talks to a single MCP server. Implementations are transport
 * specific (HTTP, WebSocket).
 */
public interface MCPClient {

    /** Connect / initialize. */
    void connect() throws Exception;

    /** List tools exposed by the server. */
    List<MCPTool> listTools() throws Exception;

    /** Invoke a tool with the given arguments. */
    MCPTool.Result callTool(String toolName, Map<String, Object> arguments) throws Exception;

    /** Close the client. */
    void close();

    /** Get the config this client was created with. */
    MCPConfig getConfig();

    /** Is this client usable? */
    boolean isReady();
}
