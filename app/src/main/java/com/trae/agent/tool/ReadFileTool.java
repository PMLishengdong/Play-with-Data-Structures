package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Reads the contents of a file from the filesystem.
 */
public class ReadFileTool implements Tool {

    private static final String SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"}"
            + "},"
            + "\"required\":[\"file_path\"]"
            + "}";

    @Override
    public String getName() { return "read_file"; }

    @Override
    public String getDescription() {
        return "Read the full contents of a file from the filesystem. Use this to understand existing code.";
    }

    @Override
    public String getParametersJsonSchema() { return SCHEMA; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String path = (String) args.get("file_path");
        if (path == null || path.isEmpty()) {
            return ToolResult.fail("Missing required argument: file_path");
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            return ToolResult.ok(new String(bytes));
        } catch (IOException e) {
            return ToolResult.fail("Error reading file " + path + ": " + e.getMessage());
        }
    }
}