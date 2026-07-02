"use strict";

const fs = require("fs");
const path = require("path");
const config = require("../config");

/**
 * 记忆系统，以 JSON 文件持久化短期记忆（会话内）与长期记忆（跨会话）。
 */
class MemoryStore {
    /**
     * 构造记忆仓库。
     * @param {string} [filePath] 持久化文件路径，默认使用 config.memory.filePath
     */
    constructor(filePath) {
        this.filePath = filePath || config.memory.filePath;
        this.shortTerm = [];
        this.longTerm = [];
        this._ensureDir();
        this._load();
    }

    /**
     * 确保存储目录存在。
     */
    _ensureDir() {
        const dir = path.dirname(this.filePath);
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
    }

    /**
     * 从磁盘加载长期记忆。
     */
    _load() {
        if (!fs.existsSync(this.filePath)) {
            return;
        }
        try {
            const data = fs.readFileSync(this.filePath, "utf-8");
            const parsed = JSON.parse(data);
            if (parsed && Array.isArray(parsed.longTerm)) {
                this.longTerm = parsed.longTerm;
            }
        } catch (error) {
            console.error("加载记忆文件失败:", error.message);
        }
    }

    /**
     * 将长期记忆保存到磁盘。
     */
    _save() {
        const data = {
            longTerm: this.longTerm
        };
        fs.writeFileSync(this.filePath, JSON.stringify(data, null, 4), "utf-8");
    }

    /**
     * 添加一条短期记忆，短期记忆不会持久化，仅用于当前会话上下文。
     * @param {string} content 记忆内容
     * @param {string} [source=""] 来源说明
     */
    addShortTerm(content, source) {
        this.shortTerm.push({
            type: "shortTerm",
            content: content,
            source: source || "",
            timestamp: Date.now()
        });
    }

    /**
     * 添加一条长期记忆，并立即持久化。
     * @param {string} content 记忆内容
     * @param {string} [source=""] 来源说明
     */
    addLongTerm(content, source) {
        this.longTerm.push({
            type: "longTerm",
            content: content,
            source: source || "",
            timestamp: Date.now()
        });
        this._save();
    }

    /**
     * 获取最近的短期记忆。
     * @param {number} [limit=10] 条数
     * @returns {Object[]}
     */
    getRecentShortTerm(limit) {
        if (!limit) {
            limit = 10;
        }
        return this.shortTerm.slice(-limit);
    }

    /**
     * 获取最近的长期记忆。
     * @param {number} [limit=10] 条数
     * @returns {Object[]}
     */
    getRecentLongTerm(limit) {
        if (!limit) {
            limit = 10;
        }
        return this.longTerm.slice(-limit);
    }

    /**
     * 按关键词在所有记忆中进行简单检索，不区分大小写。
     * @param {string} keyword
     * @returns {Object[]}
     */
    search(keyword) {
        const lowerKeyword = keyword.toLowerCase();
        const all = this.shortTerm.concat(this.longTerm);
        return all.filter(function (item) {
            return item.content.toLowerCase().indexOf(lowerKeyword) !== -1;
        });
    }

    /**
     * 将会话中的关键信息沉淀为长期记忆。
     * @param {string} summary 总结文本
     */
    persistSummary(summary) {
        this.addLongTerm(summary, "session-summary");
    }
}

module.exports = MemoryStore;
