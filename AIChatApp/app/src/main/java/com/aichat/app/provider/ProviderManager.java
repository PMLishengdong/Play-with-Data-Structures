package com.aichat.app.provider;

import com.aichat.app.model.ProviderConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of AI providers. For "openai" and "custom" the same
 * {@link OpenAIProvider} implementation is reused since the wire format is
 * identical.
 */
public class ProviderManager {
    private static final ProviderManager INSTANCE = new ProviderManager();

    public static ProviderManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, AIProvider> providers = new HashMap<String, AIProvider>();

    private ProviderManager() {
        providers.put("openai", new OpenAIProvider());
        providers.put("custom", new OpenAIProvider());
        providers.put("anthropic", new AnthropicProvider());
    }

    public AIProvider get(String type) {
        if (type == null) return providers.get("openai");
        AIProvider p = providers.get(type);
        if (p != null) return p;
        return providers.get("openai");
    }

    public AIProvider forConfig(ProviderConfig cfg) {
        if (cfg == null) return providers.get("openai");
        return get(cfg.type);
    }
}
