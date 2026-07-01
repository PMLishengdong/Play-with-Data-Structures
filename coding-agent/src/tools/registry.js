// 工具注册表：管理所有可用工具的注册、查找与统一调用
class ToolRegistry {
    constructor() {
        this.tools = {};
    }

    // 注册一个工具
    register(tool) {
        this.tools[tool.name] = tool;
        return this;
    }

    // 按名称获取工具，未找到返回 null
    get(name) {
        return this.tools[name] || null;
    }

    // 获取所有工具列表
    list() {
        return Object.values(this.tools);
    }

    // 获取所有工具的 OpenAI 描述数组
    toOpenAITools() {
        return this.list().map(function (t) {
            return t.toOpenAITool();
        });
    }

    // 执行指定工具，返回结果字符串
    async run(name, args) {
        const tool = this.get(name);
        if (!tool) {
            return `未找到工具: ${name}`;
        }
        return await tool.run(args);
    }
}

module.exports = ToolRegistry;
