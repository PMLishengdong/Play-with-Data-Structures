"use strict";

/**
 * 多 Agent 协作示例。
 * 模拟一个简单 Web 应用的开发流程：
 * 1. 协调者分析需求；
 * 2. 后端 Worker 设计 API；
 * 3. 前端 Worker 设计界面；
 * 4. 协调者汇总结果。
 * 使用 MockLlmProvider 离线运行。
 */

const {
    MultiAgentSystem,
    LlmProvider,
    logger
} = require("../src");

// 模拟 LLM Provider：根据输入关键词返回不同内容，用于演示多 Agent 协作
class MockLlmProvider extends LlmProvider {
    constructor(prefix) {
        super({});
        this.prefix = prefix || "";
    }

    async chat(messages) {
        const lastMessage = messages[messages.length - 1];
        const input = lastMessage && lastMessage.content ? lastMessage.content : "";

        if (input.indexOf("汇总") >= 0 || input.indexOf("综合") >= 0) {
            return {
                content: `${this.prefix}最终方案：后端提供 REST API 增删改查待办任务，前端使用 HTML + JS 实现列表展示与新增表单，两者通过 /api/items 对接。`,
                toolCalls: [],
                raw: {}
            };
        }

        if (input.indexOf("分析") >= 0 || input.indexOf("拆分") >= 0) {
            return {
                content: "建议拆分为：1) 后端 API 设计；2) 前端界面设计。",
                toolCalls: [],
                raw: {}
            };
        }

        if (input.indexOf("后端") >= 0 || input.indexOf("API") >= 0) {
            return {
                content: `${this.prefix}后端设计：提供 GET /api/items 与 POST /api/items 两个 REST 接口，返回 JSON 数据。`,
                toolCalls: [],
                raw: {}
            };
        }

        if (input.indexOf("前端") >= 0 || input.indexOf("界面") >= 0) {
            return {
                content: `${this.prefix}前端设计：使用纯 HTML + JS，提供列表展示与新增表单，调用后端 API。`,
                toolCalls: [],
                raw: {}
            };
        }

        return {
            content: `${this.prefix}已收到请求，请查看上下文继续。`,
            toolCalls: [],
            raw: {}
        };
    }
}

async function main() {
    const system = new MultiAgentSystem({
        llmConfig: { model: "mock" },
        sharedConfig: {
            allowCommandExecution: false
        }
    });

    // 创建后端专家 Worker
    system.createWorker({
        name: "backend-expert",
        role: "后端开发专家",
        expertise: "设计 RESTful API、数据库模型与服务端代码",
        llmProvider: new MockLlmProvider("[backend-expert] "),
        systemPrompt: "你是一个专业的后端开发工程师。",
        maxSteps: 5
    });

    // 创建前端专家 Worker
    system.createWorker({
        name: "frontend-expert",
        role: "前端开发专家",
        expertise: "设计页面结构、交互逻辑与前端代码",
        llmProvider: new MockLlmProvider("[frontend-expert] "),
        systemPrompt: "你是一个专业的前端开发工程师。",
        maxSteps: 5
    });

    // 为协调者也设置 Mock Provider，使其能生成分析与汇总
    system.coordinator.llm = new MockLlmProvider("[coordinator] ");

    const result = await system.run(
        "开发一个待办事项 Web 应用",
        [
            {
                workerName: "backend-expert",
                task: {
                    description: "设计待办事项应用的后端 API 与数据模型",
                    context: "待办事项应用需要支持增删改查任务。"
                }
            },
            {
                workerName: "frontend-expert",
                task: {
                    description: "设计待办事项应用的前端界面与交互",
                    context: "前端需要展示任务列表并支持新增任务。"
                }
            }
        ]
    );

    logger.info("最终结果:");
    console.log(result);

    logger.info("黑板上收集的数据:");
    console.log(JSON.stringify(system.getBlackboardData(), null, 4));
}

main().catch(function (err) {
    logger.error(`多 Agent 示例运行失败: ${err.message}`);
    process.exit(1);
});
