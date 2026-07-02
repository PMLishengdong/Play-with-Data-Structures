"use strict";

const config = require("../config");

/**
 * Agent 主控类：负责任务规划、工具调用循环、记忆沉淀与上下文管理。
 */
class Agent {
    /**
     * 构造 Agent。
     * @param {Object} deps 依赖对象
     * @param {LlmClient} deps.llmClient
     * @param {ToolRegistry} deps.toolRegistry
     * @param {MemoryStore} deps.memoryStore
     * @param {ContextManager} deps.contextManager
     * @param {Planner} deps.planner
     * @param {RagEngine} [deps.ragEngine]
     */
    constructor(deps) {
        this.llmClient = deps.llmClient;
        this.toolRegistry = deps.toolRegistry;
        this.memoryStore = deps.memoryStore;
        this.contextManager = deps.contextManager;
        this.planner = deps.planner;
        this.ragEngine = deps.ragEngine || null;
        this.maxIterations = config.agent.maxToolIterations;
    }

    /**
     * 执行一次用户任务。
     * @param {string} task 用户任务描述
     * @returns {Promise<string>} 最终回答
     */
    async run(task) {
        console.log("\n============================");
        console.log("任务:", task);
        console.log("============================\n");

        // 1. 任务规划
        const plan = await this.planner.createPlan(task, this.toolRegistry.listNames());
        console.log("生成计划:");
        plan.forEach(function (item) {
            console.log(`  步骤 ${item.step}: ${item.action} ${item.tool ? `[${item.tool}]` : ""}`);
        });

        const planText = "执行计划:\n" + plan.map(function (item) {
            return `${item.step}. ${item.action}${item.tool ? ` （工具：${item.tool}）` : ""} -> ${item.expected}`;
        }).join("\n");
        this.memoryStore.addShortTerm(planText, "planner");

        // 2. 注入用户任务
        this.contextManager.addUserMessage(task);

        // 3. 工具调用循环
        for (let iteration = 0; iteration < this.maxIterations; iteration += 1) {
            const messages = await this.contextManager.buildMessages();
            const tools = this.toolRegistry.toOpenAiTools();

            const choice = await this.llmClient.chat(messages, {
                tools: tools,
                tool_choice: "auto",
                temperature: 0.3
            });

            const message = choice.message;

            // 情况 A：LLM 请求调用工具
            if (message.tool_calls && message.tool_calls.length > 0) {
                this.contextManager.addAssistantToolCall({
                    role: "assistant",
                    content: message.content || "",
                    tool_calls: message.tool_calls
                });

                for (let i = 0; i < message.tool_calls.length; i += 1) {
                    const toolCall = message.tool_calls[i];
                    console.log(`\n[调用工具] ${toolCall.function.name}(${toolCall.function.arguments || "{}"})`);
                    const result = await this.toolRegistry.execute(toolCall);
                    console.log(`[工具结果] ${result.content.substring(0, 500)}${result.content.length > 500 ? "..." : ""}`);

                    this.contextManager.addToolResult(result.tool_call_id, result.name, result.content);
                    this.memoryStore.addShortTerm(
                        `工具 ${result.name} 调用结果: ${result.content.substring(0, 200)}`,
                        "tool-result"
                    );
                }
                continue;
            }

            // 情况 B：LLM 直接返回最终回答
            const answer = message.content || "";
            this.contextManager.addAssistantMessage(answer);
            this.memoryStore.addLongTerm(`任务完成: ${task} -> ${answer.substring(0, 200)}`, "agent-summary");
            return answer;
        }

        const timeoutMsg = `已达到最大工具调用轮数（${this.maxIterations}），任务未能在本轮完成。`;
        this.memoryStore.addShortTerm(timeoutMsg, "agent");
        return timeoutMsg;
    }
}

module.exports = Agent;
