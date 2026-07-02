"use strict";

/**
 * 工具注册表，负责维护所有可用工具、生成 OpenAI function 定义并分发执行。
 */
class ToolRegistry {
    /**
     * 构造空的工具注册表。
     */
    constructor() {
        this.tools = {};
    }

    /**
     * 注册一个工具。
     * @param {Object} tool 工具对象，需包含 name、description、parameters、execute
     */
    register(tool) {
        if (!tool || !tool.name) {
            throw new Error("工具注册失败：缺少 name");
        }
        this.tools[tool.name] = tool;
    }

    /**
     * 批量注册工具。
     * @param {Object[]} tools
     */
    registerMany(tools) {
        const self = this;
        tools.forEach(function (tool) {
            self.register(tool);
        });
    }

    /**
     * 获取指定名称的工具。
     * @param {string} name
     * @returns {Object}
     */
    get(name) {
        return this.tools[name];
    }

    /**
     * 列出所有已注册工具名称。
     * @returns {string[]}
     */
    listNames() {
        return Object.keys(this.tools);
    }

    /**
     * 生成 OpenAI 兼容的 tools 数组，用于 chat.completions 请求。
     * @returns {Array<{type:string, function:Object}>}
     */
    toOpenAiTools() {
        const result = [];
        const names = Object.keys(this.tools);
        for (let i = 0; i < names.length; i += 1) {
            const tool = this.tools[names[i]];
            result.push({
                type: "function",
                function: {
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters
                }
            });
        }
        return result;
    }

    /**
     * 执行单个工具调用。
     * @param {Object} toolCall OpenAI 返回的 tool_call 对象
     * @returns {Promise<{tool_call_id:string, name:string, content:string}>}
     */
    async execute(toolCall) {
        const name = toolCall.function && toolCall.function.name;
        const tool = this.tools[name];
        if (!tool) {
            return {
                tool_call_id: toolCall.id,
                name: name,
                content: `错误：未找到工具 "${name}"`
            };
        }

        let args = {};
        try {
            args = JSON.parse(toolCall.function.arguments || "{}");
        } catch (parseError) {
            return {
                tool_call_id: toolCall.id,
                name: name,
                content: `参数解析失败: ${parseError.message}`
            };
        }

        try {
            const result = await tool.execute(args);
            return {
                tool_call_id: toolCall.id,
                name: name,
                content: typeof result === "string" ? result : JSON.stringify(result, null, 4)
            };
        } catch (error) {
            return {
                tool_call_id: toolCall.id,
                name: name,
                content: `工具执行出错: ${error.message}`
            };
        }
    }
}

module.exports = ToolRegistry;
