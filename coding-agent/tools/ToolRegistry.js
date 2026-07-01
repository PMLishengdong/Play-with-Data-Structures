"use strict";

/**
 * 工具注册表
 * 统一管理 Agent 可调用的工具，包括注册、获取、执行以及生成 OpenAI 格式的工具定义。
 */
class ToolRegistry {
    /**
     * 构造函数
     */
    constructor() {
        // 工具名到工具实例的映射
        this.tools = {};
    }

    /**
     * 注册一个工具
     * @param {Object} tool - 工具实例
     * @param {string} tool.name - 工具名称
     * @param {Function} tool.execute - 执行函数
     * @param {Object} tool.definition - OpenAI 格式的工具定义
     */
    register(tool) {
        if (!tool || !tool.name) {
            throw new Error("工具必须包含 name 字段");
        }
        this.tools[tool.name] = tool;
    }

    /**
     * 获取指定工具
     * @param {string} name - 工具名称
     * @returns {Object|null} - 工具实例
     */
    get(name) {
        const tool = this.tools[name];
        return tool || null;
    }

    /**
     * 获取所有工具的 OpenAI 格式定义
     * @returns {Array<Object>} - 工具定义数组
     */
    getDefinitions() {
        const definitions = [];
        const names = Object.keys(this.tools);
        for (let i = 0; i < names.length; i++) {
            const tool = this.tools[names[i]];
            definitions.push(tool.definition);
        }
        return definitions;
    }

    /**
     * 执行工具调用
     * @param {string} name - 工具名称
     * @param {Object} args - 工具参数
     * @returns {Promise<any>} - 工具执行结果
     */
    async execute(name, args) {
        const tool = this.get(name);
        if (!tool) {
            throw new Error(`未知工具: ${name}`);
        }
        return await tool.execute(args);
    }
}

module.exports = ToolRegistry;
