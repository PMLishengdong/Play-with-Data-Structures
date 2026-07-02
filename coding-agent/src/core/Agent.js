"use strict";

/**
 * Agent 主类。
 * 整合 LLM、工具注册表、记忆系统、世界书、上下文管理与任务规划，
 * 实现接收用户需求、循环调用 LLM 与工具、最终给出答案的完整流程。
 */

const OpenAiProvider = require("../llm/OpenAiProvider");
const ToolRegistry = require("../tools/ToolRegistry");
const AskUserTool = require("../tools/AskUserTool");
const FileOperationTool = require("../tools/FileOperationTool");
const ExecuteCommandTool = require("../tools/ExecuteCommandTool");
const MemorySystem = require("./MemorySystem");
const WorldBook = require("./WorldBook");
const ContextManager = require("./ContextManager");
const TaskPlanner = require("./TaskPlanner");
const logger = require("../utils/logger");

class Agent {
    /**
     * 创建 Agent 实例。
     * @param {Object} options - 配置项
     * @param {Object} options.llmConfig - LLM 配置
     * @param {LlmProvider} options.llmProvider - 可选的自定义 LLM Provider
     * @param {string} options.systemPrompt - 基础系统提示
     * @param {Array<BaseTool>} options.tools - 额外工具列表
     * @param {boolean} options.allowCommandExecution - 是否允许执行命令
     * @param {string} options.memoryPath - 长期记忆文件路径
     * @param {string} options.worldBookPath - 世界书文件路径
     * @param {number} options.maxSteps - 单轮任务最大 LLM 调用次数
     */
    constructor(options) {
        options = options || {};

        // Agent 全局配置
        this.config = {
            allowCommandExecution: options.allowCommandExecution === true,
            maxSteps: options.maxSteps || 20
        };

        // 初始化 LLM Provider
        if (options.llmProvider) {
            this.llm = options.llmProvider;
        } else {
            this.llm = new OpenAiProvider(options.llmConfig || {});
        }

        // 初始化记忆与世界书
        this.memory = new MemorySystem({ longTermPath: options.memoryPath });
        this.worldBook = new WorldBook({ filePath: options.worldBookPath });

        // 初始化上下文管理器
        this.contextManager = new ContextManager({
            memory: this.memory,
            worldBook: this.worldBook,
            systemPrompt: options.systemPrompt
        });

        // 初始化任务规划器
        this.taskPlanner = new TaskPlanner();

        // 初始化工具注册表并注册默认工具
        this.toolRegistry = new ToolRegistry();
        this._registerDefaultTools();
        if (options.tools && options.tools.length > 0) {
            this.toolRegistry.registerMany(options.tools);
        }
    }

    /**
     * 注册默认工具：询问用户、文件操作、执行命令。
     */
    _registerDefaultTools() {
        this.toolRegistry.register(new AskUserTool());
        this.toolRegistry.register(new FileOperationTool());
        this.toolRegistry.register(new ExecuteCommandTool());
    }

    /**
     * 处理用户输入的主入口。
     * @param {string} userInput - 用户需求
     * @returns {Promise<string>} Agent 最终回复
     */
    async run(userInput) {
        await this.memory.load();
        await this.worldBook.load();

        // 制定任务计划
        this.taskPlanner.plan(userInput);
        logger.info(`任务计划已生成，共 ${this.taskPlanner.tasks.length} 个子任务`);

        // 将用户输入加入短期记忆
        this.memory.addShortTerm("user", userInput);

        // 主循环：持续与 LLM 交互直到任务完成或达到最大步数
        let finalContent = "";
        let taskDone = false;
        for (let step = 0; step < this.config.maxSteps; step += 1) {
            const messages = await this._buildMessages(userInput);
            // 当所有子任务已执行完毕后，不再向 LLM 提供工具，等待其给出最终总结
            const tools = taskDone ? [] : this.toolRegistry.toOpenAiTools();

            let response;
            try {
                response = await this.llm.chat(messages, tools);
            } catch (err) {
                logger.error(`LLM 调用失败: ${err.message}`);
                return `Agent 运行出错: ${err.message}`;
            }

            // 将 LLM 回复加入短期记忆
            this.memory.addShortTerm("assistant", response.content, {
                tool_calls: response.toolCalls
            });

            // 如果模型返回纯文本且没有工具调用，视为最终答案
            if (!response.toolCalls || response.toolCalls.length === 0) {
                finalContent = response.content;
                break;
            }

            // 处理工具调用；若全部子任务已完成，则下一轮不再提供工具以获取总结
            const allDone = await this._handleToolCalls(response.toolCalls);
            if (allDone) {
                taskDone = true;
            }
        }

        // 将最终答案加入短期记忆，并尝试保存一条长期摘要
        this.memory.addShortTerm("assistant", finalContent);
        await this._saveSummary(userInput, finalContent);

        return finalContent;
    }

    /**
     * 组装包含任务计划、世界书、记忆和当前输入的完整消息列表。
     * @param {string} userInput - 当前用户输入
     * @returns {Promise<Array<Object>>} 消息列表
     */
    async _buildMessages(userInput) {
        const messages = await this.contextManager.buildMessages(userInput);

        // 在 system 消息后追加当前任务计划
        if (this.taskPlanner.tasks.length > 0) {
            const planText = this.taskPlanner.toPromptText();
            const planMessage = {
                role: "system",
                content: planText
            };
            // 插入到第一个 system 消息之后
            messages.splice(1, 0, planMessage);
        }

        return messages;
    }

    /**
     * 处理 LLM 返回的工具调用列表。
     * @param {Array<Object>} toolCalls - tool_calls 数组
     * @returns {Promise<boolean>} 是否继续主循环
     */
    async _handleToolCalls(toolCalls) {
        const self = this;
        let continueLoop = true;

        for (let i = 0; i < toolCalls.length; i += 1) {
            const call = toolCalls[i];
            const toolCallId = call.id || `call_${i}`;
            const functionName = call.function && call.function.name;
            let args = {};

            try {
                args = JSON.parse(call.function.arguments || "{}");
            } catch (err) {
                logger.warn(`工具参数解析失败: ${err.message}`);
            }

            // 构建 Agent 上下文，传递给工具
            const context = {
                config: self.config,
                memory: self.memory,
                worldBook: self.worldBook,
                taskPlanner: self.taskPlanner
            };

            const result = await self.toolRegistry.execute(functionName, args, context);

            // 将工具结果加入短期记忆
            self.memory.addShortTerm("tool", result, {
                tool_call_id: toolCallId,
                name: functionName
            });

            // 根据工具类型更新任务状态
            if (functionName === "file_operation" && args.action === "write") {
                self.taskPlanner.completeCurrent(result);
            }
        }

        // 如果所有子任务都已完成，可让 LLM 再总结一次后退出
        if (this.taskPlanner.isDone()) {
            continueLoop = false;
        }

        return continueLoop;
    }

    /**
     * 保存任务摘要到长期记忆。
     * @param {string} userInput - 用户输入
     * @param {string} finalContent - 最终回复
     */
    async _saveSummary(userInput, finalContent) {
        try {
            const summary = `用户请求: ${userInput.substring(0, 80)}；结果: ${finalContent.substring(0, 80)}`;
            await this.memory.addLongTerm("summary", summary);
        } catch (err) {
            logger.warn(`保存长期记忆失败: ${err.message}`);
        }
    }
}

module.exports = Agent;
