"use strict";

const fs = require("fs");
const path = require("path");
const config = require("../config");

/**
 * 文件操作工具
 * 支持读取、写入、列出文件，所有路径以 Agent 工作目录为基准。
 */
class FileTool {
    /**
     * 构造函数
     */
    constructor() {
        this.workDir = config.agent.workDir;
        this.name = "fileTool";
        this.definition = {
            type: "function",
            function: {
                name: "fileTool",
                description: "对文件进行读取、写入或列出目录内容",
                parameters: {
                    type: "object",
                    properties: {
                        action: {
                            type: "string",
                            enum: ["read", "write", "list"],
                            description: "操作类型: read / write / list"
                        },
                        filePath: {
                            type: "string",
                            description: "相对或绝对文件路径"
                        },
                        content: {
                            type: "string",
                            description: "写入内容，仅在 action=write 时有效"
                        }
                    },
                    required: ["action", "filePath"]
                }
            }
        };
    }

    /**
     * 执行文件操作
     * @param {Object} args - 参数
     * @returns {Promise<string>} - 操作结果
     */
    async execute(args) {
        const action = args.action;
        const targetPath = this.resolvePath(args.filePath);

        if (action === "read") {
            return await this.readFile(targetPath);
        } else if (action === "write") {
            return await this.writeFile(targetPath, args.content || "");
        } else if (action === "list") {
            return await this.listFiles(targetPath);
        }

        return `不支持的操作: ${action}`;
    }

    /**
     * 将路径解析为绝对路径
     * @param {string} inputPath - 输入路径
     * @returns {string} - 绝对路径
     */
    resolvePath(inputPath) {
        if (path.isAbsolute(inputPath)) {
            return inputPath;
        }
        return path.join(this.workDir, inputPath);
    }

    /**
     * 读取文件内容
     * @param {string} filePath - 文件绝对路径
     * @returns {Promise<string>} - 文件内容或错误信息
     */
    async readFile(filePath) {
        try {
            const content = fs.readFileSync(filePath, "utf-8");
            return content;
        } catch (error) {
            return `读取失败: ${error.message}`;
        }
    }

    /**
     * 写入文件内容
     * @param {string} filePath - 文件绝对路径
     * @param {string} content - 写入内容
     * @returns {Promise<string>} - 操作结果
     */
    async writeFile(filePath, content) {
        try {
            const dir = path.dirname(filePath);
            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }
            fs.writeFileSync(filePath, content, "utf-8");
            return `文件已写入: ${filePath}`;
        } catch (error) {
            return `写入失败: ${error.message}`;
        }
    }

    /**
     * 列出目录内容
     * @param {string} dirPath - 目录绝对路径
     * @returns {Promise<string>} - 文件列表或错误信息
     */
    async listFiles(dirPath) {
        try {
            const items = fs.readdirSync(dirPath);
            if (items.length === 0) {
                return "目录为空。";
            }
            return items.join("\n");
        } catch (error) {
            return `列出目录失败: ${error.message}`;
        }
    }
}

module.exports = FileTool;
