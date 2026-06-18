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

public class GoogleProvider implements AIProvider {
    private final Map<String, HttpURLConnection> activeConnections;

    public GoogleProvider() {
        activeConnections = new ConcurrentHashMap<String, HttpURLConnection>();
    }

    @Override
    public void sendMessage(String messageId, String content, List<Message> history, AIModel model, StreamCallback callback) {
        try {
            String urlStr = model.getBaseUrl() + "/v1beta/models/" + model.getId() + ":streamGenerateContent?alt=sse";
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
                urlStr += "&key=" + model.getApiKey();
                url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
            }
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.setReadTimeout(0);

            activeConnections.put(messageId, connection);

            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append("{\"contents\":[");
            
            for (int i = 0; i < history.size(); i++) {
                Message msg = history.get(i);
                requestBuilder.append("{\"role\":\"").append(msg.isUserMessage() ? "user" : "model").append("\",");
                requestBuilder.append("\"parts\":[{\"text\":\"").append(escapeJson(msg.getContent())).append("\"}]}");
                if (i < history.size() - 1) {
                    requestBuilder.append(",");
                }
            }
            
            requestBuilder.append(",{\"role\":\"user\",\"parts\":[{\"text\":\"").append(escapeJson(content)).append("\"}]}");
            requestBuilder.append("]}");

            connection.getOutputStream().write(requestBuilder.toString().getBytes());
            connection.getOutputStream().flush();
            connection.getOutputStream().close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!activeConnections.containsKey(messageId)) {
                    break;
                }
                if (line.startsWith("data: ")) {
                    String json = line.substring(6);
                    int textStart = json.indexOf("\"text\":\"");
                    if (textStart != -1) {
                        int textEnd = json.indexOf("\"", textStart + 8);
                        if (textEnd != -1) {
                            String text = json.substring(textStart + 8, textEnd);
                            callback.onContent(messageId, text);
                        }
                    }
                    if (json.contains("\"finishReason\"")) {
                        break;
                    }
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
        return "Google";
    }
}
