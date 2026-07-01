// 配置管理模块：统一管理 Agent 运行所需的各项配置，支持环境变量与用户覆盖
const path = require("path");

// 默认配置：优先读取环境变量，便于在不同环境快速切换
const defaultConfig = {
    llm: {
        // 提供商类型：openai（兼容接口）| glm（智谱原生）
        provider: process.env.LLM_PROVIDER || "openai",
        // 模型名称
        model: process.env.LLM_MODEL || "gpt-3.5-turbo",
        // API Key
        apiKey: process.env.LLM_API_KEY || "",
        // 接口基础地址（OpenAI 兼容服务可改写此值）
        baseUrl: process.env.LLM_BASE_URL || "https://api.openai.com/v1",
        // 采样温度
        temperature: 0.2,
        // 单次最大生成 token
        maxTokens: 2048,
        // 请求超时（毫秒）
        timeout: 60000
    },
    memory: {
        // 长期记忆持久化文件路径
        longTermFile: path.join(process.cwd(), ".agent_memory.json"),
        // 短期记忆最大条数
        shortTermLimit: 50
    },
    context: {
        // 上下文窗口 token 估算上限
        maxTokens: 8000,
        // 默认系统提示词
        systemPrompt: "你是一个专业的写代码的 Agent。"
    },
    agent: {
        // 单次任务最大循环次数，防止死循环
        maxIterations: 20
    }
};

// 加载配置：将用户传入的覆盖项深度合并到默认配置上
function loadConfig(overrides) {
    if (!overrides) {
        return defaultConfig;
    }
    return deepMerge(defaultConfig, overrides);
}

// 简单的深度合并工具（不处理数组合并，数组直接替换）
function deepMerge(target, source) {
    const out = Object.assign({}, target);
    const keys = Object.keys(source || {});
    for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const val = source[key];
        if (val && typeof val === "object" && !Array.isArray(val)) {
            out[key] = deepMerge(target[key] || {}, val);
        } else {
            out[key] = val;
        }
    }
    return out;
}

module.exports = { defaultConfig, loadConfig };
