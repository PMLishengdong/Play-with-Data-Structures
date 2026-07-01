"use strict";

/**
 * 全局配置模块
 * 负责从环境变量中读取 LLM 与 Agent 行为相关配置，并提供默认值。
 */

const config = {
    // LLM 相关配置
    llm: {
        // 默认使用 OpenAI 兼容接口
        provider: process.env.LLM_PROVIDER || "openai",
        // API 基础地址，支持自定义（如本地 vLLM、Ollama 等）
        baseUrl: process.env.LLM_BASE_URL || "https://api.openai.com/v1",
        // API 密钥
        apiKey: process.env.LLM_API_KEY || "",
        // 默认模型名称
        model: process.env.LLM_MODEL || "gpt-4o-mini",
        // 最大 token 数
        maxTokens: parseInt(process.env.LLM_MAX_TOKENS || "2048", 10),
        // 采样温度
        temperature: parseFloat(process.env.LLM_TEMPERATURE || "0.7")
    },

    // Agent 行为配置
    agent: {
        // 单轮最大工具调用次数，防止死循环
        maxToolCallsPerTurn: parseInt(process.env.AGENT_MAX_TOOL_CALLS || "10", 10),
        // 最大对话轮数
        maxRounds: parseInt(process.env.AGENT_MAX_ROUNDS || "20", 10),
        // 默认工作目录
        workDir: process.env.AGENT_WORK_DIR || process.cwd()
    },

    // 记忆系统配置
    memory: {
        // 短期记忆保留的最大消息数
        shortTermMaxMessages: parseInt(process.env.MEMORY_SHORT_TERM_MAX || "30", 10),
        // 是否启用长期记忆摘要
        enableLongTerm: process.env.MEMORY_ENABLE_LONG_TERM === "true"
    }
};

module.exports = config;
