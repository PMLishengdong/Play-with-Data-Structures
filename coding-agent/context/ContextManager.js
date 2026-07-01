"use strict";

/**
 * 上下文管理器
 * 负责维护对话消息、系统提示词、工具结果，并控制短期记忆的截断。
 */
class ContextManager {
    /**
     * 构造函数
     * @param {Object} options - 配置项
     * @param {string} options.systemPrompt - 系统提示词
     * @param {number} options.maxMessages - 短期记忆最大消息数
     */
    constructor(options) {
        this.systemPrompt = options.systemPrompt || "你是一个 helpful 的编程助手。";
        this.maxMessages = options.maxMessages || 30;
        this.messages = [];
    }

    /**
     * 获取当前完整的消息列表（包含系统提示词）
     * @returns {Array<Object>} - 消息列表
     */
    getMessages() {
        const result = [{ role: "system", content: this.systemPrompt }];
        result.push.apply(result, this.messages);
        return result;
    }

    /**
     * 添加一条用户消息
     * @param {string} content - 消息内容
     */
    addUserMessage(content) {
        this.messages.push({ role: "user", content: content });
        this.trim();
    }

    /**
     * 添加一条助手消息
     * @param {string} content - 消息内容
     */
    addAssistantMessage(content) {
        this.messages.push({ role: "assistant", content: content });
        this.trim();
    }

    /**
     * 添加一条工具调用结果消息
     * @param {string} toolCallId - 工具调用 ID
     * @param {string} content - 工具返回结果
     */
    addToolResult(toolCallId, content) {
        this.messages.push({
            role: "tool",
            tool_call_id: toolCallId,
            content: content
        });
        this.trim();
    }

    /**
     * 添加一条助手发起的工具调用消息
     * @param {Object} message - 包含 tool_calls 的 assistant 消息
     */
    addToolCallMessage(message) {
        this.messages.push(message);
        this.trim();
    }

    /**
     * 截断消息列表，保留最近的 maxMessages 条，防止超出上下文窗口
     */
    trim() {
        if (this.messages.length > this.maxMessages) {
            const keepCount = Math.floor(this.maxMessages / 2);
            this.messages = this.messages.slice(this.messages.length - keepCount);
        }
    }

    /**
     * 清空历史消息（保留系统提示词）
     */
    clear() {
        this.messages = [];
    }
}

module.exports = ContextManager;
