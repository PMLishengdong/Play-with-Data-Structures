"use strict";

/**
 * 工具注册表。
 * 负责统一管理工具的注册、查找、生成 OpenAI 兼容描述以及分发执行。
 */

const logger = require("../utils/logger");

class ToolRegistry {
    constructor() {
        /**
         * 已注册工具映射，key 为工具名，value 为工具实例。
         * @type {Object}
         */
        this.tools = {};
    }

    /**
     * 注册单个工具。
     * @param {BaseTool} tool - 工具实例
     */
    register(tool) {
        if (!tool || !tool.name) {
            throw new Error("注册工具失败：工具实例或名称为空");
        }
        this.tools[tool.name] = tool;
        logger.debug(`注册工具: ${tool.name}`);
    }

    /**
     * 批量注册工具。
     * @param {Array<BaseTool>} tools - 工具实例数组
     */
    registerMany(tools) {
        const self = this;
        tools.forEach(function (tool) {
            self.register(tool);
        });
    }

    /**
     * 根据名称获取工具实例。
     * @param {string} name - 工具名
     * @returns {BaseTool|undefined} 工具实例
     */
    get(name) {
        return this.tools[name];
    }

    /**
     * 判断指定名称的工具是否已注册。
     * @param {string} name - 工具名
     * @returns {boolean} 是否已注册
     */
    has(name) {
        return !!this.tools[name];
    }

    /**
     * 获取所有已注册工具的 OpenAI 兼容描述列表。
     * @returns {Array<Object>} tools 描述数组
     */
    toOpenAiTools() {
        const result = [];
        Object.keys(this.tools).forEach(function (name) {
            result.push(this.tools[name].toOpenAiTool());
        }, this);
        return result;
    }

    /**
     * 执行指定工具。
     * @param {string} name - 工具名
     * @param {Object} args - 工具参数
     * @param {Object} context - Agent 上下文
     * @returns {Promise<string>} 工具执行结果
     */
    async execute(name, args, context) {
        const tool = this.get(name);
        if (!tool) {
            const message = `工具未找到: ${name}`;
            logger.warn(message);
            return message;
        }

        logger.info(`执行工具: ${name}, args=${JSON.stringify(args)}`);

        try {
            const result = await tool.execute(args, context);
            return result;
        } catch (err) {
            logger.error(`工具执行异常 [${name}]: ${err.message}`);
            return `工具执行异常: ${err.message}`;
        }
    }
}

module.exports = ToolRegistry;
