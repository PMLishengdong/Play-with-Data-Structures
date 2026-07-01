package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Performs an exact string replacement in a file.
 * Equivalent to "find and replace" — replaces the first occurrence of old_string with new_string.
 */
public class EditTool implements Tool {

    private static final String SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"},"
            + "\"old_string\":{\"type\":\"string\",\"description\":\"The exact text to replace (must be unique in the file)\"},"
            + "\"new_string\":{\"type\":\"string\",\"description\":\"The replacement text\"}"
            + "},"
            + "\"required\":[\"file_path\",\"old_string\",\"new_string\"]"
            + "}";

    @Override
    public String getName() { return "edit"; }

    @Override
    public String getDescription() {
        return "Replace existing text in a file with new text. Use this to modify existing code without rewriting the whole file.";
    }

    @Override
    public String getParametersJsonSchema() { return SCHEMA; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String path = (String) args.get("file_path");
        String oldStr = (String) args.get("old_string");
        String newStr = (String) args.get("new_string");

        if (path == null || oldStr == null || newStr == null) {
            return ToolResult.fail("Missing required arguments: file_path, old_string, new_string");
        }

        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            int index = content.indexOf(oldStr);
            if (index == -1) {
                return ToolResult.fail("old_string not found in " + path);
            }
            // Only replace first occurrence
            String newContent = content.substring(0, index)
                    + newStr
                    + content.substring(index + oldStr.length());
            Files.write(Paths.get(path), newContent.getBytes());
            return ToolResult.ok("Successfully edited " + path);
        } catch (IOException e) {
            return ToolResult.fail("Error editing file " + path + ": " + e.getMessage());
        }
    }
}