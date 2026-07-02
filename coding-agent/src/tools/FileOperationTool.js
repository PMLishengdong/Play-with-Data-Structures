"use strict";

/**
 * 文件操作工具。
 * 支持读取、写入、追加、删除文件，供 Agent 在写代码过程中使用。
 */

const fs = require("fs").promises;
const path = require("path");
const BaseTool = require("./BaseTool");
const logger = require("../utils/logger");

class FileOperationTool extends BaseTool {
    constructor() {
        super();
        this.name = "file_operation";
        this.description = "对文件系统进行读写操作，支持 read、write、append、delete、list 等动作。";
        this.parameters = {
            type: "object",
            properties: {
                action: {
                    type: "string",
                    enum: ["read", "write", "append", "delete", "list"],
                    description: "要执行的文件操作类型"
                },
                filePath: {
                    type: "string",
                    description: "目标文件或目录路径（相对或绝对路径）"
                },
                content: {
                    type: "string",
                    description: "write/append 时的文件内容"
                }
            },
            required: ["action", "filePath"]
        };
    }

    /**
     * 根据 action 分发执行不同文件操作。
     * @param {Object} args - 操作参数
     * @returns {Promise<string>} 操作结果
     */
    async execute(args) {
        const action = args.action;
        const filePath = path.resolve(args.filePath || "");

        try {
            if (action === "read") {
                const data = await fs.readFile(filePath, "utf-8");
                return data;
            }

            if (action === "write") {
                const content = args.content !== undefined ? args.content : "";
                await fs.mkdir(path.dirname(filePath), { recursive: true });
                await fs.writeFile(filePath, content, "utf-8");
                return `已写入文件: ${filePath}`;
            }

            if (action === "append") {
                const content = args.content !== undefined ? args.content : "";
                await fs.mkdir(path.dirname(filePath), { recursive: true });
                await fs.appendFile(filePath, content, "utf-8");
                return `已追加内容到: ${filePath}`;
            }

            if (action === "delete") {
                await fs.unlink(filePath);
                return `已删除文件: ${filePath}`;
            }

            if (action === "list") {
                const entries = await fs.readdir(filePath, { withFileTypes: true });
                const lines = entries.map(function (entry) {
                    return entry.isDirectory() ? `${entry.name}/` : entry.name;
                });
                return lines.join("\n") || "（空目录）";
            }

            return `未知的文件操作: ${action}`;
        } catch (err) {
            logger.error(`文件操作失败 [${action} ${filePath}]: ${err.message}`);
            return `文件操作失败: ${err.message}`;
        }
    }
}

module.exports = FileOperationTool;
