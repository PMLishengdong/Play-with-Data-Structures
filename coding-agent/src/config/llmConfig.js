"use strict";

/**
 * LLM 相关配置默认值与环境读取。
 * 支持 OpenAI 兼容接口，可通过环境变量覆盖。
 */

const config = {
    // API 基础地址，默认 OpenAI 官方地址
    baseUrl: process.env.LLM_BASE_URL || "https://api.openai.com/v1",

    // API 密钥
    apiKey: process.env.LLM_API_KEY || "",

    // 默认使用的模型名称
    model: process.env.LLM_MODEL || "gpt-4o-mini",

    // 默认温度参数
    temperature: parseFloat(process.env.LLM_TEMPERATURE || "0.7"),

    // 默认最大 tokens
    maxTokens: parseInt(process.env.LLM_MAX_TOKENS || "4096", 10),

    // 请求超时时间（毫秒）
    timeout: parseInt(process.env.LLM_TIMEOUT || "60000", 10),

    // 是否启用流式输出（本项目默认非流式，便于工具调用解析）
    stream: false
};

/**
 * 读取并返回当前 LLM 配置。
 * 返回深拷贝，避免外部修改影响全局默认值。
 * @returns {Object} 配置对象
 */
function getConfig() {
    return JSON.parse(JSON.stringify(config));
}

/**
 * 更新 LLM 配置。
 * @param {Object} partial - 需要覆盖的配置项
 */
function updateConfig(partial) {
    Object.keys(partial).forEach(function (key) {
        if (config[key] !== undefined) {
            config[key] = partial[key];
        }
    });
}

module.exports = {
    getConfig,
    updateConfig
};
