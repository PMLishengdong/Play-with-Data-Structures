"use strict";

const axios = require("axios");

/**
 * OpenAI 兼容 LLM 客户端
 * 支持通过环境变量切换 baseUrl、model、apiKey，适配多个兼容接口。
 */
class OpenAIClient {
    /**
     * 构造函数
     * @param {Object} options - 配置项
     * @param {string} options.baseUrl - API 基础地址
     * @param {string} options.apiKey - API 密钥
     * @param {string} options.model - 模型名称
     * @param {number} options.maxTokens - 最大生成 token 数
     * @param {number} options.temperature - 采样温度
     */
    constructor(options) {
        this.baseUrl = options.baseUrl;
        this.apiKey = options.apiKey;
        this.model = options.model;
        this.maxTokens = options.maxTokens;
        this.temperature = options.temperature;
    }

    /**
     * 调用 Chat Completions 接口
     * @param {Array<Object>} messages - 消息列表，格式为 OpenAI messages
     * @param {Array<Object>} [tools] - 可选的工具定义列表
     * @returns {Promise<Object>} - 返回 LLM 响应对象
     */
    async chat(messages, tools) {
        const url = `${this.baseUrl}/chat/completions`;
        const headers = {
            "Content-Type": "application/json"
        };
        if (this.apiKey && this.apiKey.length > 0) {
            headers.Authorization = `Bearer ${this.apiKey}`;
        }

        const body = {
            model: this.model,
            messages: messages,
            max_tokens: this.maxTokens,
            temperature: this.temperature
        };

        // 仅在传入工具时启用工具调用
        if (tools && tools.length > 0) {
            body.tools = tools;
            body.tool_choice = "auto";
        }

        try {
            const response = await axios.post(url, body, {
                headers: headers,
                timeout: 120000
            });
            return response.data;
        } catch (error) {
            const message = error.response
                ? JSON.stringify(error.response.data)
                : error.message;
            throw new Error(`LLM 请求失败: ${message}`);
        }
    }
}

module.exports = OpenAIClient;
