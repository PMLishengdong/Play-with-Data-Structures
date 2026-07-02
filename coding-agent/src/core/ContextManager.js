"use strict";

/**
 * 上下文管理器。
 * 负责组装发送给 LLM 的消息列表，包括系统提示、世界书、记忆与当前对话，并进行简单的长度控制。
 */

class ContextManager {
    /**
     * 创建上下文管理器。
     * @param {Object} options - 配置项
     * @param {MemorySystem} options.memory - 记忆系统实例
     * @param {WorldBook} options.worldBook - 世界书实例
     * @param {string} options.systemPrompt - 基础系统提示
     * @param {number} options.maxMessages - 最大保留消息数
     */
    constructor(options) {
        options = options || {};
        this.memory = options.memory;
        this.worldBook = options.worldBook;
        this.baseSystemPrompt = options.systemPrompt || "你是一个专业的编程助手 Agent。";
        this.maxMessages = options.maxMessages || 40;
    }

    /**
     * 估算消息的 token 数量（粗略按字符数除以 4 估算）。
     * @param {Object} message - 消息对象
     * @returns {number} 估算 token 数
     */
    _estimateTokens(message) {
        const text = message.content || "";
        return Math.ceil(text.length / 4);
    }

    /**
     * 组装发送给 LLM 的完整消息列表。
     * 顺序为：system -> 世界书 -> 长期记忆 -> 最近的短期记忆 -> 当前用户输入。
     * @param {string} userInput - 当前用户输入
     * @returns {Promise<Array<Object>>} 消息列表
     */
    async buildMessages(userInput) {
        const messages = [];

        // 1. 系统提示
        let systemContent = this.baseSystemPrompt;

        // 2. 追加世界书内容
        if (this.worldBook) {
            const worldBookText = await this.worldBook.toPromptText();
            if (worldBookText) {
                systemContent += "\n\n" + worldBookText;
            }
        }

        // 3. 追加长期记忆
        if (this.memory) {
            const memoryText = await this.memory.getLongTermText();
            if (memoryText) {
                systemContent += "\n\n【长期记忆】\n" + memoryText;
            }
        }

        messages.push({
            role: "system",
            content: systemContent
        });

        // 4. 追加最近的短期记忆
        if (this.memory) {
            const recent = this._trimMessages(this.memory.getRecentShortTerm(this.maxMessages));
            recent.forEach(function (msg) {
                messages.push(msg);
            });
        }

        // 5. 追加当前用户输入
        messages.push({
            role: "user",
            content: userInput
        });

        return messages;
    }

    /**
     * 当消息数量超过限制时，保留最近消息并丢弃早期消息。
     * @param {Array<Object>} messages - 原始消息数组
     * @returns {Array<Object>} 截断后的数组
     */
    _trimMessages(messages) {
        if (messages.length <= this.maxMessages) {
            return messages;
        }
        return messages.slice(-this.maxMessages);
    }

    /**
     * 估算整组消息的总 token 数。
     * @param {Array<Object>} messages - 消息列表
     * @returns {number} 总估算 token 数
     */
    estimateTotalTokens(messages) {
        const self = this;
        let total = 0;
        messages.forEach(function (msg) {
            total += self._estimateTokens(msg);
        });
        return total;
    }
}

module.exports = ContextManager;
