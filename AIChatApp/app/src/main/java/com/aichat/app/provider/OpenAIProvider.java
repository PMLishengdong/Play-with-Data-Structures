package com.aichat.app.provider;

import android.util.Log;

import com.aichat.app.model.ChatMessage;
import com.aichat.app.model.MessageRole;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.util.HttpUtil;
import com.aichat.app.util.SSEStreamReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat provider. Works with OpenAI, Azure-OpenAI, and any
 * custom endpoint that follows the /v1/chat/completions streaming SSE protocol.
 */
public class OpenAIProvider implements AIProvider {
    private static final String TAG = "OpenAIProvider";

    @Override
    public String getType() {
        return "openai";
    }

    @Override
    public RequestHandle chat(final List<ChatMessage> messages,
                              final List<ToolSpec> tools,
                              final ProviderConfig cfg,
                              final StreamCallback cb) {
        final OpenAIHandle handle = new OpenAIHandle();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runStream(messages, tools, cfg, cb, handle);
            }
        }, "OpenAIProvider-Stream");
        t.setDaemon(true);
        t.start();
        return handle;
    }

    private void runStream(List<ChatMessage> messages,
                           List<ToolSpec> tools,
                           ProviderConfig cfg,
                           StreamCallback cb,
                           OpenAIHandle handle) {
        try {
            String base = cfg.baseUrl == null ? "" : cfg.baseUrl.trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String url = base + "/chat/completions";

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer " + (cfg.apiKey == null ? "" : cfg.apiKey));
            headers.put("Accept", "text/event-stream");

            String body = buildRequest(messages, tools, cfg);
            HttpUtil.Response resp = HttpUtil.postStream(url, headers, body);

            if (resp.code >= 400) {
                String errBody = "";
                if (resp.stream != null) {
                    java.util.Scanner s = new java.util.Scanner(resp.stream, "UTF-8").useDelimiter("\\A");
                    errBody = s.hasNext() ? s.next() : "";
                }
                cb.onError(new RuntimeException("HTTP " + resp.code + ": " + errBody));
                HttpUtil.close(resp);
                return;
            }
            final InputStream is = resp.stream;
            final SSEStreamReader reader = new SSEStreamReader(is, new SSEStreamReader.SSEHandler() {
                @Override
                public void onData(String data) {
                    if ("[DONE]".equals(data)) return;
                    try {
                        JSONObject obj = new JSONObject(data);
                        JSONArray choices = obj.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) return;
                        JSONObject choice = choices.optJSONObject(0);
                        if (choice == null) return;
                        JSONObject delta = choice.optJSONObject("delta");
                        if (delta != null) {
                            String content = delta.optString("content", null);
                            if (content != null && content.length() > 0 && !content.equals("null")) {
                                cb.onText(content);
                            }
                            JSONArray toolCalls = delta.optJSONArray("tool_calls");
                            if (toolCalls != null) {
                                for (int i = 0; i < toolCalls.length(); i++) {
                                    JSONObject tc = toolCalls.optJSONObject(i);
                                    if (tc == null) continue;
                                    AIProvider.ToolCallDelta d = new AIProvider.ToolCallDelta();
                                    d.id = tc.optString("id", "");
                                    JSONObject fn = tc.optJSONObject("function");
                                    if (fn != null) {
                                        d.name = fn.optString("name", "");
                                        d.argumentsFragment = fn.optString("arguments", "");
                                    }
                                    cb.onToolCall(d);
                                }
                            }
                        }
                    } catch (JSONException ex) {
                        Log.w(TAG, "parse chunk error: " + ex.getMessage());
                    }
                }
                @Override
                public void onEventEnd() { /* no-op */ }
                @Override
                public void onEnd() { cb.onComplete(); }
            });
            handle.attachReader(reader);
            try {
                reader.readAll();
            } catch (Throwable ex) {
                if (handle.isStopped()) {
                    // user-cancelled, treat as complete
                    cb.onComplete();
                } else {
                    cb.onError(ex);
                }
            }
            HttpUtil.close(resp);
        } catch (Throwable ex) {
            cb.onError(ex);
        }
    }

    private String buildRequest(List<ChatMessage> messages, List<ToolSpec> tools, ProviderConfig cfg) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("model", cfg.model);
        root.put("stream", true);

        JSONArray arr = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            JSONObject o = new JSONObject();
            String role = m.role == null ? MessageRole.USER : m.role;
            o.put("role", role);
            if (m.content != null) o.put("content", m.content);
            if (MessageRole.TOOL.equals(role)) {
                if (m.toolCallId != null && m.toolCallId.length() > 0) o.put("tool_call_id", m.toolCallId);
                if (m.name != null && m.name.length() > 0) o.put("name", m.name);
            }
            arr.put(o);
        }
        root.put("messages", arr);

        if (tools != null && tools.size() > 0) {
            JSONArray ta = new JSONArray();
            for (int i = 0; i < tools.size(); i++) {
                ToolSpec t = tools.get(i);
                JSONObject fn = new JSONObject();
                fn.put("name", t.name);
                if (t.description != null) fn.put("description", t.description);
                JSONObject params;
                if (t.jsonSchema == null || t.jsonSchema.length() == 0) {
                    params = new JSONObject();
                } else {
                    try {
                        params = new JSONObject(t.jsonSchema);
                    } catch (JSONException ex) {
                        params = new JSONObject();
                    }
                }
                fn.put("parameters", params);
                JSONObject wrapper = new JSONObject();
                wrapper.put("type", "function");
                wrapper.put("function", fn);
                ta.put(wrapper);
            }
            root.put("tools", ta);
        }
        return root.toString();
    }

    private static class OpenAIHandle implements RequestHandle {
        private SSEStreamReader reader;
        private volatile boolean stopped;
        public void attachReader(SSEStreamReader r) { this.reader = r; }
        @Override public void stop() { stopped = true; if (reader != null) reader.stop(); }
        @Override public boolean isStopped() { return stopped; }
    }
}
