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
 * Anthropic Messages API provider with streaming SSE.
 *
 * Note: Anthropic separates "system" from the messages array, and uses
 * content_block_start / content_block_delta events for both text and tool_use.
 */
public class AnthropicProvider implements AIProvider {
    private static final String TAG = "AnthropicProvider";

    @Override
    public String getType() {
        return "anthropic";
    }

    @Override
    public RequestHandle chat(final List<ChatMessage> messages,
                              final List<ToolSpec> tools,
                              final ProviderConfig cfg,
                              final StreamCallback cb) {
        final AnthropicHandle handle = new AnthropicHandle();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runStream(messages, tools, cfg, cb, handle);
            }
        }, "AnthropicProvider-Stream");
        t.setDaemon(true);
        t.start();
        return handle;
    }

    private void runStream(List<ChatMessage> messages,
                           List<ToolSpec> tools,
                           ProviderConfig cfg,
                           StreamCallback cb,
                           AnthropicHandle handle) {
        try {
            String base = cfg.baseUrl == null ? "" : cfg.baseUrl.trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String url = base + "/v1/messages";

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("x-api-key", cfg.apiKey == null ? "" : cfg.apiKey);
            headers.put("anthropic-version", "2023-06-01");
            headers.put("Accept", "text/event-stream");
            headers.put("Content-Type", "application/json");

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
            // Anthropic SSE event types we care about:
            //  - content_block_start with content_block of type text or tool_use
            //  - content_block_delta with delta.text or delta.partial_json
            //  - message_stop
            final SSEStreamReader reader = new SSEStreamReader(is, new SSEStreamReader.SSEHandler() {
                private String currentToolId;
                private String currentToolName;
                @Override
                public void onData(String data) {
                    // data is the raw JSON of the event
                    try {
                        JSONObject obj = new JSONObject(data);
                        String type = obj.optString("type", "");
                        if ("content_block_start".equals(type)) {
                            JSONObject cb1 = obj.optJSONObject("content_block");
                            if (cb1 != null) {
                                String bt = cb1.optString("type", "");
                                if ("tool_use".equals(bt)) {
                                    currentToolId = cb1.optString("id", "");
                                    currentToolName = cb1.optString("name", "");
                                } else {
                                    currentToolId = null;
                                    currentToolName = null;
                                }
                            }
                        } else if ("content_block_delta".equals(type)) {
                            JSONObject delta = obj.optJSONObject("delta");
                            if (delta != null) {
                                String dt = delta.optString("type", "");
                                if ("text_delta".equals(dt)) {
                                    String text = delta.optString("text", "");
                                    if (text.length() > 0) cb.onText(text);
                                } else if ("input_json_delta".equals(dt)) {
                                    String frag = delta.optString("partial_json", "");
                                    if (frag.length() > 0) {
                                        AIProvider.ToolCallDelta d = new AIProvider.ToolCallDelta();
                                        d.id = currentToolId;
                                        d.name = currentToolName;
                                        d.argumentsFragment = frag;
                                        cb.onToolCall(d);
                                    }
                                }
                            }
                        } else if ("message_stop".equals(type)) {
                            // done
                        } else if ("error".equals(type)) {
                            cb.onError(new RuntimeException("Anthropic error: " + obj.optJSONObject("error")));
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
        root.put("max_tokens", 4096);

        String systemPrompt = null;
        JSONArray arr = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (MessageRole.SYSTEM.equals(m.role)) {
                systemPrompt = m.content;
                continue;
            }
            if (MessageRole.TOOL.equals(m.role)) {
                JSONObject o = new JSONObject();
                o.put("role", "user");
                JSONArray content = new JSONArray();
                JSONObject block = new JSONObject();
                block.put("type", "tool_result");
                block.put("tool_use_id", m.toolCallId == null ? "" : m.toolCallId);
                block.put("content", m.content == null ? "" : m.content);
                content.put(block);
                o.put("content", content);
                arr.put(o);
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("role", MessageRole.ASSISTANT.equals(m.role) ? "assistant" : "user");
            JSONArray content = new JSONArray();
            JSONObject block = new JSONObject();
            block.put("type", "text");
            block.put("text", m.content == null ? "" : m.content);
            content.put(block);
            o.put("content", content);
            arr.put(o);
        }
        root.put("messages", arr);
        if (systemPrompt != null) root.put("system", systemPrompt);

        if (tools != null && tools.size() > 0) {
            JSONArray ta = new JSONArray();
            for (int i = 0; i < tools.size(); i++) {
                ToolSpec t = tools.get(i);
                JSONObject tool = new JSONObject();
                tool.put("name", t.name);
                if (t.description != null) tool.put("description", t.description);
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
                tool.put("input_schema", params);
                ta.put(tool);
            }
            root.put("tools", ta);
        }
        return root.toString();
    }

    private static class AnthropicHandle implements RequestHandle {
        private SSEStreamReader reader;
        private volatile boolean stopped;
        public void attachReader(SSEStreamReader r) { this.reader = r; }
        @Override public void stop() { stopped = true; if (reader != null) reader.stop(); }
        @Override public boolean isStopped() { return stopped; }
    }
}
