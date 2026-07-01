const axios = require("axios");
const BaseLLMProvider = require("./base_provider");

// OpenAI 兼容的 LLM Provider
// 可适配 OpenAI 官方以及 DeepSeek、Moonshot、本地 vLLM 等兼容 /v1/chat/completions 的服务
class OpenAIProvider extends BaseLLMProvider {
    constructor(config) {
        super(config);
        // 复用一个 axios 实例，统一 baseURL 与鉴权头
        this.client = axios.create({
            baseURL: config.baseUrl,
            timeout: config.timeout || 60000,
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${config.apiKey}`
            }
        });
    }

    // 调用 chat/completions，解析出文本内容与工具调用
    async chat(messages, options) {
        const body = {
            model: this.config.model,
            messages: messages,
            temperature: this.config.temperature,
            max_tokens: this.config.maxTokens
        };
        // 仅在提供了工具时透传 tools 字段
        if (options && options.tools && options.tools.length) {
            body.tools = options.tools;
            body.tool_choice = options.toolChoice || "auto";
        }
        try {
            const resp = await this.client.post("/chat/completions", body);
            return this.parseResponse(resp.data);
        } catch (err) {
            throw this.wrapError("OpenAI 调用失败", err);
        }
    }

    // 解析 OpenAI 标准响应结构，归一化为 { content, toolCalls }
    parseResponse(data) {
        const choice = data && data.choices && data.choices[0];
        const msg = choice && choice.message ? choice.message : {};
        const toolCalls = [];
        if (msg.tool_calls && msg.tool_calls.length) {
            for (let i = 0; i < msg.tool_calls.length; i++) {
                const tc = msg.tool_calls[i];
                // 工具参数是 JSON 字符串，需解析为对象
                let args = {};
                try {
                    args = JSON.parse(tc.function.arguments || "{}");
                } catch (e) {
                    args = {};
                }
                toolCalls.push({
                    id: tc.id,
                    name: tc.function.name,
                    arguments: args
                });
            }
        }
        return {
            content: msg.content || "",
            toolCalls: toolCalls,
            raw: data
        };
    }
}

module.exports = OpenAIProvider;
