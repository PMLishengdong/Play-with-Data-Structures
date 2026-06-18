package com.aichat.app.model;

/**
 * AI provider configuration. Stored in shared preferences as JSON.
 *
 * type values:
 *  - "openai"     OpenAI / OpenAI-compatible (Chat Completions, stream SSE)
 *  - "anthropic"  Anthropic Messages API with stream
 *  - "custom"     OpenAI-compatible custom endpoint
 */
public class ProviderConfig {
    public String id;
    public String name;
    public String type;       // openai | anthropic | custom
    public String baseUrl;    // e.g. https://api.openai.com/v1
    public String apiKey;
    public String model;      // default model
    public boolean enabled = true;
    public long createdAt;

    public ProviderConfig() {
        this.createdAt = System.currentTimeMillis();
    }
}
