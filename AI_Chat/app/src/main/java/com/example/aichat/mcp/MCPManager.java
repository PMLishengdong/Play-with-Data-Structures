package com.example.aichat.mcp;

import android.content.Context;

import com.example.aichat.model.MCPInfo;
import com.example.aichat.util.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MCPManager {
    private static MCPManager instance;
    private final ConfigManager configManager;
    private final Map<String, MCPInterface> mcpCache;

    private MCPManager(Context context) {
        configManager = ConfigManager.getInstance(context);
        mcpCache = new HashMap<String, MCPInterface>();
    }

    public static synchronized MCPManager getInstance(Context context) {
        if (instance == null) {
            instance = new MCPManager(context.getApplicationContext());
        }
        return instance;
    }

    public void registerMCP(MCPInfo mcpInfo) {
        configManager.addMCP(mcpInfo);
        mcpCache.put(mcpInfo.getId(), new MCPInterface(mcpInfo));
    }

    public void unregisterMCP(String mcpId) {
        configManager.removeMCP(mcpId);
        mcpCache.remove(mcpId);
    }

    public void enableMCP(String mcpId, boolean enabled) {
        List<MCPInfo> mcps = configManager.getMCPS();
        for (MCPInfo mcp : mcps) {
            if (mcp.getId().equals(mcpId)) {
                mcp.setEnabled(enabled);
                configManager.updateMCP(mcp);
                break;
            }
        }
    }

    public MCPInterface getMCP(String mcpId) {
        MCPInterface mcp = mcpCache.get(mcpId);
        if (mcp == null) {
            List<MCPInfo> mcps = configManager.getMCPS();
            for (MCPInfo mcpInfo : mcps) {
                if (mcpInfo.getId().equals(mcpId)) {
                    mcp = new MCPInterface(mcpInfo);
                    mcpCache.put(mcpId, mcp);
                    break;
                }
            }
        }
        return mcp;
    }

    public List<MCPInfo> getAllMCPS() {
        return configManager.getMCPS();
    }

    public List<MCPInfo> getEnabledMCPS() {
        List<MCPInfo> all = configManager.getMCPS();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).isEnabled()) {
                all.remove(i);
            }
        }
        return all;
    }

    public void clearCache() {
        mcpCache.clear();
    }
}
