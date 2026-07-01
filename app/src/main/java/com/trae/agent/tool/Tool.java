package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

/**
 * A tool that the agent can invoke.
 * Each tool has a name, description, and an execute method.
 */
public interface Tool {

    /** Unique tool name referenced by the LLM. */
    String getName();

    /** Human-readable description of what this tool does. */
    String getDescription();

    /** JSON schema of the expected arguments (OpenAI function-calling format). */
    String getParametersJsonSchema();

    /**
     * Execute the tool with the given arguments (string-keyed map from JSON).
     *
     * @param args argument map parsed from the LLM's tool call
     * @return execution result
     */
    ToolResult execute(java.util.Map<String, Object> args);
}