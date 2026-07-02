"use strict";

const LlmClient = require("./llm/llmClient");
const ToolRegistry = require("./tools/toolRegistry");
const createAskUserTool = require("./tools/askUserTool");
const { readFileTool, writeFileTool, editFileTool } = require("./tools/fileTools");
const executeCommandTool = require("./tools/executeCommandTool");
const MemoryStore = require("./memory/memoryStore");
const ContextManager = require("./context/contextManager");
const RagEngine = require("./rag/ragEngine");
const Planner = require("./planning/planner");
const Agent = require("./agent/agent");

/**
 * 组装所有模块并启动 Agent，执行示例任务。
 */
async function main() {
    // 缺少 LLM API Key 时给出友好提示，不直接发起网络请求
    if (!process.env.LLM_API_KEY) {
        console.log("请先设置 LLM_API_KEY 环境变量，例如:");
        console.log("  export LLM_API_KEY=your-api-key");
        console.log("可选环境变量: LLM_BASE_URL, LLM_MODEL, LLM_EMBEDDING_MODEL\n");
        console.log("然后运行: npm start");
        return;
    }

    const llmClient = new LlmClient();

    // 注册工具
    const toolRegistry = new ToolRegistry();
    toolRegistry.registerMany([
        createAskUserTool(),
        readFileTool,
        writeFileTool,
        editFileTool,
        executeCommandTool
    ]);

    const memoryStore = new MemoryStore();
    const ragEngine = new RagEngine(llmClient);
    const contextManager = new ContextManager({
        memoryStore: memoryStore,
        ragEngine: ragEngine
    });
    const planner = new Planner(llmClient);

    const agent = new Agent({
        llmClient: llmClient,
        toolRegistry: toolRegistry,
        memoryStore: memoryStore,
        contextManager: contextManager,
        planner: planner,
        ragEngine: ragEngine
    });

    // 向 RAG 索引注入项目规范，便于后续任务检索参考
    await injectProjectKnowledge(ragEngine);

    // 从命令行读取任务，否则使用默认示例
    const task = process.argv.slice(2).join(" ") || "在当前项目根目录创建一个 sayHello.js，导出一个返回 'Hello, World!' 的函数";

    try {
        const answer = await agent.run(task);
        console.log("\n============================");
        console.log("最终回答:");
        console.log("============================");
        console.log(answer);
    } catch (error) {
        console.error("\nAgent 执行出错:", error.message);
        process.exitCode = 1;
    }
}

/**
 * 注入示例项目知识到 RAG 索引，方便 Agent 在写代码时检索。
 * @param {RagEngine} ragEngine
 */
async function injectProjectKnowledge(ragEngine) {
    const conventions = [
        "项目使用 Node.js v16.14.0，采用 CommonJS 模块规范，通过 module.exports 导出。",
        "代码风格：四空格缩进、语句末尾加分号、变量与函数使用 camelCase。",
        "字符串规则：复杂或含插值的字符串使用反引号，简单字面量使用双引号。",
        "禁用 ?. 和 ?? 运算符，使用 && 或显式判空代替。",
        "异步逻辑统一使用 async/await + try/catch。",
        "文件命名使用驼峰风格，如 myModule.js、contextManager.js。",
        "每个方法需添加简单中文注释，核心逻辑加详细中文注释。"
    ];

    for (let i = 0; i < conventions.length; i += 1) {
        await ragEngine.addDocument(conventions[i], { source: "project-conventions", index: i });
    }
}

main().catch(function (error) {
    console.error("未捕获的异常:", error);
    process.exit(1);
});
