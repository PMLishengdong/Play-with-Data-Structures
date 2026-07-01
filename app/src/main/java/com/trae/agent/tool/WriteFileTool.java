package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Writes content to a file (creates or overwrites).
 */
public class WriteFileTool implements Tool {

    private static final String SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"},"
            + "\"content\":{\"type\":\"string\",\"description\":\"Full content to write to the file\"}"
            + "},"
            + "\"required\":[\"file_path\",\"content\"]"
            + "}";

    @Override
    public String getName() { return "write_file"; }

    @Override
    public String getDescription() {
        return "Create a new file or overwrite an existing file with the given content. Use this to write new code.";
    }

    @Override
    public String getParametersJsonSchema() { return SCHEMA; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String path = (String) args.get("file_path");
        String content = (String) args.get("content");
        if (path == null || path.isEmpty()) {
            return ToolResult.fail("Missing required argument: file_path");
        }
        if (content == null) {
            return ToolResult.fail("Missing required argument: content");
        }
        try {
            Path target = Paths.get(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes());
            return ToolResult.ok("Successfully wrote " + target.toAbsolutePath());
        } catch (IOException e) {
            return ToolResult.fail("Error writing file " + path + ": " + e.getMessage());
        }
    }
}