package com.aichat.app.mcp;

import com.aichat.app.model.MCPConfig;
import com.aichat.app.storage.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Holds the live MCP clients. Reads enabled configs from {@link ConfigManager}
 * and lazily opens clients on demand. Clients can be "uninstalled" (closed and
 * forgotten) by deleting the underlying config.
 */
public class MCPManager {
    private static final MCPManager INSTANCE = new MCPManager();
    public static MCPManager getInstance() { return INSTANCE; }

    private final Map<String, MCPClient> clients = new HashMap<String, MCPClient>();
    private final Object lock = new Object();
    private ConfigManager config;

    public void init(ConfigManager cfg) {
        this.config = cfg;
    }

    public MCPClient getClient(String id) {
        if (id == null) return null;
        synchronized (lock) {
            return clients.get(id);
        }
    }

    public MCPClient connect(MCPConfig cfg) {
        if (cfg == null) return null;
        synchronized (lock) {
            MCPClient existing = clients.get(cfg.id);
            if (existing != null && existing.isReady()) return existing;
            if (existing != null) { try { existing.close(); } catch (Exception ignored) {} }
            MCPClient c = createClient(cfg);
            try {
                c.connect();
            } catch (Throwable t) {
                // Keep client in map but not ready; will retry on next use
            }
            clients.put(cfg.id, c);
            return c;
        }
    }

    public void disconnect(String id) {
        if (id == null) return;
        synchronized (lock) {
            MCPClient c = clients.remove(id);
            if (c != null) try { c.close(); } catch (Exception ignored) {}
        }
    }

    public void disconnectAll() {
        synchronized (lock) {
            Iterator<Map.Entry<String, MCPClient>> it = clients.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, MCPClient> e = it.next();
                try { e.getValue().close(); } catch (Exception ignored) {}
                it.remove();
            }
        }
    }

    public List<MCPTool> listAllTools() {
        List<MCPTool> out = new ArrayList<MCPTool>();
        if (config == null) return out;
        List<MCPConfig> configs = config.listMCPs();
        for (int i = 0; i < configs.size(); i++) {
            MCPConfig c = configs.get(i);
            if (!c.enabled) continue;
            try {
                MCPClient client = connect(c);
                if (client == null || !client.isReady()) continue;
                List<MCPTool> ts = client.listTools();
                if (ts != null) out.addAll(ts);
            } catch (Throwable t) {
                // skip
            }
        }
        return out;
    }

    public MCPTool.Result invokeByName(String toolName, Map<String, Object> args) {
        if (toolName == null) return new MCPTool.Result(false, "null tool", null);
        // First try exact match by tool id (when MCP is bridged via ToolConfig)
        if (config != null) {
            com.aichat.app.model.ToolConfig tc = config.getTool(toolName);
            if (tc != null && "mcp".equals(tc.type) && tc.enabled) {
                // Params contain the MCP server id and the actual tool name
                String mcpId = null;
                String realName = toolName;
                try {
                    org.json.JSONObject p = new org.json.JSONObject(tc.params);
                    mcpId = p.optString("serverId", null);
                    String mn = p.optString("toolName", null);
                    if (mn != null && mn.length() > 0) realName = mn;
                } catch (Exception ignored) {}
                if (mcpId == null) return new MCPTool.Result(false, "no serverId in tool config", null);
                MCPConfig mc = config.getMCP(mcpId);
                if (mc == null) return new MCPTool.Result(false, "mcp not found", null);
                MCPClient client = connect(mc);
                if (client == null) return new MCPTool.Result(false, "client null", null);
                try {
                    return client.callTool(realName, args);
                } catch (Throwable t) {
                    return new MCPTool.Result(false, t.getMessage(), null);
                }
            }
        }
        // Otherwise search all MCPs
        if (config == null) return new MCPTool.Result(false, "no config", null);
        List<MCPConfig> configs = config.listMCPs();
        for (int i = 0; i < configs.size(); i++) {
            MCPConfig c = configs.get(i);
            if (!c.enabled) continue;
            MCPClient client = connect(c);
            if (client == null || !client.isReady()) continue;
            try {
                List<MCPTool> ts = client.listTools();
                if (ts != null) {
                    for (int j = 0; j < ts.size(); j++) {
                        if (toolName.equals(ts.get(j).name)) {
                            return client.callTool(toolName, args);
                        }
                    }
                }
            } catch (Throwable t) {
                // continue
            }
        }
        return new MCPTool.Result(false, "tool not found: " + toolName, null);
    }

    private MCPClient createClient(MCPConfig cfg) {
        if ("ws".equalsIgnoreCase(cfg.type)) {
            return new WebSocketMCPClient(cfg);
        }
        return new HTTPMCPClient(cfg);
    }
}
