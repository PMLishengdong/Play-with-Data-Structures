"use strict";

/**
 * 世界书。
 * 存放项目的通用知识、编码规范、约束与最佳实践，供 Agent 在每次请求前注入系统提示。
 */

const fs = require("fs").promises;
const path = require("path");
const logger = require("../utils/logger");

class WorldBook {
    /**
     * 创建世界书实例。
     * @param {Object} options - 配置项
     * @param {string} options.filePath - 世界书 JSON 文件路径
     */
    constructor(options) {
        options = options || {};

        /**
         * 知识条目列表。
         * @type {Array<Object>}
         */
        this.entries = [];

        /**
         * 世界书文件路径。
         * @type {string}
         */
        this.filePath = options.filePath || path.join(process.cwd(), "worldBook.json");

        /**
         * 是否已加载。
         * @type {boolean}
         */
        this.loaded = false;
    }

    /**
     * 加载世界书文件，若不存在则使用默认示例条目。
     */
    async load() {
        if (this.loaded) {
            return;
        }

        try {
            const data = await fs.readFile(this.filePath, "utf-8");
            this.entries = JSON.parse(data);
            logger.info(`世界书已加载: ${this.filePath}, 条目数=${this.entries.length}`);
        } catch (err) {
            if (err.code === "ENOENT") {
                this.entries = this._defaultEntries();
                logger.info("世界书文件不存在，已加载默认条目");
            } else {
                logger.error(`加载世界书失败: ${err.message}`);
                this.entries = this._defaultEntries();
            }
        }

        this.loaded = true;
    }

    /**
     * 保存当前世界书到文件。
     */
    async save() {
        try {
            await fs.mkdir(path.dirname(this.filePath), { recursive: true });
            await fs.writeFile(
                this.filePath,
                JSON.stringify(this.entries, null, 4),
                "utf-8"
            );
            logger.debug("世界书已保存");
        } catch (err) {
            logger.error(`保存世界书失败: ${err.message}`);
        }
    }

    /**
     * 添加一条世界书知识。
     * @param {string} category - 分类，例如 coding-style / architecture / rules
     * @param {string} title - 标题
     * @param {string} content - 内容
     */
    async addEntry(category, title, content) {
        await this.load();
        this.entries.push({
            category: category,
            title: title,
            content: content
        });
        await this.save();
    }

    /**
     * 按分类检索条目。
     * @param {string} category - 分类名
     * @returns {Array<Object>} 匹配的条目
     */
    async findByCategory(category) {
        await this.load();
        return this.entries.filter(function (entry) {
            return entry.category === category;
        });
    }

    /**
     * 生成用于拼接到系统提示中的世界书文本。
     * @returns {string} 格式化文本
     */
    async toPromptText() {
        await this.load();
        if (this.entries.length === 0) {
            return "";
        }

        const lines = ["【世界书】"];
        this.entries.forEach(function (entry) {
            lines.push(`### ${entry.category}: ${entry.title}`);
            lines.push(entry.content);
            lines.push("");
        });

        return lines.join("\n");
    }

    /**
     * 默认世界书条目，作为初始引导。
     * @returns {Array<Object>} 默认条目
     */
    _defaultEntries() {
        return [
            {
                category: "coding-style",
                title: "NodeJS 代码规范",
                content: "使用 CommonJS 模块，4 空格缩进，语句结尾加分号，变量和函数使用 camelCase，字符串简单用双引号、复杂用反引号。"
            },
            {
                category: "rules",
                title: "安全约束",
                content: "执行命令前必须确认配置 allowCommandExecution 为 true；写文件前先确认路径正确，避免覆盖重要文件。"
            },
            {
                category: "workflow",
                title: "任务处理流程",
                content: "1) 分析需求；2) 如信息不足则 ask_user；3) 制定子任务；4) 调用工具执行；5) 验证结果并总结。"
            }
        ];
    }
}

module.exports = WorldBook;
