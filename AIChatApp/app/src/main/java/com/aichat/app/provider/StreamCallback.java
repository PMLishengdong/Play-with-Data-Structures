package com.aichat.app.provider;

import com.aichat.app.model.ChatMessage;

/**
 * Streaming callback invoked from a background thread by the provider. The
 * chat layer is responsible for marshalling these events to the UI thread.
 */
public interface StreamCallback {
    /** Called for each text fragment. Multiple calls form the final assistant text. */
    void onText(String text);
    /** Called when the model requests a tool call. */
    void onToolCall(AIProvider.ToolCallDelta delta);
    /** Called when the stream is fully done. */
    void onComplete();
    /** Called on network / parse / api error. */
    void onError(Throwable error);
}
