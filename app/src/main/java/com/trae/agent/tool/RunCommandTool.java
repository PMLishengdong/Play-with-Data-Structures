package com.trae.agent.tool;

import com.trae.agent.model.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a shell command and returns stdout + stderr.
 */
public class RunCommandTool implements Tool {

    private static final String SCHEMA = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"command\":{\"type\":\"string\",\"description\":\"Shell command to execute\"},"
            + "\"working_dir\":{\"type\":\"string\",\"description\":\"Working directory (optional)\"}"
            + "},"
            + "\"required\":[\"command\"]"
            + "}";

    @Override
    public String getName() { return "run_command"; }

    @Override
    public String getDescription() {
        return "Execute a shell command and capture its output. Use this to build, test, lint, or run git operations.";
    }

    @Override
    public String getParametersJsonSchema() { return SCHEMA; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String command = (String) args.get("command");
        String workDir = (String) args.get("working_dir");

        if (command == null || command.isEmpty()) {
            return ToolResult.fail("Missing required argument: command");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            if (workDir != null && !workDir.isEmpty()) {
                pb.directory(new java.io.File(workDir));
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.fail("Command timed out after 60s:\n" + output);
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode == 0) {
                return ToolResult.ok(result.isEmpty() ? "(no output)" : result);
            } else {
                return ToolResult.fail("Exit code " + exitCode + ":\n" + result);
            }
        } catch (Exception e) {
            return ToolResult.fail("Error executing command: " + e.getMessage());
        }
    }
}