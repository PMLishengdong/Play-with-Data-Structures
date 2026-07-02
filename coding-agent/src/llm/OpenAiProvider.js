"use strict";

/**
 * OpenAI 兼容的 LLM Provider 实现。
 * 使用 axios 发送 chat/completions 请求，并解析工具调用。
 */

const axios = require("axios");
const LlmProvider = require("./LlmProvider");
const logger = require("../utils/logger");

class OpenAiProvider extends LlmProvider {
    /**
     * 创建 OpenAI 兼容 Provider。
     * @param {Object} config - 配置对象，包含 baseUrl、apiKey、model 等
     */
    constructor(config) {
        super(config);
        this.baseUrl = (config.baseUrl || "https://api.openai.com/v1").replace(/\/$/, "");
        this.apiKey = config.apiKey || "";
        this.model = config.model || "gpt-4o-mini";
        this.temperature = config.temperature !== undefined ? config.temperature : 0.7;
        this.maxTokens = config.maxTokens || 4096;
        this.timeout = config.timeout || 60000;
    }

    /**
     * 构造请求头。
     * @returns {Object} axios 请求头
     */
    _buildHeaders() {
        const headers = {
            "Content-Type": "application/json"
        };
        if (this.apiKey) {
            headers.Authorization = `Bearer ${this.apiKey}`;
        }
        return headers;
    }

    /**
     * 构造请求体。
     * @param {Array<Object>} messages - 消息列表
     * @param {Array<Object>} tools - 工具描述列表
     * @returns {Object} 请求体
     */
    _buildPayload(messages, tools) {
        const payload = {
            model: this.model,
            messages: messages,
            temperature: this.temperature,
            max_tokens: this.maxTokens
        };

        // 仅在提供了工具描述时传入 tools 参数
        if (tools && tools.length > 0) {
            payload.tools = tools;
            payload.tool_choice = "auto";
        }

        return payload;
    }

    /**
     * 发送聊天请求并解析响应。
     * 返回统一结构：{ content, toolCalls, raw }
     * @param {Array<Object>} messages - 消息列表
     * @param {Array<Object>} tools - 工具描述列表
     * @returns {Promise<Object>} 标准化响应
     */
    async chat(messages, tools) {
        const url = `${this.baseUrl}/chat/completions`;
        const payload = this._buildPayload(messages, tools);

        logger.debug(`请求 LLM: ${url}, model=${this.model}, messages=${messages.length}`);

        try {
            const response = await axios.post(url, payload, {
                headers: this._buildHeaders(),
                timeout: this.timeout
            });

            const raw = response.data;
            const choice = raw.choices && raw.choices[0];
            if (!choice) {
                throw new Error("LLM 响应缺少 choices 字段");
            }

            const message = choice.message;
            const content = message.content || "";
            const toolCalls = message.tool_calls || [];

            logger.debug(`LLM 响应: content.length=${content.length}, toolCalls=${toolCalls.length}`);

            return {
                content: content,
                toolCalls: toolCalls,
                raw: raw
            };
        } catch (err) {
            logger.error(`LLM 请求失败: ${err.message}`);
            if (err.response) {
                const status = err.response.status;
                const data = err.response.data;
                throw new Error(`LLM 请求失败 [${status}]: ${JSON.stringify(data)}`);
            }
            throw err;
        }
    }
}

module.exports = OpenAiProvider;
