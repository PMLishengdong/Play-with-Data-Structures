package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Grep-like text search within the filesystem.
 */
public class SearchTool implements Tool {

    private static final String SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"pattern\":{\"type\":\"string\",\"description\":\"Regex pattern to search for\"},"
            + "\"path\":{\"type\":\"string\",\"description\":\"Directory or file path to search in\"},"
            + "\"file_pattern\":{\"type\":\"string\",\"description\":\"Optional glob pattern to filter files (e.g. *.java)\"}"
            + "},"
            + "\"required\":[\"pattern\",\"path\"]"
            + "}";

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() {
        return "Search for text patterns in the codebase using regex. Use this to find relevant code, class definitions, or usages.";
    }

    @Override
    public String getParametersJsonSchema() { return SCHEMA; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String pattern = (String) args.get("pattern");
        String path = (String) args.get("path");
        String filePattern = (String) args.get("file_pattern");

        if (pattern == null || path == null) {
            return ToolResult.fail("Missing required arguments: pattern, path");
        }

        StringBuilder result = new StringBuilder();
        try {
            Pattern regex = Pattern.compile(pattern);
            java.nio.file.Path startPath = Paths.get(path);

            try (Stream<java.nio.file.Path> stream = Files.walk(startPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> filePattern == null
                                || p.toString().endsWith(filePattern.replace("*", "")))
                        .limit(50)
                        .forEach(p -> {
                            try (Stream<String> lines = Files.lines(p)) {
                                lines.filter(l -> regex.matcher(l).find())
                                        .limit(5)
                                        .forEach(l -> result.append(p).append(": ")
                                                .append(l.trim()).append("\n"));
                            } catch (IOException ignored) {}
                        });
            }

            String output = result.toString().trim();
            if (output.isEmpty()) {
                return ToolResult.ok("No matches found for pattern: " + pattern);
            }
            return ToolResult.ok(output);
        } catch (IOException e) {
            return ToolResult.fail("Error searching: " + e.getMessage());
        }
    }
}