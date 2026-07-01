"use strict";

/**
 * 记忆存储
 * 提供键值对形式的长期记忆读写，并支持短期会话摘要。
 */
class MemoryStore {
    /**
     * 构造函数
     */
    constructor() {
        // 长期记忆，以 key-value 形式保存
        this.longTerm = {};
        // 会话摘要列表
        this.summaries = [];
    }

    /**
     * 写入长期记忆
     * @param {string} key - 记忆键
     * @param {string} value - 记忆值
     */
    set(key, value) {
        this.longTerm[key] = value;
    }

    /**
     * 读取长期记忆
     * @param {string} key - 记忆键
     * @returns {string|null} - 记忆值，不存在则返回 null
     */
    get(key) {
        const value = this.longTerm[key];
        return value !== undefined ? value : null;
    }

    /**
     * 获取所有长期记忆
     * @returns {Object} - 记忆对象
     */
    getAll() {
        return Object.assign({}, this.longTerm);
    }

    /**
     * 删除某条长期记忆
     * @param {string} key - 记忆键
     */
    delete(key) {
        delete this.longTerm[key];
    }

    /**
     * 添加会话摘要
     * @param {string} summary - 摘要内容
     */
    addSummary(summary) {
        this.summaries.push(summary);
    }

    /**
     * 获取所有会话摘要
     * @returns {Array<string>} - 摘要列表
     */
    getSummaries() {
        return this.summaries.slice();
    }

    /**
     * 将记忆内容格式化为文本，供系统提示词使用
     * @returns {string} - 格式化后的记忆文本
     */
    formatMemory() {
        const parts = [];
        const keys = Object.keys(this.longTerm);
        if (keys.length > 0) {
            parts.push("【长期记忆】");
            for (let i = 0; i < keys.length; i++) {
                const key = keys[i];
                parts.push(`- ${key}: ${this.longTerm[key]}`);
            }
        }
        if (this.summaries.length > 0) {
            parts.push("【会话摘要】");
            for (let j = 0; j < this.summaries.length; j++) {
                parts.push(`- ${this.summaries[j]}`);
            }
        }
        return parts.join("\n");
    }
}

module.exports = MemoryStore;
