"use strict";

/**
 * 工具基类。
 * 所有可注册到 Agent 的工具应继承此类，并实现 name、description、parameters、execute。
 */

class BaseTool {
    /**
     * 工具构造函数。
     * 子类可调用 super() 并覆盖相关属性。
     */
    constructor() {
        if (new.target === BaseTool) {
            throw new Error("BaseTool 是抽象类，不能直接实例化");
        }

        /**
         * 工具名称，作为工具调用的唯一标识。
         * @type {string}
         */
        this.name = "";

        /**
         * 工具功能描述，供 LLM 理解何时使用该工具。
         * @type {string}
         */
        this.description = "";

        /**
         * 工具参数 JSON Schema 描述。
         * @type {Object}
         */
        this.parameters = {
            type: "object",
            properties: {},
            required: []
        };
    }

    /**
     * 执行工具逻辑。
     * @param {Object} args - LLM 传入的参数
     * @param {Object} context - Agent 上下文，包含 memory、worldBook、contextManager 等
     * @returns {Promise<string>} 工具执行结果字符串
     */
    async execute(args, context) {
        throw new Error("子类必须实现 execute 方法");
    }

    /**
     * 生成 OpenAI 兼容的 function/tool 描述对象。
     * @returns {Object} tool 描述
     */
    toOpenAiTool() {
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
