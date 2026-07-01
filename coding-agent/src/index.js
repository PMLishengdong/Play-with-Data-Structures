const { defaultConfig, loadConfig } = require("../config");
const { createLLM } = require("./llm");
const CodingAgent = require("./agent");
const Memory = require("./memory");
const ContextManager = require("./context");
const Planner = require("./planner");
const { ToolRegistry, createDefaultRegistry } = require("./tools");

// 快速创建一个配置完整的 Agent 实例
function createAgent(options) {
    options = options || {};
    const config = loadConfig(options.config);
    const llm = options.llm || createLLM(config.llm);
    return new CodingAgent({
        llm: llm,
        config: config,
        inputFn: options.inputFn,
        registry: options.registry,
        onLog: options.onLog
    });
}

module.exports = {
    createAgent,
    CodingAgent,
    createLLM,
    Memory,
    ContextManager,
    Planner,
    ToolRegistry,
    createDefaultRegistry,
    defaultConfig,
    loadConfig
};
