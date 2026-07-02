"use strict";

const fs = require("fs");
const path = require("path");

/**
 * 读取文件内容工具。
 */
const readFileTool = {
    name: "readFile",
    description: "读取指定文件的文本内容，若文件不存在则返回错误信息。",
    parameters: {
        type: "object",
        properties: {
            filePath: {
                type: "string",
                description: "要读取的绝对路径或相对路径"
            }
        },
        required: ["filePath"]
    },

    /**
     * 执行文件读取。
     * @param {Object} args
     * @returns {Promise<string>}
     */
    async execute(args) {
        const targetPath = path.resolve(args.filePath);
        if (!fs.existsSync(targetPath)) {
            return `错误：文件不存在 ${targetPath}`;
        }
        const stats = fs.statSync(targetPath);
        if (!stats.isFile()) {
            return `错误：${targetPath} 不是文件`;
        }
        const content = fs.readFileSync(targetPath, "utf-8");
        return content;
    }
};

/**
 * 写入文件内容工具。
 */
const writeFileTool = {
    name: "writeFile",
    description: "将内容写入指定文件，若目录不存在会自动创建。",
    parameters: {
        type: "object",
        properties: {
            filePath: {
                type: "string",
                description: "目标文件路径"
            },
            content: {
                type: "string",
                description: "要写入的文本内容"
            }
        },
        required: ["filePath", "content"]
    },

    /**
     * 执行文件写入。
     * @param {Object} args
     * @returns {Promise<string>}
     */
    async execute(args) {
        const targetPath = path.resolve(args.filePath);
        const dir = path.dirname(targetPath);
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
        fs.writeFileSync(targetPath, args.content, "utf-8");
        return `已写入文件: ${targetPath}`;
    }
};

/**
 * 编辑文件内容工具：查找 oldString 并替换为 newString。
 */
const editFileTool = {
    name: "editFile",
    description: "在指定文件中查找一段文本并替换为新的文本。",
    parameters: {
        type: "object",
        properties: {
            filePath: {
                type: "string",
                description: "要编辑的文件路径"
            },
            oldString: {
                type: "string",
                description: "要查找的旧文本"
            },
            newString: {
                type: "string",
                description: "替换后的新文本"
            }
        },
        required: ["filePath", "oldString", "newString"]
    },

    /**
     * 执行文件编辑。
     * @param {Object} args
     * @returns {Promise<string>}
     */
    async execute(args) {
        const targetPath = path.resolve(args.filePath);
        if (!fs.existsSync(targetPath)) {
            return `错误：文件不存在 ${targetPath}`;
        }
        const content = fs.readFileSync(targetPath, "utf-8");
        if (content.indexOf(args.oldString) === -1) {
            return `错误：未在文件中找到指定文本`;
        }
        const newContent = content.split(args.oldString).join(args.newString);
        fs.writeFileSync(targetPath, newContent, "utf-8");
        return `已编辑文件: ${targetPath}`;
    }
};

module.exports = {
    readFileTool: readFileTool,
    writeFileTool: writeFileTool,
    editFileTool: editFileTool
};
