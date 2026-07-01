package com.trae.agent.planner;

import com.trae.agent.model.Message;
import com.trae.agent.model.Plan;
import com.trae.agent.model.Task;
import com.trae.agent.llm.LlmClient;
import com.trae.agent.llm.LlmClient.LlmResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decomposes a user goal into an ordered list of tasks.
 *
 * Uses the LLM to analyze the request and produce a step-by-step plan,
 * falling back to a simple rule-based split if the LLM call fails.
 */
public class TaskPlanner {

    private static final String PLANNER_PROMPT =
            "You are a planning agent for a code-writing AI assistant. " +
            "Given a user request, break it down into a logical sequence of tasks. " +
            "Each task should be a single, concrete step the agent can execute. " +
            "Return your answer as a numbered list, one task per line, with no extra commentary.\n\n" +
            "Example:\n" +
            "1. Read the existing project structure and understand the codebase\n" +
            "2. Create the utility class with the required methods\n" +
            "3. Write unit tests for the new class\n" +
            "4. Run tests to verify correctness\n\n" +
            "User request: ";

    private final LlmClient llmClient;

    public TaskPlanner(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Create a plan from a user goal.
     */
    public Plan createPlan(String goal) {
        List<Task> tasks;

        try {
            String llmPlan = requestPlanFromLLM(goal);
            tasks = parseTaskList(llmPlan);
            if (tasks.isEmpty()) {
                tasks = fallbackPlan(goal);
            }
        } catch (Exception e) {
            tasks = fallbackPlan(goal);
        }

        // assign IDs
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setId(String.valueOf(i + 1));
        }

        return new Plan(goal, tasks);
    }

    private String requestPlanFromLLM(String goal) throws IOException {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.userMessage(PLANNER_PROMPT + goal));

        LlmResponse response = llmClient.chat(messages, null);
        return response.getContent() != null ? response.getContent() : "";
    }

    private List<Task> parseTaskList(String text) {
        List<Task> tasks = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d+\\.\\s*(.+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String desc = matcher.group(1).trim();
            if (!desc.isEmpty()) {
                tasks.add(new Task(null, desc));
            }
        }
        return tasks;
    }

    private List<Task> fallbackPlan(String goal) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(null, "Analyze the request: " + goal));
        tasks.add(new Task(null, "Search the codebase for relevant files"));
        tasks.add(new Task(null, "Read and understand existing code"));
        tasks.add(new Task(null, "Implement the required changes"));
        tasks.add(new Task(null, "Verify the implementation"));
        return tasks;
    }
}