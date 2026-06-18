package com.aichat.app.tools;

import org.json.JSONObject;

import java.util.Map;

/**
 * A tool the LLM can invoke. Each tool has a unique id (name) and a JSON
 * schema for its arguments. The result is a plain string suitable for
 * injecting back into the LLM context.
 */
public interface Tool {

    /** Tool identifier (also used by the LLM to call). */
    String getId();

    /** Human readable display name. */
    String getDisplayName();

    /** Short description for the LLM. */
    String getDescription();

    /** JSON schema of arguments, e.g. {"type":"object","properties":{...}}. */
    String getArgumentSchema();

    /** Execute the tool with the given argument map. */
    String execute(Map<String, Object> arguments) throws Exception;
}
