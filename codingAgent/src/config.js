"use strict";

const path = require("path");

// 从环境变量读取配置，未设置时使用默认值
const config = {
    llm: {
        provider: process.env.LLM_PROVIDER || "openai",
        apiKey: process.env.LLM_API_KEY || "",
        baseUrl: process.env.LLM_BASE_URL || "https://api.openai.com/v1",
        model: process.env.LLM_MODEL || "gpt-4o-mini",
        embeddingModel: process.env.LLM_EMBEDDING_MODEL || "text-embedding-3-small",
        maxRetries: 3,
        timeoutMs: 60000
    },
    agent: {
        maxToolIterations: 30,
        systemPromptFile: path.join(__dirname, "prompts", "system.txt")
    },
    memory: {
        filePath: path.join(__dirname, "..", "data", "memory.json")
    },
    rag: {
        chunkSize: 800,
        chunkOverlap: 100,
        topK: 3
    }
};

module.exports = config;
