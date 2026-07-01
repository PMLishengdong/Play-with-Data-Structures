// 工具基类：定义工具的统一结构（名称、描述、参数 schema、执行函数）
class BaseTool {
    constructor(definition) {
        this.name = definition.name;
        this.description = definition.description;
        this.parameters = definition.parameters;
        this.handler = definition.handler;
    }

    // 执行工具，捕获异常并返回字符串结果（避免异常冒泡中断 Agent 循环）
    async run(args) {
        try {
            const result = await this.handler(args || {});
            return result;
        } catch (err) {
            const detail = err && err.message ? err.message : String(err);
            return `工具执行出错: ${detail}`;
        }
    }

    // 转换为 OpenAI function-calling 的工具描述格式
    toOpenAITool() {
        return {
            type: "function",
            function: {
                name: this.name,
                description: this.description,
                parameters: this.parameters
            }
        };
    }
}

module.exports = BaseTool;
