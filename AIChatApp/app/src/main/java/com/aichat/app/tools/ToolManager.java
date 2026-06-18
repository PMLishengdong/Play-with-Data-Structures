package com.aichat.app.tools;

import com.aichat.app.mcp.MCPManager;
import com.aichat.app.mcp.MCPTool;
import com.aichat.app.model.ToolConfig;
import com.aichat.app.provider.AIProvider;
import com.aichat.app.storage.ConfigManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Resolves tool configs to the actual {@link Tool} implementations exposed to
 * the LLM. Supports:
 *   - builtin   (built-in Java tools)
 *   - mcp       (delegated to an MCP server)
 *   - http      (configured HTTP endpoint)
 *
 * The manager also exposes the LLM-facing tool specs (name / description /
 * schema) for the current enabled set.
 */
public class ToolManager {
    private static final ToolManager INSTANCE = new ToolManager();
    public static ToolManager getInstance() { return INSTANCE; }

    private final Map<String, Tool> builtins = new HashMap<String, Tool>();
    private final Map<String, Tool> mcpCache = new HashMap<String, Tool>();
    private ConfigManager config;
    private MCPManager mcpManager;

    public void init(ConfigManager cfg) {
        this.config = cfg;
        this.mcpManager = MCPManager.getInstance();
        this.mcpManager.init(cfg);
        registerBuiltin(new CalculatorTool());
        registerBuiltin(new DateTimeTool());
        registerBuiltin(new WebFetchTool());
        registerBuiltin(new WebSearchTool());
    }

    private void registerBuiltin(Tool t) {
        builtins.put(t.getId(), t);
    }

    public void invalidate() {
        mcpCache.clear();
    }

    public List<AIProvider.ToolSpec> currentToolSpecs() {
        List<AIProvider.ToolSpec> out = new ArrayList<AIProvider.ToolSpec>();
        if (config == null) return out;
        List<ToolConfig> tools = config.listTools();
        for (int i = 0; i < tools.size(); i++) {
            ToolConfig tc = tools.get(i);
            if (!tc.enabled) continue;
            AIProvider.ToolSpec spec = specFor(tc);
            if (spec != null) out.add(spec);
        }
        // Also include tools exposed by MCPs (proxied as their bare names)
        if (mcpManager != null) {
            try {
                List<MCPTool> mcpTools = mcpManager.listAllTools();
                for (int i = 0; i < mcpTools.size(); i++) {
                    MCPTool mt = mcpTools.get(i);
                    out.add(AIProvider.tool(mt.name, mt.description, mt.inputSchema));
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    public AIProvider.ToolSpec specFor(ToolConfig tc) {
        if (tc == null) return null;
        if ("builtin".equalsIgnoreCase(tc.type)) {
            Tool t = builtins.get(tc.id);
            if (t == null) return null;
            return AIProvider.tool(t.getId(), t.getDescription(), t.getArgumentSchema());
        }
        if ("mcp".equalsIgnoreCase(tc.type)) {
            String mcpId = null;
            String toolName = tc.id;
            try {
                JSONObject p = new JSONObject(tc.params);
                mcpId = p.optString("serverId", null);
                String mn = p.optString("toolName", null);
                if (mn != null && mn.length() > 0) toolName = mn;
            } catch (Exception ignored) {}
            if (mcpId == null) return null;
            com.aichat.app.model.MCPConfig mc = config.getMCP(mcpId);
            if (mc == null) return null;
            try {
                MCPClientWrapper wrapper = (MCPClientWrapper) mcpCache.get(tc.id);
                if (wrapper == null) {
                    wrapper = new MCPClientWrapper(mc, toolName, tc.description);
                    mcpCache.put(tc.id, wrapper);
                }
                return AIProvider.tool(tc.id, tc.description, wrapper.getArgumentSchema());
            } catch (Throwable ignored) {}
            return null;
        }
        if ("http".equalsIgnoreCase(tc.type)) {
            // generic HTTP tool: pass URL template in params.url
            return AIProvider.tool(tc.id,
                    tc.description == null ? tc.name : tc.description,
                    "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},\"maxLength\":{\"type\":\"integer\"}}}");
        }
        return null;
    }

    /** Execute a tool by id with the given JSON argument string. */
    public String execute(String toolId, String argumentsJson) {
        Map<String, Object> args = parseArgs(argumentsJson);
        return execute(toolId, args);
    }

    public String execute(String toolId, Map<String, Object> arguments) {
        if (toolId == null) return "error: null tool id";
        if (config == null) return "error: no config";
        // 1. builtin
        Tool b = builtins.get(toolId);
        if (b != null) {
            try { return b.execute(arguments); } catch (Throwable t) { return "error: " + t.getMessage(); }
        }
        // 2. tool config (builtin, mcp, http)
        ToolConfig tc = config.getTool(toolId);
        if (tc != null) {
            if ("mcp".equalsIgnoreCase(tc.type) && mcpManager != null) {
                MCPTool.Result r = mcpManager.invokeByName(toolId, arguments);
                return r.ok ? r.text : ("error: " + r.text);
            }
            if ("http".equalsIgnoreCase(tc.type)) {
                // Resolve URL
                String url = null;
                try {
                    JSONObject p = new JSONObject(tc.params);
                    url = p.optString("url", null);
                } catch (Exception ignored) {}
                if (url == null) return "error: http tool missing url";
                try {
                    Map<String, String> headers = new HashMap<String, String>();
                    com.aichat.app.util.HttpUtil.Response resp = com.aichat.app.util.HttpUtil.get(url, headers);
                    try {
                        return "HTTP " + resp.code + "\n" + (resp.body == null ? "" : resp.body);
                    } finally {
                        com.aichat.app.util.HttpUtil.close(resp);
                    }
                } catch (Throwable t) {
                    return "error: " + t.getMessage();
                }
            }
        }
        // 3. fallback: maybe an MCP tool surfaced by name only
        if (mcpManager != null) {
            MCPTool.Result r = mcpManager.invokeByName(toolId, arguments);
            if (r.ok) return r.text;
            return "error: " + r.text;
        }
        return "error: tool not found";
    }

    public Tool builtin(String id) { return builtins.get(id); }

    /** Tiny adapter for MCP-bridged tools. */
    private static class MCPClientWrapper implements Tool {
        private final com.aichat.app.model.MCPConfig cfg;
        private final String toolName;
        private final String description;
        MCPClientWrapper(com.aichat.app.model.MCPConfig cfg, String toolName, String description) {
            this.cfg = cfg;
            this.toolName = toolName;
            this.description = description;
        }
        @Override public String getId() { return toolName; }
        @Override public String getDisplayName() { return cfg.name + " / " + toolName; }
        @Override public String getDescription() { return description == null ? ("MCP tool " + toolName) : description; }
        @Override public String getArgumentSchema() {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        @Override public String execute(Map<String, Object> arguments) {
            MCPTool.Result r = MCPManager.getInstance().invokeByName(toolName, arguments);
            return r.ok ? r.text : ("error: " + r.text);
        }
    }

    private static Map<String, Object> parseArgs(String json) {
        Map<String, Object> out = new HashMap<String, Object>();
        if (json == null) return out;
        json = json.trim();
        if (json.length() == 0) return out;
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, o.opt(k));
            }
        } catch (Throwable ignored) {}
        return out;
    }
}
