package com.aichat.app.chat;

import com.aichat.app.model.ChatMessage;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.model.ToolCall;
import com.aichat.app.provider.AIProvider;
import com.aichat.app.provider.ProviderManager;
import com.aichat.app.provider.StreamCallback;
import com.aichat.app.storage.ConfigManager;
import com.aichat.app.tools.ToolManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates a single LLM turn: send the conversation history to the
 * provider, and (if the model emits tool calls) execute them via
 * {@link ToolManager} and feed the results back, looping up to
 * {@link #MAX_TOOL_ITERATIONS} times.
 *
 * The whole pipeline is async — results are delivered on a background thread
 * via the supplied {@link Listener}. Stop with {@link #stop()}.
 */
public class ChatManager {
    public static final int MAX_TOOL_ITERATIONS = 4;

    public interface Listener {
        /** Called for each text fragment streamed from the assistant. */
        void onText(String text);
        /** Called when a tool call starts. */
        void onToolCallStart(String toolId, String name);
        /** Called when a tool call's JSON arguments are completed. */
        void onToolCallArgs(String toolId, String args);
        /** Called when a tool finishes executing. */
        void onToolCallResult(String toolId, String result);
        /** Called when the assistant turn is fully done. */
        void onComplete();
        /** Called on any error. */
        void onError(Throwable error);
    }

    private final ConfigManager config;
    private AIProvider.RequestHandle current;
    private volatile boolean stopped;
    private final Object lock = new Object();

    public ChatManager(ConfigManager config) {
        this.config = config;
    }

    public void stop() {
        stopped = true;
        synchronized (lock) {
            if (current != null) {
                try { current.stop(); } catch (Exception ignored) {}
            }
        }
    }

    public boolean isStopped() { return stopped; }

    public void send(final List<ChatMessage> history, final ProviderConfig cfg, final Listener listener) {
        stopped = false;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runPipeline(history, cfg, listener);
                } catch (Throwable ex) {
                    listener.onError(ex);
                }
            }
        }, "ChatManager-Pipeline");
        t.setDaemon(true);
        t.start();
    }

    private void runPipeline(List<ChatMessage> history, ProviderConfig cfg, final Listener listener) throws Exception {
        if (cfg == null) {
            listener.onError(new IllegalStateException("provider not configured"));
            return;
        }
        List<ChatMessage> convo = new ArrayList<ChatMessage>(history);
        List<AIProvider.ToolSpec> toolSpecs = ToolManager.getInstance().currentToolSpecs();
        for (int iter = 0; iter < MAX_TOOL_ITERATIONS; iter++) {
            if (stopped) return;
            final StringBuilder assistantText = new StringBuilder();
            final List<ToolCall> toolCalls = new ArrayList<ToolCall>();
            final Map<String, String> toolArgs = new HashMap<String, String>();
            // Each delta in OpenAI may have separate id entries. We'll coalesce
            // by id, joining arguments fragments.
            final Map<String, ToolCall> callById = new HashMap<String, ToolCall>();
            final Map<String, String> nameById = new HashMap<String, String>();
            AIProvider provider = ProviderManager.getInstance().forConfig(cfg);
            synchronized (lock) {
                current = provider.chat(convo, toolSpecs, cfg, new StreamCallback() {
                    @Override
                    public void onText(String text) {
                        assistantText.append(text);
                        listener.onText(text);
                    }
                    @Override
                    public void onToolCall(AIProvider.ToolCallDelta delta) {
                        String id = delta.id == null || delta.id.length() == 0
                                ? ("call_" + callById.size()) : delta.id;
                        ToolCall call = (ToolCall) callById.get(id);
                        if (call == null) {
                            call = new ToolCall(id, delta.name, "");
                            callById.put(id, call);
                            if (delta.name != null && delta.name.length() > 0) {
                                nameById.put(id, delta.name);
                            }
                            listener.onToolCallStart(id, delta.name);
                        }
                        if (delta.name != null && delta.name.length() > 0 && call.name == null) {
                            call.name = delta.name;
                            nameById.put(id, delta.name);
                        }
                        if (delta.argumentsFragment != null && delta.argumentsFragment.length() > 0) {
                            String prev = toolArgs.get(id);
                            String now = (prev == null ? "" : prev) + delta.argumentsFragment;
                            toolArgs.put(id, now);
                            call.arguments = now;
                            listener.onToolCallArgs(id, now);
                        }
                    }
                    @Override
                    public void onComplete() { /* streamed end */ }
                    @Override
                    public void onError(Throwable error) { listener.onError(error); }
                });
            }
            // Wait for the stream to finish
            synchronized (lock) {
                AIProvider.RequestHandle h = current;
                if (h != null) {
                    // No public await; the call() returned the handle so the
                    // request runs in its own thread. We poll until it stops.
                    while (!h.isStopped() && !stopped) {
                        try { lock.wait(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                }
            }
            if (stopped) return;
            // Save assistant message
            ChatMessage assistantMsg = new ChatMessage("assistant", assistantText.toString());
            assistantMsg.timestamp = System.currentTimeMillis();
            convo.add(assistantMsg);

            // If there were tool calls, execute them and feed results back
            if (callById.isEmpty()) {
                listener.onComplete();
                return;
            }
            List<ToolCall> calls = new ArrayList<ToolCall>(callById.values());
            for (int i = 0; i < calls.size(); i++) {
                ToolCall call = calls.get(i);
                String result = ToolManager.getInstance().execute(call.name, call.arguments == null ? "" : call.arguments);
                listener.onToolCallResult(call.id, result);
                ChatMessage toolMsg = new ChatMessage();
                toolMsg.role = "tool";
                toolMsg.content = result;
                toolMsg.name = call.name;
                toolMsg.toolCallId = call.id;
                toolMsg.timestamp = System.currentTimeMillis();
                convo.add(toolMsg);
            }
        }
        listener.onComplete();
    }
}
