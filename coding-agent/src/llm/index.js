const OpenAIProvider = require("./openai_provider");
const GLMProvider = require("./glm_provider");

// 根据配置创建对应的 LLM Provider 实例
function createLLM(config) {
    const provider = (config && config.provider || "openai").toLowerCase();
    switch (provider) {
        case "glm":
        case "zhipu":
            return new GLMProvider(config);
        case "openai":
        default:
            return new OpenAIProvider(config);
    }
}

module.exports = { createLLM, OpenAIProvider, GLMProvider };
