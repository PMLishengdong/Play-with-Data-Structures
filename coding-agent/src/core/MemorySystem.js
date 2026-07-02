"use strict";

/**
 * 记忆系统。
 * 维护短期记忆（当前会话消息）与长期记忆（持久化到本地 JSON 文件）。
 */

const fs = require("fs").promises;
const path = require("path");
const logger = require("../utils/logger");

class MemorySystem {
    /**
     * 创建记忆系统实例。
     * @param {Object} options - 配置项
     * @param {string} options.longTermPath - 长期记忆文件路径
     */
    constructor(options) {
        options = options || {};

        /**
         * 短期记忆：当前会话中的交互记录数组。
         * @type {Array<Object>}
         */
        this.shortTerm = [];

        /**
         * 长期记忆：持久化存储的关键事实与摘要。
         * @type {Array<Object>}
         */
        this.longTerm = [];

        /**
         * 长期记忆文件路径。
         * @type {string}
         */
        this.longTermPath = options.longTermPath || path.join(process.cwd(), "memory.json");

        /**
         * 是否已加载长期记忆。
         * @type {boolean}
         */
        this.loaded = false;
    }

    /**
     * 加载长期记忆文件，若文件不存在则初始化为空数组。
     */
    async load() {
        if (this.loaded) {
            return;
        }

        try {
            const data = await fs.readFile(this.longTermPath, "utf-8");
            this.longTerm = JSON.parse(data);
            logger.info(`长期记忆已加载: ${this.longTermPath}, 条目数=${this.longTerm.length}`);
        } catch (err) {
            if (err.code === "ENOENT") {
                this.longTerm = [];
                logger.info("长期记忆文件不存在，已初始化为空");
            } else {
                logger.error(`加载长期记忆失败: ${err.message}`);
                this.longTerm = [];
            }
        }

        this.loaded = true;
    }

    /**
     * 将长期记忆保存到本地文件。
     */
    async save() {
        try {
            await fs.mkdir(path.dirname(this.longTermPath), { recursive: true });
            await fs.writeFile(
                this.longTermPath,
                JSON.stringify(this.longTerm, null, 4),
                "utf-8"
            );
            logger.debug("长期记忆已保存");
        } catch (err) {
            logger.error(`保存长期记忆失败: ${err.message}`);
        }
    }

    /**
     * 添加一条短期记忆。
     * @param {string} role - 角色：system / user / assistant / tool
     * @param {string} content - 内容
     * @param {Object} extra - 额外字段，例如 tool_call_id、name 等
     */
    addShortTerm(role, content, extra) {
        const entry = {
            role: role,
            content: content
        };

        if (extra) {
            Object.keys(extra).forEach(function (key) {
                entry[key] = extra[key];
            });
        }

        this.shortTerm.push(entry);
    }

    /**
     * 添加一条长期记忆。
     * @param {string} type - 记忆类型，例如 fact / summary / preference
     * @param {string} content - 记忆内容
     */
    async addLongTerm(type, content) {
        await this.load();
        this.longTerm.push({
            type: type,
            content: content,
            createdAt: new Date().toISOString()
        });
        await this.save();
    }

    /**
     * 获取格式化的长期记忆文本，用于拼接到系统提示中。
     * @returns {string} 长期记忆文本
     */
    async getLongTermText() {
        await this.load();
        if (this.longTerm.length === 0) {
            return "";
        }

        const lines = this.longTerm.map(function (item) {
            return `- [${item.type}] ${item.content}`;
        });
        return lines.join("\n");
    }

    /**
     * 清空短期记忆。
     */
    clearShortTerm() {
        this.shortTerm = [];
    }

    /**
     * 获取最近的若干条短期记忆。
     * @param {number} count - 条数
     * @returns {Array<Object>} 消息数组
     */
    getRecentShortTerm(count) {
        if (!count || count <= 0) {
            return this.shortTerm.slice();
        }
        return this.shortTerm.slice(-count);
    }
}

module.exports = MemorySystem;
