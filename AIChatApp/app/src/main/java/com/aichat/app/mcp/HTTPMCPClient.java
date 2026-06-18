package com.aichat.app.mcp;

import android.util.Log;

import com.aichat.app.model.MCPConfig;
import com.aichat.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * HTTP-based MCP client. Sends JSON-RPC 2.0 requests over HTTP POST. If the
 * server advertises a tools endpoint we hit it; otherwise we just call the
 * base endpoint with a tools/list request.
 */
public class HTTPMCPClient implements MCPClient {
    private static final String TAG = "HTTPMCPClient";

    private final MCPConfig config;
    private int idCounter = 0;
    private boolean ready;

    public HTTPMCPClient(MCPConfig config) {
        this.config = config;
    }

    @Override
    public void connect() {
        ready = true;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public List<MCPTool> listTools() throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId());
        req.put("method", "tools/list");
        req.put("params", new JSONObject());

        HttpUtil.Response resp = HttpUtil.post(config.endpoint, null, req.toString());
        try {
            if (resp.code >= 400) {
                throw new RuntimeException("HTTP " + resp.code + ": " + resp.body);
            }
            JSONObject root = new JSONObject(resp.body);
            JSONArray result = root.optJSONArray("result");
            if (result == null) {
                // some servers return {result:{tools:[...]}}
                JSONObject resObj = root.optJSONObject("result");
                if (resObj != null) result = resObj.optJSONArray("tools");
            }
            List<MCPTool> tools = new ArrayList<MCPTool>();
            if (result != null) {
                for (int i = 0; i < result.length(); i++) {
                    JSONObject o = result.optJSONObject(i);
                    if (o == null) continue;
                    String name = o.optString("name", "");
                    if (name.length() == 0) continue;
                    String desc = o.optString("description", "");
                    JSONObject schema = o.optJSONObject("inputSchema");
                    if (schema == null) schema = o.optJSONObject("input_schema");
                    String schemaStr = schema == null ? "{}" : schema.toString();
                    tools.add(new MCPTool(config.id, name, desc, schemaStr));
                }
            }
            return tools;
        } catch (JSONException ex) {
            throw new RuntimeException("parse error: " + ex.getMessage() + " body=" + resp.body, ex);
        } finally {
            HttpUtil.close(resp);
        }
    }

    @Override
    public MCPTool.Result callTool(String toolName, Map<String, Object> arguments) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId());
        req.put("method", "tools/call");
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", mapToJson(arguments));
        req.put("params", params);

        HttpUtil.Response resp = HttpUtil.post(config.endpoint, null, req.toString());
        try {
            if (resp.code >= 400) {
                return new MCPTool.Result(false, "HTTP " + resp.code + ": " + resp.body, null);
            }
            JSONObject root = new JSONObject(resp.body);
            if (root.has("error")) {
                return new MCPTool.Result(false, root.optJSONObject("error").toString(), null);
            }
            JSONObject result = root.optJSONObject("result");
            String text = "";
            if (result != null) {
                JSONArray content = result.optJSONArray("content");
                if (content != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject c = content.optJSONObject(i);
                        if (c == null) continue;
                        String t = c.optString("type", "text");
                        if ("text".equals(t)) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(c.optString("text", ""));
                        } else {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(c.toString());
                        }
                    }
                    text = sb.toString();
                } else {
                    text = result.toString();
                }
            }
            return new MCPTool.Result(true, text, result);
        } catch (JSONException ex) {
            return new MCPTool.Result(false, "parse error: " + ex.getMessage(), null);
        } finally {
            HttpUtil.close(resp);
        }
    }

    @Override
    public void close() {
        ready = false;
    }

    @Override
    public MCPConfig getConfig() {
        return config;
    }

    private int nextId() {
        synchronized (this) {
            return ++idCounter;
        }
    }

    private static JSONObject mapToJson(Map<String, Object> map) {
        if (map == null) return new JSONObject();
        JSONObject o = new JSONObject();
        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            Object v = e.getValue();
            try {
                if (v instanceof Map) {
                    o.put(e.getKey(), mapToJson((Map<String, Object>) v));
                } else if (v instanceof List) {
                    JSONArray a = new JSONArray();
                    List<?> l = (List<?>) v;
                    for (int i = 0; i < l.size(); i++) {
                        Object item = l.get(i);
                        if (item instanceof Map) {
                            a.put(mapToJson((Map<String, Object>) item));
                        } else {
                            a.put(item);
                        }
                    }
                    o.put(e.getKey(), a);
                } else {
                    o.put(e.getKey(), v);
                }
            } catch (JSONException ex) {
                Log.w(TAG, "skip arg: " + e.getKey() + " -> " + v);
            }
        }
        return o;
    }

    public static Map<String, Object> jsonToMap(JSONObject o) {
        if (o == null) return new HashMap<String, Object>();
        Map<String, Object> m = new HashMap<String, Object>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = o.opt(k);
            m.put(k, v);
        }
        return m;
    }
}
