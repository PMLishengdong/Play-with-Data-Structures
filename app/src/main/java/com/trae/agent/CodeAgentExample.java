package com.trae.agent;

import com.trae.agent.agent.CodeAgent;
import com.trae.agent.llm.LlmClient;
import com.trae.agent.llm.LlmConfig;
import com.trae.agent.tool.EditTool;
import com.trae.agent.tool.ReadFileTool;
import com.trae.agent.tool.RunCommandTool;
import com.trae.agent.tool.SearchTool;
import com.trae.agent.tool.WriteFileTool;

/**
 * Example usage of CodeAgent.
 *
 * Before running, set the environment variables:
 *   LLM_API_KEY=sk-your-key
 *   LLM_BASE_URL=https://api.openai.com/v1   (optional, default)
 *   LLM_MODEL=gpt-4                          (optional, default)
 */
public class CodeAgentExample {

    public static void main(String[] args) {
        // ---- Configure LLM ----
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set LLM_API_KEY environment variable");
            System.exit(1);
        }

        LlmConfig config = LlmConfig.builder()
                .apiKey(apiKey)
                .baseUrl(getEnv("LLM_BASE_URL", "https://api.openai.com/v1"))
                .model(getEnv("LLM_MODEL", "gpt-4"))
                .temperature(0.2)
                .maxTokens(4096)
                .build();

        LlmClient llmClient = new LlmClient(config);

        // ---- Create Agent ----
        CodeAgent agent = new CodeAgent(llmClient);

        // ---- Register Tools ----
        agent.registerTool(new ReadFileTool());
        agent.registerTool(new WriteFileTool());
        agent.registerTool(new EditTool());
        agent.registerTool(new RunCommandTool());
        agent.registerTool(new SearchTool());

        // ---- Execute ----
        String request = args.length > 0
                ? String.join(" ", args)
                : "Create a Java utility class StringUtils in /tmp that has methods: " +
                  "reverse(String), isPalindrome(String), and capitalize(String). " +
                  "Then verify it compiles.";

        System.out.println("=== User Request ===");
        System.out.println(request);
        System.out.println();

        String result = agent.execute(request);

        System.out.println();
        System.out.println("=== Agent Result ===");
        System.out.println(result);
    }

    private static String getEnv(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}