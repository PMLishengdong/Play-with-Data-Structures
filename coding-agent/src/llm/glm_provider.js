const axios = require("axios");
const crypto = require("crypto");
const BaseLLMProvider = require("./base_provider");

// 智谱 GLM 原生 Provider
// 与 OpenAI 兼容接口的区别主要在鉴权：GLM 使用基于 API Key 的 JWT 鉴权
class GLMProvider extends BaseLLMProvider {
    constructor(config) {
        super(config);
        // GLM 的 apiKey 形如 "{id}.{secret}"，需拆分用于签名
        const parts = (config.apiKey || "").split(".");
        this.apiId = parts[0];
        this.apiSecret = parts[1] || "";
        this.client = axios.create({
            baseURL: config.baseUrl || "https://open.bigmodel.cn/api/paas/v4",
            timeout: config.timeout || 60000,
            headers: { "Content-Type": "application/json" }
        });
    }

    // 生成 GLM 要求的 JWT token（HS256 签名）
    generateToken() {
        const header = { alg: "HS256", sign_type: "SIGN" };
        const payload = {
            api_key: this.apiId,
            exp: Math.floor(Date.now() / 1000) + 3600,
            timestamp: Math.floor(Date.now() / 1000)
        };
        // base64url 编码（去除填充、替换 URL 不安全字符）
        const base64url = function (obj) {
            return Buffer.from(JSON.stringify(obj))
                .toString("base64")
                .replace(/=/g, "")
                .replace(/\+/g, "-")
                .replace(/\//g, "_");
        };
        const signingInput = `${base64url(header)}.${base64url(payload)}`;
        const sig = crypto.createHmac("sha256", this.apiSecret)
            .update(signingInput)
            .digest("base64")
            .replace(/=/g, "")
            .replace(/\+/g, "-")
            .replace(/\//g, "_");
        return `${signingInput}.${sig}`;
    }

    // 调用 GLM chat/completions
    async chat(messages, options) {
        const body = {
            model: this.config.model || "glm-4",
            messages: messages,
            temperature: this.config.temperature,
            max_tokens: this.config.maxTokens
        };
        if (options && options.tools && options.tools.length) {
            body.tools = options.tools;
            body.tool_choice = options.toolChoice || "auto";
        }
        try {
            const resp = await this.client.post("/chat/completions", body, {
                headers: { Authorization: `Bearer ${this.generateToken()}` }
            });
            return this.parseResponse(resp.data);
        } catch (err) {
            throw this.wrapError("GLM 调用失败", err);
        }
    }

    // GLM 响应结构与 OpenAI 兼容，复用解析逻辑
    parseResponse(data) {
        const choice = data && data.choices && data.choices[0];
        const msg = choice && choice.message ? choice.message : {};
        const toolCalls = [];
        if (msg.tool_calls && msg.tool_calls.length) {
            for (let i = 0; i < msg.tool_calls.length; i++) {
                const tc = msg.tool_calls[i];
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

module.exports = GLMProvider;
