package com.trae.agent.agent;

import com.trae.agent.llm.LlmClient;
import com.trae.agent.llm.LlmClient.LlmResponse;
import com.trae.agent.model.Message;
import com.trae.agent.model.Plan;
import com.trae.agent.model.Task;
import com.trae.agent.model.ToolCall;
import com.trae.agent.model.ToolResult;
import com.trae.agent.planner.TaskPlanner;
import com.trae.agent.tool.Tool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The core agent that orchestrates task planning, LLM reasoning, and tool execution.
 *
 * Flow:
 * 1. Parse user request → TaskPlanner creates a Plan
 * 2. For each task:
 *    a. Send conversation history + task context to LLM
 *    b. If LLM returns tool calls → execute each tool → feed results back
 *    c. Repeat until LLM produces a final answer for this task
 * 3. Aggregate all task outputs into a final response
 */
public class CodeAgent {

    private static final String SYSTEM_PROMPT =
            "You are an AI coding assistant that writes, reads, and modifies code. " +
            "You have access to a set of tools you can use to accomplish your task. " +
            "Think step by step. When you need to use a tool, use it. " +
            "When you are done with the current task, provide a clear summary of what was done. " +
            "Always include file paths and key details in your summaries.";

    private final LlmClient llmClient;
    private final TaskPlanner planner;
    private final Map<String, Tool> tools = new HashMap<>();
    private final List<Message> conversation = new ArrayList<>();
    private int maxIterationsPerTask = 15;

    public CodeAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.planner = new TaskPlanner(llmClient);
        this.conversation.add(Message.systemMessage(SYSTEM_PROMPT));
    }

    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public void setMaxIterationsPerTask(int max) {
        this.maxIterationsPerTask = max;
    }

    // ---- Main entry point ----

    public String execute(String userRequest) {
        StringBuilder finalOutput = new StringBuilder();

        // 1. Plan
        log("\n=== Planning ===");
        Plan plan = planner.createPlan(userRequest);
        log(plan.toString());

        // 2. Add user request to conversation
        conversation.add(Message.userMessage(userRequest));

        // 3. Execute each task
        while (!plan.isComplete()) {
            Task task = plan.getNextTask();
            task.setStatus(Task.Status.IN_PROGRESS);
            log("\n=== Executing Task: " + task.getDescription() + " ===");

            // Add task context as a user message
            conversation.add(Message.userMessage(
                    "Current task: " + task.getDescription()));

            String taskResult = executeTaskWithLLM();
            task.setStatus(Task.Status.COMPLETED);
            finalOutput.append("[").append(task.getId()).append("] ")
                    .append(task.getDescription()).append("\n")
                    .append(taskResult).append("\n\n");

            plan.advance();
        }

        // 4. Final summary
        String summary = generateFinalSummary(finalOutput.toString());
        log("\n=== Final Summary ===");
        log(summary);
        return summary;
    }

    // ---- Task execution loop ----

    private String executeTaskWithLLM() {
        for (int i = 0; i < maxIterationsPerTask; i++) {
            LlmResponse response = sendLLMRequest();

            if (response == null) {
                return "Failed to get response from LLM.";
            }

            // If LLM returns tool calls, execute them
            if (response.hasToolCalls()) {
                for (ToolCall tc : response.getToolCalls()) {
                    ToolResult result = executeToolCall(tc);
                    log("  Tool [" + tc.getName() + "] → " + (result.isSuccess() ? "OK" : "FAIL"));
                    conversation.add(Message.toolResultMessage(tc.getId(), result.toString()));
                }
                // Let the LLM see the results and decide next steps
                continue;
            }

            // No tool calls → LLM produced final answer for this task
            String content = response.getContent();
            if (content != null && !content.isEmpty()) {
                conversation.add(Message.assistantMessage(content));
                return content;
            }

            // If finish_reason is "stop", we're done
            if ("stop".equals(response.getFinishReason()) && response.getContent() != null) {
                conversation.add(Message.assistantMessage(response.getContent()));
                return response.getContent();
            }
        }

        return "Task reached maximum iterations (" + maxIterationsPerTask + ") without completion.";
    }

    // ---- LLM interaction ----

    private LlmResponse sendLLMRequest() {
        try {
            JSONArray toolsJson = buildToolsJson();
            LlmResponse response = llmClient.chat(conversation, toolsJson);

            if (response.getPromptTokens() > 0) {
                log("  [Tokens: " + response.getPromptTokens() + " in → "
                        + response.getCompletionTokens() + " out]");
            }
            return response;
        } catch (IOException e) {
            log("  LLM call failed: " + e.getMessage());
            return null;
        }
    }

    // ---- Tool execution ----

    private ToolResult executeToolCall(ToolCall tc) {
        Tool tool = tools.get(tc.getName());
        if (tool == null) {
            return ToolResult.fail("Unknown tool: " + tc.getName()
                    + ". Available tools: " + tools.keySet());
        }
        try {
            return tool.execute(tc.getArguments());
        } catch (Exception e) {
            return ToolResult.fail("Tool " + tc.getName() + " threw exception: " + e.getMessage());
        }
    }

    // ---- Tool definitions for LLM ----

    private JSONArray buildToolsJson() {
        JSONArray arr = new JSONArray();
        for (Tool tool : tools.values()) {
            JSONObject obj = new JSONObject();
            obj.put("type", "function");
            JSONObject func = new JSONObject();
            func.put("name", tool.getName());
            func.put("description", tool.getDescription());
            func.put("parameters", new JSONObject(tool.getParametersJsonSchema()));
            obj.put("function", func);
            arr.put(obj);
        }
        return arr;
    }

    // ---- Final summary ----

    private String generateFinalSummary(String taskResults) {
        List<Message> summaryMessages = new ArrayList<>();
        summaryMessages.add(Message.systemMessage(
                "Summarize the following work into a concise final report for the user. " +
                "Include what was accomplished, what files were created/modified, and any important details."));
        summaryMessages.add(Message.userMessage(taskResults));

        try {
            LlmResponse response = llmClient.chat(summaryMessages, null);
            return response.getContent() != null ? response.getContent() : taskResults;
        } catch (IOException e) {
            return taskResults;
        }
    }

    // ---- Logging ----

    private void log(String msg) {
        System.out.println("[CodeAgent] " + msg);
    }

    /** Returns the raw conversation history (useful for debugging). */
    public List<Message> getConversation() {
        return conversation;
    }
}