package com.example.aichat.ai;

import com.example.aichat.model.AIModel;
import com.example.aichat.model.Message;

import java.util.List;

public interface AIProvider {
    void sendMessage(String messageId, String content, List<Message> history, AIModel model, StreamCallback callback);
    void stop(String messageId);
    String getProviderName();
}
