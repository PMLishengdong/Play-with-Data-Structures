"use strict";

/**
 * Agent 使用示例。
 * 使用 MockLlmProvider 模拟 LLM 的多次响应，演示 Agent 的工具调用循环。
 */

const {
    Agent,
    LlmProvider,
    logger
} = require("../src");

// 模拟 LLM Provider：按预定顺序返回响应，便于离线运行示例
class MockLlmProvider extends LlmProvider {
    constructor() {
        super({});
        this.step = 0;
    }

    /**
     * 按步骤返回预定义的 LLM 响应。
     */
    async chat(messages, tools) {
        this.step += 1;
        logger.info(`Mock LLM 第 ${this.step} 次调用`);

        if (this.step === 1) {
            // 第一步：让 Agent 写入文件
            return {
                content: "现在开始生成文件。",
                toolCalls: [
                    {
                        id: "call_1",
                        function: {
                            name: "file_operation",
                            arguments: JSON.stringify({
                                action: "write",
                                filePath: "./example/output/hello.js",
                                content: "console.log('Hello, Coding Agent!');\n"
                            })
                        }
                    }
                ],
                raw: {}
            };
        }

        if (this.step === 2) {
            // 第二步：让 Agent 读取文件以验证
            return {
                content: "文件已写入，正在验证内容。",
                toolCalls: [
                    {
                        id: "call_2",
                        function: {
                            name: "file_operation",
                            arguments: JSON.stringify({
                                action: "read",
                                filePath: "./example/output/hello.js"
                            })
                        }
                    }
                ],
                raw: {}
            };
        }

        // 第三步：给出最终总结
        return {
            content: "已完成。文件 ./example/output/hello.js 已生成并验证，内容为打印 'Hello, Coding Agent!'。",
            toolCalls: [],
            raw: {}
        };
    }
}

async function main() {
    const agent = new Agent({
        llmProvider: new MockLlmProvider(),
        systemPrompt: "你是一个 NodeJS 编程助手，会按步骤完成任务。",
        memoryPath: "./example/memory.json",
        worldBookPath: "./example/worldBook.json",
        allowCommandExecution: false,
        maxSteps: 10
    });

    const result = await agent.run("帮我创建一个打印问候语的 JS 文件");
    logger.info("最终结果:");
    console.log(result);
}

main().catch(function (err) {
    logger.error(`示例运行失败: ${err.message}`);
    process.exit(1);
});
