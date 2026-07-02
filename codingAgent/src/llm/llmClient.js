"use strict";

const axios = require("axios");
const config = require("../config");

/**
 * 通用的 LLM 客户端，默认兼容 OpenAI API 格式。
 * 支持多轮对话 chat 与 embedding 两种核心能力。
 */
class LlmClient {
    /**
     * 构造 LLM 客户端，使用 config.llm 中的配置。
     */
    constructor() {
        this.provider = config.llm.provider;
        this.apiKey = config.llm.apiKey;
        this.baseUrl = config.llm.baseUrl.replace(/\/$/, "");
        this.model = config.llm.model;
        this.embeddingModel = config.llm.embeddingModel;
        this.maxRetries = config.llm.maxRetries;
        this.timeoutMs = config.llm.timeoutMs;
    }

    /**
     * 发起聊天补全请求。
     * @param {Array<{role:string, content:string, name?:string}>} messages 消息列表
     * @param {Object} [options={}] 额外参数，如 tools、tool_choice、temperature
     * @returns {Promise<Object>} LLM 返回的完整 choice 对象
     */
    async chat(messages, options) {
        if (!options) {
            options = {};
        }

        const url = `${this.baseUrl}/chat/completions`;
        const payload = {
            model: this.model,
            messages: messages
        };

        if (options.tools) {
            payload.tools = options.tools;
        }
        if (options.tool_choice) {
            payload.tool_choice = options.tool_choice;
        }
        if (typeof options.temperature === "number") {
            payload.temperature = options.temperature;
        }

        let lastError = null;
        for (let attempt = 0; attempt < this.maxRetries; attempt += 1) {
            try {
                const response = await axios.post(url, payload, {
                    headers: this._buildHeaders(),
                    timeout: this.timeoutMs
                });
                const choices = response.data && response.data.choices;
                if (!choices || choices.length === 0) {
                    throw new Error("LLM 返回 choices 为空");
                }
                return choices[0];
            } catch (error) {
                lastError = error;
                if (attempt < this.maxRetries - 1) {
                    const delayMs = 1000 * Math.pow(2, attempt);
                    await this._sleep(delayMs);
                }
            }
        }

        throw new Error(`LLM 请求失败（已重试 ${this.maxRetries} 次）: ${this._formatError(lastError)}`);
    }

    /**
     * 获取文本的 embedding 向量，用于 RAG 检索。
     * @param {string|string[]} input 单条或多条文本
     * @returns {Promise<number[][]>} 向量列表
     */
    async embed(input) {
        if (typeof input === "string") {
            input = [input];
        }

        const url = `${this.baseUrl}/embeddings`;
        const payload = {
            model: this.embeddingModel,
            input: input
        };

        let lastError = null;
        for (let attempt = 0; attempt < this.maxRetries; attempt += 1) {
            try {
                const response = await axios.post(url, payload, {
                    headers: this._buildHeaders(),
                    timeout: this.timeoutMs
                });
                const data = response.data && response.data.data;
                if (!data || data.length === 0) {
                    throw new Error("Embedding 返回数据为空");
                }
                return data.map(function (item) {
                    return item.embedding;
                });
            } catch (error) {
                lastError = error;
                if (attempt < this.maxRetries - 1) {
                    const delayMs = 1000 * Math.pow(2, attempt);
                    await this._sleep(delayMs);
                }
            }
        }

        throw new Error(`Embedding 请求失败（已重试 ${this.maxRetries} 次）: ${this._formatError(lastError)}`);
    }

    /**
     * 构造 HTTP 请求头，注入认证信息。
     * @returns {Object} headers
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
     * 将错误对象格式化为可读的字符串。
     * @param {Error} error
     * @returns {string}
     */
    _formatError(error) {
        if (!error) {
            return "未知错误";
        }
        if (error.response && error.response.data) {
            return `${error.message}; 响应: ${JSON.stringify(error.response.data)}`;
        }
        return error.message;
    }

    /**
     * 等待指定毫秒。
     * @param {number} ms
     * @returns {Promise<void>}
     */
    _sleep(ms) {
        return new Promise(function (resolve) {
            setTimeout(resolve, ms);
        });
    }
}

module.exports = LlmClient;
