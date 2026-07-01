package com.trae.agent.llm;

import com.trae.agent.model.Message;
import com.trae.agent.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * OpenAI-compatible LLM client.
 * Supports chat completions with function/tool calling.
 */
public class LlmClient {

    private final LlmConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LlmClient(LlmConfig config) {
        this.config = config;
    }

    /**
     * Send a chat completion request synchronously.
     *
     * @param messages  conversation history
     * @param toolsJson JSON array of tool definitions (OpenAI tool format), may be null
     * @return parsed LLM response
     */
    public LlmResponse chat(List<Message> messages, JSONArray toolsJson) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());
        body.put("messages", messagesToJson(messages));
        if (toolsJson != null && toolsJson.length() > 0) {
            body.put("tools", toolsJson);
        }

        String responseJson = postJson(config.getBaseUrl() + "/chat/completions", body.toString());

        return parseResponse(responseJson);
    }

    /**
     * Async variant.
     */
    public Future<LlmResponse> chatAsync(List<Message> messages, JSONArray toolsJson) {
        return executor.submit(() -> chat(messages, toolsJson));
    }

    // ---- HTTP ----

    private String postJson(String urlStr, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        BufferedReader reader;
        if (status >= 200 && status < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        if (status < 200 || status >= 300) {
            throw new IOException("LLM API error " + status + ": " + sb);
        }
        return sb.toString();
    }

    // ---- JSON helpers ----

    private JSONArray messagesToJson(List<Message> messages) {
        JSONArray arr = new JSONArray();
        for (Message msg : messages) {
            JSONObject obj = new JSONObject();
            obj.put("role", msg.getRole());
            obj.put("content", msg.getContent() != null ? msg.getContent() : "");

            // tool_calls from assistant
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                JSONArray tcs = new JSONArray();
                for (ToolCall tc : msg.getToolCalls()) {
                    JSONObject tcObj = new JSONObject();
                    tcObj.put("id", tc.getId());
                    tcObj.put("type", tc.getType());
                    JSONObject func = new JSONObject();
                    func.put("name", tc.getName());
                    func.put("arguments", tc.getArguments() != null
                            ? new JSONObject(tc.getArguments()).toString()
                            : "{}");
                    tcObj.put("function", func);
                    tcs.put(tcObj);
                }
                obj.put("tool_calls", tcs);
            }

            // tool call result
            if ("tool".equals(msg.getRole())) {
                obj.put("tool_call_id", msg.getToolCallId());
            }

            arr.put(obj);
        }
        return arr;
    }

    private LlmResponse parseResponse(String json) {
        JSONObject root = new JSONObject(json);
        JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
        JSONObject messageJson = choice.getJSONObject("message");

        String role = messageJson.optString("role", "assistant");
        String content = messageJson.optString("content", null);

        LlmResponse response = new LlmResponse(role, content);

        if (messageJson.has("tool_calls")) {
            JSONArray tcs = messageJson.getJSONArray("tool_calls");
            List<ToolCall> toolCalls = new ArrayList<>();
            for (int i = 0; i < tcs.length(); i++) {
                JSONObject tc = tcs.getJSONObject(i);
                JSONObject func = tc.getJSONObject("function");
                String argsStr = func.optString("arguments", "{}");

                Map<String, Object> args = new HashMap<>();
                try {
                    JSONObject argsJson = new JSONObject(argsStr);
                    for (String key : argsJson.keySet()) {
                        args.put(key, argsJson.get(key));
                    }
                } catch (Exception ignored) {}

                ToolCall toolCall = new ToolCall(
                        tc.getString("id"),
                        func.getString("name"),
                        args
                );
                toolCalls.add(toolCall);
            }
            response.setToolCalls(toolCalls);
        }

        // token usage
        if (root.has("usage")) {
            JSONObject usage = root.getJSONObject("usage");
            response.setPromptTokens(usage.optInt("prompt_tokens", 0));
            response.setCompletionTokens(usage.optInt("completion_tokens", 0));
        }

        response.setFinishReason(choice.optString("finish_reason", "stop"));
        return response;
    }

    // ---- Response DTO ----

    public static class LlmResponse {
        private final String role;
        private final String content;
        private List<ToolCall> toolCalls;
        private int promptTokens;
        private int completionTokens;
        private String finishReason;

        public LlmResponse(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
        public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    }
}