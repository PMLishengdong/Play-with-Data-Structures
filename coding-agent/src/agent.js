const ContextManager = require("./context");
const Memory = require("./memory");
const Planner = require("./planner");
const { createDefaultRegistry } = require("./tools");

// 写代码的 Agent 核心类
// 整合 任务规划、工具调用、记忆系统、上下文管理 与 LLM，
// 以 ReAct（思考-行动-观察）循环执行直到任务完成或达到最大迭代次数
class CodingAgent {
    constructor(options) {
        options = options || {};
        this.llm = options.llm;
        if (!this.llm) {
            throw new Error("必须提供 llm provider");
        }
        this.config = options.config || {};
        // 上下文管理器：维护发送给 LLM 的消息序列
        this.context = new ContextManager(this.config.context || {});
        // 记忆系统：短期 + 长期记忆
        this.memory = new Memory(this.config.memory || {});
        // 任务规划器：拆解目标为步骤
        this.planner = new Planner(this.llm);
        // 工具注册表：默认注册内置工具，可外部传入自定义注册表
        this.registry = options.registry || createDefaultRegistry({
            inputFn: options.inputFn
        });
        // 任务步骤与当前步骤索引（需在构建系统提示前初始化）
        this.steps = [];
        this.currentStep = 0;
        // 日志回调，便于外部观察执行过程
        this.onLog = options.onLog || function () {};
        // 初始化系统提示
        this.context.setSystemPrompt(this.buildSystemPrompt(""));
    }

    // 构建系统提示词：注入目标、长期记忆、近期观察、任务计划
    buildSystemPrompt(goal) {
        const parts = [];
        parts.push("你是一个专业的写代码的 Agent。你可以通过调用工具来读取/写入文件、列出目录、向用户提问。");
        parts.push("请一步步思考，必要时调用工具获取信息，当任务完成时给出最终结论或代码。");
        if (goal) {
            parts.push(`\n当前目标: ${goal}`);
        }
        const longTerm = this.memory.getLongTerm();
        if (longTerm.length) {
            parts.push("\n长期记忆:");
            longTerm.forEach(function (f) {
                parts.push(`- ${f}`);
            });
        }
        const shortTerm = this.memory.getShortTerm();
        if (shortTerm.length) {
            parts.push("\n近期观察:");
            shortTerm.forEach(function (s) {
                parts.push(`- ${s}`);
            });
        }
        if (this.steps.length) {
            parts.push("\n任务计划:");
            this.steps.forEach(function (s, i) {
                parts.push(`${i + 1}. ${s}`);
            });
        }
        return parts.join("\n");
    }

    // 输出日志
    log(msg) {
        this.onLog(msg);
    }

    // 运行 Agent 完成一个目标，返回执行结果
    async run(goal) {
        this.log(`开始任务: ${goal}`);
        // 1. 任务规划：将目标拆解为步骤（失败则直接执行，不阻断流程）
        try {
            this.steps = await this.planner.plan(goal);
            this.log(`规划完成，共 ${this.steps.length} 步`);
        } catch (e) {
            this.log(`规划失败，直接执行: ${e.message}`);
            this.steps = [];
        }
        // 2. 初始化上下文：清空旧消息，注入带目标与记忆的系统提示
        this.context.clear();
        this.context.setSystemPrompt(this.buildSystemPrompt(goal));
        this.context.addMessage("user", goal);

        const maxIter = (this.config.agent && this.config.agent.maxIterations) || 20;
        // 3. ReAct 循环：思考 -> 行动 -> 观察
        for (let iter = 0; iter < maxIter; iter++) {
            const messages = this.context.build();
            const tools = this.registry.toOpenAITools();
            const resp = await this.llm.chat(messages, { tools: tools });

            // 把助手回复（含可能的工具调用）写入上下文
            this.context.addMessage("assistant", resp.content, resp.toolCalls);

            // 无工具调用即视为最终回答
            if (!resp.toolCalls || !resp.toolCalls.length) {
                this.log("任务完成");
                this.memory.addShortTerm(`完成任务: ${goal}`);
                return {
                    success: true,
                    answer: resp.content,
                    steps: this.steps,
                    iterations: iter + 1
                };
            }

            // 依次执行每个工具调用，并把结果回填到上下文与记忆
            for (let i = 0; i < resp.toolCalls.length; i++) {
                const call = resp.toolCalls[i];
                const argStr = JSON.stringify(call.arguments);
                this.log(`调用工具: ${call.name} 参数: ${argStr}`);
                const result = await this.registry.run(call.name, call.arguments);
                const preview = typeof result === "string" ? result.slice(0, 200) : String(result);
                this.log(`工具结果: ${preview}`);
                // 工具结果作为 tool 消息回填，供 LLM 下一轮观察
                this.context.addToolResult(call.id, result);
                // 重要观察写入短期记忆，参与后续上下文构建
                const memo = typeof result === "string" ? result.slice(0, 300) : String(result);
                this.memory.addShortTerm(`[${call.name}] ${memo}`);
            }
        }
        // 达到最大迭代仍未完成
        this.log("达到最大迭代次数，任务终止");
        return {
            success: false,
            answer: "达到最大迭代次数",
            steps: this.steps,
            iterations: maxIter
        };
    }
}

module.exports = CodingAgent;
