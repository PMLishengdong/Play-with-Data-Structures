"use strict";

const fs = require("fs");
const config = require("../config");

/**
 * 上下文管理器，维护系统提示与多轮对话消息。
 * 当消息总数超过阈值时，会对早期非系统消息进行裁剪，防止超出 LLM 上下文窗口。
 */
class ContextManager {
    /**
     * 构造上下文管理器。
     * @param {Object} [deps] 依赖注入
     * @param {MemoryStore} [deps.memoryStore] 记忆仓库
     * @param {RagEngine} [deps.ragEngine] RAG 引擎
     */
    constructor(deps) {
        this.messages = [];
        this.maxMessages = 40;
        this.systemPrompt = this._loadSystemPrompt();
        this.memoryStore = deps && deps.memoryStore ? deps.memoryStore : null;
        this.ragEngine = deps && deps.ragEngine ? deps.ragEngine : null;
    }

    /**
     * 从配置文件加载系统提示。
     * @returns {string}
     */
    _loadSystemPrompt() {
        try {
            if (fs.existsSync(config.agent.systemPromptFile)) {
                return fs.readFileSync(config.agent.systemPromptFile, "utf-8");
            }
        } catch (error) {
            console.error("加载系统提示失败:", error.message);
        }
        return "你是一个 helpful 的编程助手。";
    }

    /**
     * 构建最终发送给 LLM 的完整消息数组。
     * 系统提示会附加相关记忆与 RAG 检索结果。
     * @returns {Promise<Array<Object>>}
     */
    async buildMessages() {
        const systemContent = await this._buildSystemContent();
        const result = [{ role: "system", content: systemContent }].concat(this.messages);
        return result;
    }

    /**
     * 拼装系统提示内容，注入记忆与 RAG 检索结果。
     * @returns {Promise<string>}
     */
    async _buildSystemContent() {
        const parts = [this.systemPrompt];

        if (this.memoryStore) {
            const shortTerm = this.memoryStore.getRecentShortTerm(5);
            const longTerm = this.memoryStore.getRecentLongTerm(5);
            if (shortTerm.length > 0 || longTerm.length > 0) {
                parts.push("\n\n【相关记忆】");
                shortTerm.forEach(function (item) {
                    parts.push(`- [短期] ${item.content}`);
                });
                longTerm.forEach(function (item) {
                    parts.push(`- [长期] ${item.content}`);
                });
            }
        }

        if (this.ragEngine) {
            const recentUserMessages = this._getRecentUserContents(1);
            if (recentUserMessages.length > 0) {
                const query = recentUserMessages[0];
                const docs = await this.ragEngine.retrieve(query, config.rag.topK);
                if (docs.length > 0) {
                    parts.push("\n\n【相关文档片段】");
                    docs.forEach(function (doc, index) {
                        parts.push(`\n[片段 ${index + 1}]\n${doc.content}`);
                    });
                }
            }
        }

        return parts.join("\n");
    }

    /**
     * 获取最近几条用户消息的内容。
     * @param {number} [limit=1]
     * @returns {string[]}
     */
    _getRecentUserContents(limit) {
        if (!limit) {
            limit = 1;
        }
        const result = [];
        for (let i = this.messages.length - 1; i >= 0 && result.length < limit; i -= 1) {
            if (this.messages[i].role === "user") {
                result.unshift(this.messages[i].content);
            }
        }
        return result;
    }

    /**
     * 添加一条用户消息。
     * @param {string} content
     */
    addUserMessage(content) {
        this.messages.push({ role: "user", content: content });
        this._trimIfNeeded();
    }

    /**
     * 添加一条助手消息。
     * @param {string} content
     */
    addAssistantMessage(content) {
        this.messages.push({ role: "assistant", content: content });
        this._trimIfNeeded();
    }

    /**
     * 添加一条工具调用结果消息。
     * @param {string} toolCallId
     * @param {string} name
     * @param {string} content
     */
    addToolResult(toolCallId, name, content) {
        this.messages.push({
            role: "tool",
            tool_call_id: toolCallId,
            content: content
        });
        this._trimIfNeeded();
    }

    /**
     * 添加一条助手请求工具调用的消息（包含 tool_calls 字段）。
     * @param {Object} assistantMessage
     */
    addAssistantToolCall(assistantMessage) {
        this.messages.push(assistantMessage);
        this._trimIfNeeded();
    }

    /**
     * 当消息数量超过阈值时，裁剪最早的用户/助手消息（保留系统提示与最近对话）。
     */
    _trimIfNeeded() {
        if (this.messages.length <= this.maxMessages) {
            return;
        }
        const removeCount = Math.floor(this.maxMessages / 4);
        this.messages = this.messages.slice(removeCount);
    }

    /**
     * 清空对话历史（保留系统提示）。
     */
    clear() {
        this.messages = [];
    }

    /**
     * 获取当前对话消息数量。
     * @returns {number}
     */
    size() {
        return this.messages.length;
    }
}

module.exports = ContextManager;
