"use strict";

const config = require("./config");
const OpenAIClient = require("./llm/OpenAIClient");
const ToolRegistry = require("./tools/ToolRegistry");
const AskUserTool = require("./tools/AskUserTool");
const FileTool = require("./tools/FileTool");
const CommandTool = require("./tools/CommandTool");
const ContextManager = require("./context/ContextManager");
const MemoryStore = require("./memory/MemoryStore");
const TaskPlanner = require("./planning/TaskPlanner");
const Agent = require("./core/Agent");

/**
 * 创建并配置 Agent 实例
 * @returns {Agent} - 配置完成的 Agent
 */
function createAgent() {
    // 1. 初始化 LLM 客户端
    const llmClient = new OpenAIClient(config.llm);

    // 2. 初始化工具注册表并注册工具
    const toolRegistry = new ToolRegistry();
    toolRegistry.register(new AskUserTool());
    toolRegistry.register(new FileTool());
    toolRegistry.register(new CommandTool({ workDir: config.agent.workDir }));

    // 3. 初始化上下文、记忆、规划器
    const contextManager = new ContextManager({
        systemPrompt: buildSystemPrompt(),
        maxMessages: config.memory.shortTermMaxMessages
    });
    const memoryStore = new MemoryStore();
    const taskPlanner = new TaskPlanner();

    // 4. 组装 Agent
    const agent = new Agent({
        llmClient: llmClient,
        toolRegistry: toolRegistry,
        contextManager: contextManager,
        memoryStore: memoryStore,
        taskPlanner: taskPlanner
    });

    return agent;
}

/**
 * 构建系统提示词
 * @returns {string} - 系统提示词
 */
function buildSystemPrompt() {
    return [
        "你是一个专业的编程 Agent，名为 CodingAgent。",
        "你的职责是帮助用户完成软件开发任务，包括分析需求、阅读代码、编写代码、运行命令和总结结果。",
        "你可以使用以下工具来完成任务：",
        "- askUser: 当信息不足或需要用户确认时使用。",
        "- fileTool: 读取、写入或列出文件/目录。",
        "- executeCommand: 执行 shell 命令（如安装依赖、编译、运行测试）。",
        "",
        "工作原则：",
        "1. 先分析需求，再制定计划。",
        "2. 读写文件前请先确认路径。",
        "3. 执行命令前请评估安全性。",
        "4. 完成任务后请给出清晰的总结。"
    ].join("\n");
}

/**
 * 主函数
 * 读取命令行参数作为用户输入，并运行 Agent。
 */
async function main() {
    const userInput = process.argv.slice(2).join(" ") || "请帮我写一个 Hello World 程序";
    console.log(`用户输入: ${userInput}`);

    const agent = createAgent();

    // 演示：写入一条长期记忆
    agent.memoryStore.set("preferred_language", "JavaScript");

    try {
        const result = await agent.run(userInput);
        console.log("\n===== Agent 回复 =====");
        console.log(result);
    } catch (error) {
        console.error("Agent 运行出错:", error.message);
        process.exit(1);
    }
}

main();
