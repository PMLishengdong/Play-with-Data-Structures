"use strict";

const config = require("../config");

/**
 * Agent 核心类
 * 负责整合 LLM、工具、记忆、上下文与任务规划，驱动 ReAct 式思考-行动循环。
 */
class Agent {
    /**
     * 构造函数
     * @param {Object} options - 配置项
     * @param {Object} options.llmClient - LLM 客户端实例
     * @param {Object} options.toolRegistry - 工具注册表实例
     * @param {Object} options.contextManager - 上下文管理器实例
     * @param {Object} options.memoryStore - 记忆存储实例
     * @param {Object} options.taskPlanner - 任务规划器实例
     */
    constructor(options) {
        this.llmClient = options.llmClient;
        this.toolRegistry = options.toolRegistry;
        this.contextManager = options.contextManager;
        this.memoryStore = options.memoryStore;
        this.taskPlanner = options.taskPlanner;
        this.maxToolCalls = config.agent.maxToolCallsPerTurn;
        this.maxRounds = config.agent.maxRounds;
    }

    /**
     * 构建系统提示词，注入任务计划与记忆信息
     * @returns {string} - 系统提示词
     */
    buildSystemPrompt() {
        const basePrompt = this.contextManager.systemPrompt;
        const planText = this.taskPlanner.formatPlan();
        const memoryText = this.memoryStore.formatMemory();
        const toolNote = "可用工具：" + this.getToolNames().join(", ") + "。";
        const parts = [basePrompt, toolNote, planText];
        if (memoryText && memoryText.length > 0) {
            parts.push(memoryText);
        }
        return parts.join("\n\n");
    }

    /**
     * 获取所有工具名称
     * @returns {Array<string>} - 工具名称列表
     */
    getToolNames() {
        const definitions = this.toolRegistry.getDefinitions();
        const names = [];
        for (let i = 0; i < definitions.length; i++) {
            names.push(definitions[i].function.name);
        }
        return names;
    }

    /**
     * 运行 Agent 处理一次用户输入
     * @param {string} userInput - 用户输入
     * @returns {Promise<string>} - 最终回复
     */
    async run(userInput) {
        // 1. 创建任务计划
        this.taskPlanner.createPlan(userInput);
        this.taskPlanner.updateStatus("1", "in_progress");

        // 2. 将用户输入加入上下文
        this.contextManager.addUserMessage(userInput);

        // 3. 进入多轮 ReAct 循环
        let round = 0;
        while (round < this.maxRounds) {
            round++;

            // 更新系统提示词（动态注入计划与记忆）
            this.contextManager.systemPrompt = this.buildSystemPrompt();

            const messages = this.contextManager.getMessages();
            const tools = this.toolRegistry.getDefinitions();

            // 4. 调用 LLM
            let response;
            try {
                response = await this.llmClient.chat(messages, tools);
            } catch (error) {
                return `Agent 调用 LLM 出错: ${error.message}`;
            }

            const choice = response.choices && response.choices[0];
            if (!choice) {
                return "LLM 返回异常，无法解析响应。";
            }

            const message = choice.message;

            // 5. 如果助手直接回复文本，则结束本轮
            if (message.content && (!message.tool_calls || message.tool_calls.length === 0)) {
                this.contextManager.addAssistantMessage(message.content);
                this.taskPlanner.updateStatus("5", "completed");
                return message.content;
            }

            // 6. 处理工具调用
            if (message.tool_calls && message.tool_calls.length > 0) {
                this.contextManager.addToolCallMessage(message);
                const callResults = [];
                const callCount = Math.min(message.tool_calls.length, this.maxToolCalls);

                for (let i = 0; i < callCount; i++) {
                    const toolCall = message.tool_calls[i];
                    const toolName = toolCall.function.name;
                    let args;
                    try {
                        args = JSON.parse(toolCall.function.arguments);
                    } catch (error) {
                        args = {};
                    }

                    let result;
                    try {
                        result = await this.toolRegistry.execute(toolName, args);
                    } catch (error) {
                        result = { error: error.message };
                    }

                    const resultText = typeof result === "string" ? result : JSON.stringify(result);
                    this.contextManager.addToolResult(toolCall.id, resultText);
                    callResults.push({ tool: toolName, result: result });

                    // 更新任务计划状态（简单映射）
                    if (toolName === "askUser") {
                        this.taskPlanner.updateStatus("1", "completed");
                        this.taskPlanner.updateStatus("2", "in_progress");
                    } else if (toolName === "readFile" || toolName === "listFiles") {
                        this.taskPlanner.updateStatus("2", "completed");
                        this.taskPlanner.updateStatus("3", "in_progress");
                    } else if (toolName === "writeFile" || toolName === "executeCommand") {
                        this.taskPlanner.updateStatus("3", "completed");
                        this.taskPlanner.updateStatus("4", "in_progress");
                    }
                }

                // 7. 如果本轮只进行了一次工具调用且未再请求 LLM，则继续循环
                continue;
            }

            // 兜底：无内容也无工具调用
            return "LLM 未返回有效内容。";
        }

        return "Agent 已达到最大轮数，仍未完成。";
    }
}

module.exports = Agent;
