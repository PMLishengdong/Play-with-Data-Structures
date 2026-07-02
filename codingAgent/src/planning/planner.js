"use strict";

/**
 * 任务规划器：利用 LLM 将用户任务拆分为可执行的步骤计划。
 */
class Planner {
    /**
     * 构造规划器。
     * @param {LlmClient} llmClient
     */
    constructor(llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 根据用户任务生成执行计划。
     * @param {string} task 用户原始任务描述
     * @param {string[]} toolNames 可用工具名称列表
     * @returns {Promise<Array<{step:number, action:string, tool?:string, expected:string}>>}
     */
    async createPlan(task, toolNames) {
        const messages = [
            {
                role: "system",
                content: "你是一位严谨的任务规划专家。请将用户的编程任务拆分为清晰的执行步骤，只输出 JSON 数组。"
            },
            {
                role: "user",
                content: this._buildPrompt(task, toolNames)
            }
        ];

        const choice = await this.llmClient.chat(messages, { temperature: 0.2 });
        const content = choice.message && choice.message.content ? choice.message.content : "";
        return this._parsePlan(content);
    }

    /**
     * 构造让 LLM 生成计划的提示。
     * @param {string} task
     * @param {string[]} toolNames
     * @returns {string}
     */
    _buildPrompt(task, toolNames) {
        return [
            "任务:",
            task,
            "",
            `可用工具: ${toolNames.join(", ")}`,
            "",
            "要求:",
            "1. 将任务拆分为若干步骤。",
            "2. 每个步骤是 JSON 对象，包含 step（序号）, action（动作描述）, tool（可选，建议使用的工具名）, expected（预期结果）。",
            "3. 只返回 JSON 数组，不要添加 markdown 代码块或其他说明。"
        ].join("\n");
    }

    /**
     * 解析 LLM 返回的 JSON 计划。
     * @param {string} content
     * @returns {Array<Object>}
     */
    _parsePlan(content) {
        const cleaned = this._extractJson(content);
        try {
            const parsed = JSON.parse(cleaned);
            if (Array.isArray(parsed)) {
                return parsed;
            }
            if (parsed && Array.isArray(parsed.steps)) {
                return parsed.steps;
            }
        } catch (error) {
            console.warn("计划 JSON 解析失败，返回原始文本作为单步计划:", error.message);
        }
        return [
            {
                step: 1,
                action: content,
                tool: "",
                expected: "完成用户任务"
            }
        ];
    }

    /**
     * 从 LLM 输出中提取 JSON 字符串，支持去除 markdown 代码块标记。
     * @param {string} content
     * @returns {string}
     */
    _extractJson(content) {
        if (!content) {
            return "[]";
        }
        const trimmed = content.trim();
        if (trimmed.indexOf("```") === 0) {
            const lines = trimmed.split("\n");
            // 去掉首行的 ```json 或 ```
            if (lines.length > 2) {
                lines.shift();
                lines.pop();
                return lines.join("\n").trim();
            }
        }
        return trimmed;
    }
}

module.exports = Planner;
