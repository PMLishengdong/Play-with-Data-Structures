package com.example.aichat.ai;

public interface StreamCallback {
    void onMessageId(String messageId, String id);
    void onContent(String messageId, String content);
    void onComplete(String messageId);
    void onError(String messageId, Exception e);
}
