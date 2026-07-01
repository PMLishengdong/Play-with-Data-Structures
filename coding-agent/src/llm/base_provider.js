// LLM 提供商基类：定义所有 provider 必须实现的统一接口
class BaseLLMProvider {
    constructor(config) {
        this.config = config || {};
    }

    // 发起一次对话补全，返回 { content, toolCalls, raw }
    async chat(messages, options) {
        throw new Error("chat() 必须由子类实现");
    }

    // 统一错误包装：把底层错误转成带上下文信息的 Error
    wrapError(message, err) {
        const detail = err && err.message ? err.message : String(err);
        const e = new Error(`${message}: ${detail}`);
        e.cause = err;
        return e;
    }
}

module.exports = BaseLLMProvider;
