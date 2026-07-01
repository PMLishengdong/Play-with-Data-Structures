"use strict";

var path = require("path");
var fs = require("fs");

var DEFAULTS = {
    llm: {
        apiKey: process.env["LLM_API_KEY"] || "",
        baseURL: process.env["LLM_BASE_URL"] || "https://api.openai.com/v1",
        model: process.env["LLM_MODEL"] || "gpt-4",
        maxTokens: 4096,
        temperature: 0.3,
        timeout: 60000
    },
    agent: {
        maxIterations: 25,
        maxToolRetries: 3,
        workingDirectory: process.cwd()
    },
    memory: {
        maxHistoryLength: 100,
        storagePath: path.join(process.cwd(), ".agent-memory")
    }
};

function loadConfig(filePath) {
    var configPath = filePath || path.join(process.cwd(), "agent.config.json");
    var config = {};

    // Deep-clone defaults
    config = JSON.parse(JSON.stringify(DEFAULTS));

    if (fs.existsSync(configPath)) {
        try {
            var raw = fs.readFileSync(configPath, "utf-8");
            var userConfig = JSON.parse(raw);
            mergeConfig(config, userConfig);
        } catch (err) {
            console.error("Failed to load config file:", err.message);
        }
    }

    // Environment variables override file config
    if (process.env["LLM_API_KEY"]) {
        config.llm.apiKey = process.env["LLM_API_KEY"];
    }
    if (process.env["LLM_BASE_URL"]) {
        config.llm.baseURL = process.env["LLM_BASE_URL"];
    }
    if (process.env["LLM_MODEL"]) {
        config.llm.model = process.env["LLM_MODEL"];
    }
    if (process.env["AGENT_WORK_DIR"]) {
        config.agent.workingDirectory = process.env["AGENT_WORK_DIR"];
    }

    return config;
}

function mergeConfig(target, source) {
    var keys = Object.keys(source);
    for (var i = 0; i < keys.length; i++) {
        var key = keys[i];
        if (source[key] !== null && typeof source[key] === "object" && !Array.isArray(source[key])) {
            if (!target[key]) {
                target[key] = {};
            }
            mergeConfig(target[key], source[key]);
        } else {
            target[key] = source[key];
        }
    }
}

module.exports = {
    DEFAULTS: DEFAULTS,
    loadConfig: loadConfig
};