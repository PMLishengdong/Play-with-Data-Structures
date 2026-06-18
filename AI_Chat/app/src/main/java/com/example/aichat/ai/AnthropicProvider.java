package com.example.aichat.ai;

import com.example.aichat.model.AIModel;
import com.example.aichat.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnthropicProvider implements AIProvider {
    private final Map<String, HttpURLConnection> activeConnections;

    public AnthropicProvider() {
        activeConnections = new ConcurrentHashMap<String, HttpURLConnection>();
    }

    @Override
    public void sendMessage(String messageId, String content, List<Message> history, AIModel model, StreamCallback callback) {
        try {
            URL url = new URL(model.getBaseUrl() + "/v1/messages");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
                connection.setRequestProperty("x-api-key", model.getApiKey());
            }
            connection.setRequestProperty("anthropic-version", "2023-06-01");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.setReadTimeout(0);

            activeConnections.put(messageId, connection);

            StringBuilder messagesBuilder = new StringBuilder();
            messagesBuilder.append("{\"model\":\"").append(model.getId()).append("\",");
            messagesBuilder.append("\"max_tokens\":").append(model.getMaxTokens()).append(",");
            messagesBuilder.append("\"stream\":true,");
            messagesBuilder.append("\"messages\":[");

            for (int i = 0; i < history.size(); i++) {
                Message msg = history.get(i);
                messagesBuilder.append("{\"role\":\"").append(msg.isUserMessage() ? "user" : "assistant").append("\",");
                messagesBuilder.append("\"content\":\"").append(escapeJson(msg.getContent())).append("\"}");
                if (i < history.size() - 1) {
                    messagesBuilder.append(",");
                }
            }
            
            messagesBuilder.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(content)).append("\"}");
            messagesBuilder.append("]}");

            connection.getOutputStream().write(messagesBuilder.toString().getBytes());
            connection.getOutputStream().flush();
            connection.getOutputStream().close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!activeConnections.containsKey(messageId)) {
                    break;
                }
                if (line.startsWith("event: ") && line.contains("content_block_delta")) {
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String json = line.substring(6);
                            int contentStart = json.indexOf("\"text\":\"");
                            if (contentStart != -1) {
                                int contentEnd = json.indexOf("\"", contentStart + 8);
                                if (contentEnd != -1) {
                                    String text = json.substring(contentStart + 8, contentEnd);
                                    callback.onContent(messageId, text);
                                }
                            }
                            break;
                        }
                    }
                }
                if (line.contains("content_block_stop")) {
                    break;
                }
            }

            reader.close();
            activeConnections.remove(messageId);
            callback.onComplete(messageId);

        } catch (Exception e) {
            activeConnections.remove(messageId);
            callback.onError(messageId, e);
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public void stop(String messageId) {
        HttpURLConnection connection = activeConnections.remove(messageId);
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public String getProviderName() {
        return "Anthropic";
    }
}
