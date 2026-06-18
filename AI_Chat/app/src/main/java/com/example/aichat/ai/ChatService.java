package com.example.aichat.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.aichat.model.AIModel;
import com.example.aichat.model.Message;
import com.example.aichat.util.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatService {
    private static ChatService instance;
    private final ConfigManager configManager;
    private final Map<String, AIProvider> providers;
    private final Map<String, StreamRequest> activeRequests;
    private final Handler mainHandler;

    private ChatService(Context context) {
        configManager = ConfigManager.getInstance(context);
        providers = new HashMap<String, AIProvider>();
        activeRequests = new HashMap<String, StreamRequest>();
        mainHandler = new Handler(Looper.getMainLooper());
        initProviders();
    }

    public static synchronized ChatService getInstance(Context context) {
        if (instance == null) {
            instance = new ChatService(context.getApplicationContext());
        }
        return instance;
    }

    private void initProviders() {
        providers.put("OpenAI", new OpenAIProvider());
        providers.put("Anthropic", new AnthropicProvider());
        providers.put("Google", new GoogleProvider());
    }

    public void sendMessage(String content, List<Message> history, StreamCallback callback) {
        String messageId = UUID.randomUUID().toString();
        AIModel model = configManager.getSelectedModel();
        AIProvider provider = providers.get(model.getProvider());
        
        if (provider == null) {
            provider = providers.get("OpenAI");
        }

        StreamRequest request = new StreamRequest(messageId, content, history, model, provider, callback);
        activeRequests.put(messageId, request);
        
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    provider.sendMessage(messageId, content, history, model, new WrappedCallback(messageId, callback));
                } catch (Exception e) {
                    postError(messageId, e, callback);
                }
            }
        };
        
        new Thread(runnable).start();
    }

    public void stop(String messageId) {
        StreamRequest request = activeRequests.get(messageId);
        if (request != null) {
            request.provider.stop(messageId);
            activeRequests.remove(messageId);
        }
    }

    public void stopAll() {
        for (String messageId : activeRequests.keySet()) {
            stop(messageId);
        }
        activeRequests.clear();
    }

    private void postError(final String messageId, final Exception e, final StreamCallback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(messageId, e);
            }
        });
    }

    private class WrappedCallback implements StreamCallback {
        private final String messageId;
        private final StreamCallback delegate;

        public WrappedCallback(String messageId, StreamCallback delegate) {
            this.messageId = messageId;
            this.delegate = delegate;
        }

        @Override
        public void onMessageId(final String messageId, final String id) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    delegate.onMessageId(messageId, id);
                }
            });
        }

        @Override
        public void onContent(final String messageId, final String content) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    delegate.onContent(messageId, content);
                }
            });
        }

        @Override
        public void onComplete(final String messageId) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    delegate.onComplete(messageId);
                    activeRequests.remove(messageId);
                }
            });
        }

        @Override
        public void onError(final String messageId, final Exception e) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    delegate.onError(messageId, e);
                    activeRequests.remove(messageId);
                }
            });
        }
    }

    private static class StreamRequest {
        String messageId;
        String content;
        List<Message> history;
        AIModel model;
        AIProvider provider;
        StreamCallback callback;

        StreamRequest(String messageId, String content, List<Message> history, 
                      AIModel model, AIProvider provider, StreamCallback callback) {
            this.messageId = messageId;
            this.content = content;
            this.history = history;
            this.model = model;
            this.provider = provider;
            this.callback = callback;
        }
    }

    public void registerProvider(String name, AIProvider provider) {
        providers.put(name, provider);
    }
}
