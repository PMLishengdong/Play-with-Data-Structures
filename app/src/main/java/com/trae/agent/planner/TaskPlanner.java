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
            "你是一个代码编写AI助手的规划智能体。 " +
            "给定一个用户请求，将其分解为逻辑上的任务序列。 " +
            "每个任务应该是智能体可以执行的单个具体步骤。 " +
            "将你的答案以编号列表形式返回，每行一个任务，不要添加额外注释。\n\n" +
            "示例:\n" +
            "1. 阅读现有项目结构，理解代码库\n" +
            "2. 创建包含所需方法的工具类\n" +
            "3. 为新类编写单元测试\n" +
            "4. 运行测试验证正确性\n\n" +
            "用户请求: ";

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
        tasks.add(new Task(null, "分析请求: " + goal));
        tasks.add(new Task(null, "在代码库中搜索相关文件"));
        tasks.add(new Task(null, "阅读并理解现有代码"));
        tasks.add(new Task(null, "实施所需的更改"));
        tasks.add(new Task(null, "验证实现"));
        return tasks;
    }
}