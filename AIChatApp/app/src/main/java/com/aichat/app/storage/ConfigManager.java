package com.aichat.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.aichat.app.model.ChatMessage;
import com.aichat.app.model.Conversation;
import com.aichat.app.model.MCPConfig;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.model.ToolConfig;
import com.aichat.app.util.Ids;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized configuration & persistence manager. Uses SharedPreferences with
 * JSON-encoded arrays for list data. Thread-safe via synchronized methods.
 */
public class ConfigManager {
    private static final String PREF = "aichat_prefs";

    private static final String K_PROVIDERS = "providers";
    private static final String K_MCPS = "mcps";
    private static final String K_TOOLS = "tools";
    private static final String K_CONVERSATIONS = "conversations";
    private static final String K_CURRENT_PROVIDER = "current_provider_id";
    private static final String K_CURRENT_MODEL = "current_model";
    private static final String K_CURRENT_CONVERSATION = "current_conversation_id";
    private static final String K_MESSAGES_PREFIX = "messages_";

    private static ConfigManager sInstance;

    public static synchronized ConfigManager get(Context ctx) {
        if (sInstance == null) {
            sInstance = new ConfigManager(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private final Context appContext;
    private final SharedPreferences sp;

    private ConfigManager(Context ctx) {
        this.appContext = ctx;
        this.sp = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------- Provider ----------

    public synchronized List<ProviderConfig> listProviders() {
        List<ProviderConfig> out = new ArrayList<ProviderConfig>();
        JSONArray arr = readArray(K_PROVIDERS);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromProvider(o));
        }
        return out;
    }

    public synchronized ProviderConfig getProvider(String id) {
        if (id == null) return null;
        List<ProviderConfig> list = listProviders();
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).id)) return list.get(i);
        }
        return null;
    }

    public synchronized void saveProvider(ProviderConfig p) {
        if (p == null) return;
        if (p.id == null || p.id.length() == 0) p.id = Ids.newId();
        List<ProviderConfig> list = listProviders();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (p.id.equals(list.get(i).id)) {
                list.set(i, p);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(p);
        writeArray(K_PROVIDERS, toJsonArray(list));
    }

    public synchronized void deleteProvider(String id) {
        if (id == null) return;
        List<ProviderConfig> list = listProviders();
        List<ProviderConfig> keep = new ArrayList<ProviderConfig>();
        for (int i = 0; i < list.size(); i++) {
            if (!id.equals(list.get(i).id)) keep.add(list.get(i));
        }
        writeArray(K_PROVIDERS, toJsonArray(keep));
        if (id.equals(getCurrentProviderId())) {
            if (keep.size() > 0) {
                setCurrentProviderId(keep.get(0).id, keep.get(0).model);
            } else {
                setCurrentProviderId(null, null);
            }
        }
    }

    // ---------- MCP ----------

    public synchronized List<MCPConfig> listMCPs() {
        List<MCPConfig> out = new ArrayList<MCPConfig>();
        JSONArray arr = readArray(K_MCPS);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromMCP(o));
        }
        return out;
    }

    public synchronized MCPConfig getMCP(String id) {
        if (id == null) return null;
        List<MCPConfig> list = listMCPs();
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).id)) return list.get(i);
        }
        return null;
    }

    public synchronized void saveMCP(MCPConfig m) {
        if (m == null) return;
        if (m.id == null || m.id.length() == 0) m.id = Ids.newId();
        List<MCPConfig> list = listMCPs();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (m.id.equals(list.get(i).id)) {
                list.set(i, m);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(m);
        writeArray(K_MCPS, toJsonArray(list));
    }

    public synchronized void deleteMCP(String id) {
        if (id == null) return;
        List<MCPConfig> list = listMCPs();
        List<MCPConfig> keep = new ArrayList<MCPConfig>();
        for (int i = 0; i < list.size(); i++) {
            if (!id.equals(list.get(i).id)) keep.add(list.get(i));
        }
        writeArray(K_MCPS, toJsonArray(keep));
    }

    // ---------- Tool ----------

    public synchronized List<ToolConfig> listTools() {
        List<ToolConfig> out = new ArrayList<ToolConfig>();
        JSONArray arr = readArray(K_TOOLS);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromTool(o));
        }
        return out;
    }

    public synchronized ToolConfig getTool(String id) {
        if (id == null) return null;
        List<ToolConfig> list = listTools();
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).id)) return list.get(i);
        }
        return null;
    }

    public synchronized void saveTool(ToolConfig t) {
        if (t == null) return;
        if (t.id == null || t.id.length() == 0) t.id = Ids.newId();
        List<ToolConfig> list = listTools();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (t.id.equals(list.get(i).id)) {
                list.set(i, t);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(t);
        writeArray(K_TOOLS, toJsonArray(list));
    }

    public synchronized void deleteTool(String id) {
        if (id == null) return;
        List<ToolConfig> list = listTools();
        List<ToolConfig> keep = new ArrayList<ToolConfig>();
        for (int i = 0; i < list.size(); i++) {
            if (!id.equals(list.get(i).id)) keep.add(list.get(i));
        }
        writeArray(K_TOOLS, toJsonArray(keep));
    }

    // ---------- Current selection ----------

    public synchronized String getCurrentProviderId() {
        return sp.getString(K_CURRENT_PROVIDER, null);
    }

    public synchronized String getCurrentModel() {
        return sp.getString(K_CURRENT_MODEL, null);
    }

    public synchronized void setCurrentProviderId(String id, String model) {
        SharedPreferences.Editor e = sp.edit();
        e.putString(K_CURRENT_PROVIDER, id);
        e.putString(K_CURRENT_MODEL, model);
        e.apply();
    }

    // ---------- Conversation ----------

    public synchronized List<Conversation> listConversations() {
        List<Conversation> out = new ArrayList<Conversation>();
        JSONArray arr = readArray(K_CONVERSATIONS);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromConversation(o));
        }
        Collections.sort(out, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return Long.valueOf(b.updatedAt).compareTo(Long.valueOf(a.updatedAt));
            }
        });
        return out;
    }

    public synchronized Conversation getConversation(String id) {
        if (id == null) return null;
        List<Conversation> list = listConversations();
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).id)) return list.get(i);
        }
        return null;
    }

    public synchronized Conversation createConversation(String providerId, String model) {
        Conversation c = new Conversation();
        c.id = Ids.newId();
        c.title = "新对话";
        c.providerId = providerId;
        c.model = model;
        List<Conversation> list = listConversations();
        list.add(c);
        writeArray(K_CONVERSATIONS, toJsonArray(list));
        setCurrentConversationId(c.id);
        return c;
    }

    public synchronized void saveConversation(Conversation c) {
        if (c == null || c.id == null) return;
        c.updatedAt = System.currentTimeMillis();
        List<Conversation> list = listConversations();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (c.id.equals(list.get(i).id)) {
                list.set(i, c);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(c);
        writeArray(K_CONVERSATIONS, toJsonArray(list));
    }

    public synchronized void deleteConversation(String id) {
        if (id == null) return;
        List<Conversation> list = listConversations();
        List<Conversation> keep = new ArrayList<Conversation>();
        for (int i = 0; i < list.size(); i++) {
            if (!id.equals(list.get(i).id)) keep.add(list.get(i));
        }
        writeArray(K_CONVERSATIONS, toJsonArray(keep));
        SharedPreferences.Editor e = sp.edit();
        e.remove(K_MESSAGES_PREFIX + id);
        e.apply();
        if (id.equals(getCurrentConversationId())) {
            setCurrentConversationId(null);
        }
    }

    public synchronized String getCurrentConversationId() {
        return sp.getString(K_CURRENT_CONVERSATION, null);
    }

    public synchronized void setCurrentConversationId(String id) {
        SharedPreferences.Editor e = sp.edit();
        e.putString(K_CURRENT_CONVERSATION, id);
        e.apply();
    }

    public synchronized List<ChatMessage> loadMessages(String conversationId) {
        List<ChatMessage> out = new ArrayList<ChatMessage>();
        if (conversationId == null) return out;
        String raw = sp.getString(K_MESSAGES_PREFIX + conversationId, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    ChatMessage m = new ChatMessage();
                    m.role = o.optString("role", "user");
                    m.content = o.optString("content", "");
                    m.name = o.optString("name", "");
                    m.toolCallId = o.optString("toolCallId", "");
                    m.timestamp = o.optLong("timestamp", System.currentTimeMillis());
                    out.add(m);
                }
            }
        } catch (JSONException e) {
            // ignore
        }
        return out;
    }

    public synchronized void saveMessages(String conversationId, List<ChatMessage> messages) {
        if (conversationId == null) return;
        JSONArray arr = new JSONArray();
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                JSONObject o = new JSONObject();
                try {
                    o.put("role", m.role == null ? "" : m.role);
                    o.put("content", m.content == null ? "" : m.content);
                    o.put("name", m.name == null ? "" : m.name);
                    o.put("toolCallId", m.toolCallId == null ? "" : m.toolCallId);
                    o.put("timestamp", m.timestamp);
                } catch (JSONException e) {
                    // ignore
                }
                arr.put(o);
            }
        }
        SharedPreferences.Editor e = sp.edit();
        e.putString(K_MESSAGES_PREFIX + conversationId, arr.toString());
        e.apply();
        // update conversation timestamp
        Conversation c = getConversation(conversationId);
        if (c != null) saveConversation(c);
    }

    // ---------- Helpers ----------

    private JSONArray readArray(String key) {
        String raw = sp.getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void writeArray(String key, JSONArray arr) {
        SharedPreferences.Editor e = sp.edit();
        e.putString(key, arr == null ? "[]" : arr.toString());
        e.apply();
    }

    private JSONArray toJsonArray(List<?> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (int i = 0; i < list.size(); i++) arr.put(list.get(i));
        return arr;
    }

    private ProviderConfig fromProvider(JSONObject o) {
        ProviderConfig p = new ProviderConfig();
        p.id = o.optString("id");
        p.name = o.optString("name");
        p.type = o.optString("type", "openai");
        p.baseUrl = o.optString("baseUrl", "");
        p.apiKey = o.optString("apiKey", "");
        p.model = o.optString("model", "");
        p.enabled = o.optBoolean("enabled", true);
        p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return p;
    }

    private MCPConfig fromMCP(JSONObject o) {
        MCPConfig m = new MCPConfig();
        m.id = o.optString("id");
        m.name = o.optString("name");
        m.type = o.optString("type", "http");
        m.endpoint = o.optString("endpoint", "");
        m.description = o.optString("description", "");
        m.enabled = o.optBoolean("enabled", true);
        m.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return m;
    }

    private ToolConfig fromTool(JSONObject o) {
        ToolConfig t = new ToolConfig();
        t.id = o.optString("id");
        t.name = o.optString("name");
        t.type = o.optString("type", "builtin");
        t.description = o.optString("description", "");
        t.params = o.optString("params", "");
        t.enabled = o.optBoolean("enabled", true);
        t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return t;
    }

    private Conversation fromConversation(JSONObject o) {
        Conversation c = new Conversation();
        c.id = o.optString("id");
        c.title = o.optString("title", "新对话");
        c.providerId = o.optString("providerId", null);
        c.model = o.optString("model", null);
        c.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        c.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        return c;
    }

    public Context getAppContext() {
        return appContext;
    }
}
