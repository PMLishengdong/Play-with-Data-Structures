"use strict";

/**
 * LLM Provider 抽象类。
 * 所有 LLM 提供商实现需继承此类并提供 chat 方法。
 */

class LlmProvider {
    /**
     * 创建 Provider 实例。
     * @param {Object} config - 提供商配置
     */
    constructor(config) {
        if (new.target === LlmProvider) {
            throw new Error("LlmProvider 是抽象类，不能直接实例化");
        }
        this.config = config || {};
    }

    /**
     * 发送聊天请求。
     * @param {Array<Object>} messages - 消息列表
     * @param {Array<Object>} tools - 工具描述列表
     * @returns {Promise<Object>} 模型响应对象
     */
    async chat(messages, tools) {
        throw new Error("子类必须实现 chat 方法");
    }
}

module.exports = LlmProvider;
