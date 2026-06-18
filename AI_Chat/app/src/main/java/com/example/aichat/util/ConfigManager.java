package com.example.aichat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.aichat.model.AIModel;
import com.example.aichat.model.MCPInfo;
import com.example.aichat.model.ToolInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String PREF_NAME = "ai_chat_config";
    private static final String KEY_SELECTED_MODEL_ID = "selected_model_id";
    private static final String KEY_MODELS = "models";
    private static final String KEY_MCPS = "mcps";
    private static final String KEY_TOOLS = "tools";

    private static ConfigManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        initDefaultModels();
        initDefaultMCPS();
        initDefaultTools();
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context.getApplicationContext());
        }
        return instance;
    }

    private void initDefaultModels() {
        if (!prefs.contains(KEY_MODELS)) {
            List<AIModel> models = new ArrayList<AIModel>();
            models.add(new AIModel("gpt-3.5", "GPT-3.5", "OpenAI", "https://api.openai.com"));
            models.add(new AIModel("gpt-4", "GPT-4", "OpenAI", "https://api.openai.com"));
            models.add(new AIModel("claude-3", "Claude 3", "Anthropic", "https://api.anthropic.com"));
            models.add(new AIModel("gemini", "Gemini", "Google", "https://generativelanguage.googleapis.com"));
            saveModels(models);
        }
    }

    private void initDefaultMCPS() {
        if (!prefs.contains(KEY_MCPS)) {
            List<MCPInfo> mcps = new ArrayList<MCPInfo>();
            mcps.add(new MCPInfo("mcp-1", "Web Search MCP", "Search the web for information", "https://api.example.com/mcp/websearch"));
            mcps.add(new MCPInfo("mcp-2", "Weather MCP", "Get weather information", "https://api.example.com/mcp/weather"));
            saveMCPS(mcps);
        }
    }

    private void initDefaultTools() {
        if (!prefs.contains(KEY_TOOLS)) {
            List<ToolInfo> tools = new ArrayList<ToolInfo>();
            tools.add(new ToolInfo("tool-1", "Calculator", "Perform mathematical calculations", "calculate"));
            tools.add(new ToolInfo("tool-2", "Date Format", "Format dates and times", "formatDate"));
            tools.add(new ToolInfo("tool-3", "URL Parser", "Parse and extract URL information", "parseUrl"));
            saveTools(tools);
        }
    }

    public String getSelectedModelId() {
        return prefs.getString(KEY_SELECTED_MODEL_ID, "gpt-3.5");
    }

    public void setSelectedModelId(String modelId) {
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply();
    }

    public AIModel getSelectedModel() {
        String modelId = getSelectedModelId();
        for (AIModel model : getModels()) {
            if (model.getId().equals(modelId)) {
                return model;
            }
        }
        return getModels().get(0);
    }

    public List<AIModel> getModels() {
        String json = prefs.getString(KEY_MODELS, "[]");
        Type type = new TypeToken<ArrayList<AIModel>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveModels(List<AIModel> models) {
        String json = gson.toJson(models);
        prefs.edit().putString(KEY_MODELS, json).apply();
    }

    public void addModel(AIModel model) {
        List<AIModel> models = getModels();
        models.add(model);
        saveModels(models);
    }

    public void removeModel(String modelId) {
        List<AIModel> models = getModels();
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).getId().equals(modelId)) {
                models.remove(i);
                break;
            }
        }
        saveModels(models);
    }

    public List<MCPInfo> getMCPS() {
        String json = prefs.getString(KEY_MCPS, "[]");
        Type type = new TypeToken<ArrayList<MCPInfo>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveMCPS(List<MCPInfo> mcps) {
        String json = gson.toJson(mcps);
        prefs.edit().putString(KEY_MCPS, json).apply();
    }

    public void addMCP(MCPInfo mcp) {
        List<MCPInfo> mcps = getMCPS();
        mcps.add(mcp);
        saveMCPS(mcps);
    }

    public void removeMCP(String mcpId) {
        List<MCPInfo> mcps = getMCPS();
        for (int i = 0; i < mcps.size(); i++) {
            if (mcps.get(i).getId().equals(mcpId)) {
                mcps.remove(i);
                break;
            }
        }
        saveMCPS(mcps);
    }

    public void updateMCP(MCPInfo mcp) {
        List<MCPInfo> mcps = getMCPS();
        for (int i = 0; i < mcps.size(); i++) {
            if (mcps.get(i).getId().equals(mcp.getId())) {
                mcps.set(i, mcp);
                break;
            }
        }
        saveMCPS(mcps);
    }

    public List<ToolInfo> getTools() {
        String json = prefs.getString(KEY_TOOLS, "[]");
        Type type = new TypeToken<ArrayList<ToolInfo>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveTools(List<ToolInfo> tools) {
        String json = gson.toJson(tools);
        prefs.edit().putString(KEY_TOOLS, json).apply();
    }

    public void addTool(ToolInfo tool) {
        List<ToolInfo> tools = getTools();
        tools.add(tool);
        saveTools(tools);
    }

    public void removeTool(String toolId) {
        List<ToolInfo> tools = getTools();
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i).getId().equals(toolId)) {
                tools.remove(i);
                break;
            }
        }
        saveTools(tools);
    }

    public void updateTool(ToolInfo tool) {
        List<ToolInfo> tools = getTools();
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i).getId().equals(tool.getId())) {
                tools.set(i, tool);
                break;
            }
        }
        saveTools(tools);
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        initDefaultModels();
        initDefaultMCPS();
        initDefaultTools();
    }
}
