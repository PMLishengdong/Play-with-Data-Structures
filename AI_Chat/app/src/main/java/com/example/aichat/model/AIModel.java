package com.example.aichat.model;

public class AIModel {
    private String id;
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private boolean enabled;
    private int maxTokens;

    public AIModel() {}

    public AIModel(String id, String name, String provider, String baseUrl) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.enabled = true;
        this.maxTokens = 4096;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String toString() {
        return name;
    }
}
