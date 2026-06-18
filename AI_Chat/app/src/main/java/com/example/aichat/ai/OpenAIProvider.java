package com.example.aichat.ai;

import com.example.aichat.model.AIModel;
import com.example.aichat.model.ChatResponse;
import com.example.aichat.model.Message;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OpenAIProvider implements AIProvider {
    private final Gson gson;
    private final Map<String, HttpURLConnection> activeConnections;

    public OpenAIProvider() {
        gson = new Gson();
        activeConnections = new ConcurrentHashMap<String, HttpURLConnection>();
    }

    @Override
    public void sendMessage(String messageId, String content, List<Message> history, AIModel model, StreamCallback callback) {
        try {
            URL url = new URL(model.getBaseUrl() + "/v1/chat/completions");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + model.getApiKey());
            }
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.setReadTimeout(0);

            activeConnections.put(messageId, connection);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("model", model.getId());
            requestBody.put("stream", true);
            requestBody.put("max_tokens", model.getMaxTokens());

            List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
            for (Message msg : history) {
                Map<String, String> msgMap = new HashMap<String, String>();
                msgMap.put("role", msg.isUserMessage() ? "user" : "assistant");
                msgMap.put("content", msg.getContent());
                messages.add(msgMap);
            }
            Map<String, String> currentMsg = new HashMap<String, String>();
            currentMsg.put("role", "user");
            currentMsg.put("content", content);
            messages.add(currentMsg);
            requestBody.put("messages", messages);

            connection.getOutputStream().write(gson.toJson(requestBody).getBytes());
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
                    if (json.equals("[DONE]")) {
                        break;
                    }
                    try {
                        ChatResponse.StreamResponse response = gson.fromJson(json, ChatResponse.StreamResponse.class);
                        if (response != null && response.getChoices() != null && response.getChoices().length > 0) {
                            ChatResponse.StreamChoice choice = response.getChoices()[0];
                            if (choice.getDelta() != null) {
                                if (choice.getDelta().getContent() != null) {
                                    callback.onContent(messageId, choice.getDelta().getContent());
                                }
                            }
                            if (choice.getFinish_reason() != null) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // ignore parsing errors
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

    @Override
    public void stop(String messageId) {
        HttpURLConnection connection = activeConnections.remove(messageId);
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }
}
