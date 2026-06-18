package com.example.aichat.tool;

import android.content.Context;

import com.example.aichat.model.ToolInfo;
import com.example.aichat.util.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolManager {
    private static ToolManager instance;
    private final ConfigManager configManager;
    private final Map<String, ToolExecutor> toolCache;

    private ToolManager(Context context) {
        configManager = ConfigManager.getInstance(context);
        toolCache = new HashMap<String, ToolExecutor>();
        initBuiltinTools();
    }

    public static synchronized ToolManager getInstance(Context context) {
        if (instance == null) {
            instance = new ToolManager(context.getApplicationContext());
        }
        return instance;
    }

    private void initBuiltinTools() {
        toolCache.put("calculate", new CalculatorTool());
        toolCache.put("formatDate", new DateFormatTool());
        toolCache.put("parseUrl", new UrlParserTool());
    }

    public void registerTool(ToolInfo toolInfo) {
        configManager.addTool(toolInfo);
    }

    public void unregisterTool(String toolId) {
        configManager.removeTool(toolId);
    }

    public void enableTool(String toolId, boolean enabled) {
        List<ToolInfo> tools = configManager.getTools();
        for (ToolInfo tool : tools) {
            if (tool.getId().equals(toolId)) {
                tool.setEnabled(enabled);
                configManager.updateTool(tool);
                break;
            }
        }
    }

    public ToolExecutor getTool(String functionName) {
        return toolCache.get(functionName);
    }

    public String executeTool(String functionName, Map<String, Object> params) {
        ToolExecutor executor = getTool(functionName);
        if (executor != null) {
            return executor.execute(params);
        }
        return null;
    }

    public List<ToolInfo> getAllTools() {
        return configManager.getTools();
    }

    public List<ToolInfo> getEnabledTools() {
        List<ToolInfo> all = configManager.getTools();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).isEnabled()) {
                all.remove(i);
            }
        }
        return all;
    }

    public void addCustomTool(String functionName, ToolExecutor executor) {
        toolCache.put(functionName, executor);
    }
}
