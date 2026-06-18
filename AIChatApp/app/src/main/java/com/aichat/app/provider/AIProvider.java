package com.aichat.app.provider;

import com.aichat.app.model.ChatMessage;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.model.ToolCall;

import java.util.List;

/**
 * Common contract for an AI provider. Implementations send a chat completion
 * request and stream assistant text/tool-call deltas to the supplied
 * {@link StreamCallback}.
 */
public interface AIProvider {

    /** Stable type id: openai | anthropic | custom */
    String getType();

    /** Send a chat request and stream results.
     *  @param messages   conversation history
     *  @param tools      tool schemas (may be null)
     *  @param cfg        provider config (api key, base url, model)
     *  @param cb         streaming callback
     *  @return           a request handle exposing {@link #stop()} to cancel the stream
     */
    RequestHandle chat(List<ChatMessage> messages,
                       List<ToolSpec> tools,
                       ProviderConfig cfg,
                       StreamCallback cb);

    /** Build a tool spec from name/description/jsonSchema. */
    static ToolSpec tool(String name, String description, String jsonSchema) {
        return new ToolSpec(name, description, jsonSchema);
    }

    /** Plain spec holder; adapters convert to provider-specific shapes. */
    final class ToolSpec {
        public final String name;
        public final String description;
        public final String jsonSchema; // may be "{}" if no params
        public ToolSpec(String n, String d, String s) { name = n; description = d; jsonSchema = s; }
    }

    /** Handle for an in-flight streaming request. */
    interface RequestHandle {
        void stop();
        boolean isStopped();
    }

    /** Convenience holder for an assistant tool call parsed from a stream. */
    final class ToolCallDelta {
        public String id;
        public String name;
        public String argumentsFragment;
        public ToolCall toCall() {
            return new ToolCall(id, name, argumentsFragment == null ? "" : argumentsFragment);
        }
    }
}
