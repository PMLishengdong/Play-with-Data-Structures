"use strict";

const { exec } = require("child_process");
const util = require("util");

// 将 child_process.exec 转换为 Promise 风格
const execPromise = util.promisify(exec);

/**
 * 命令执行工具
 * 在 Agent 工作目录下执行 shell 命令，并返回标准输出/错误。
 */
class CommandTool {
    /**
     * 构造函数
     * @param {Object} options - 配置项
     * @param {string} options.workDir - 命令执行的工作目录
     */
    constructor(options) {
        this.workDir = (options && options.workDir) || process.cwd();
        this.name = "executeCommand";
        this.definition = {
            type: "function",
            function: {
                name: "executeCommand",
                description: "执行 shell 命令，用于编译、测试、安装依赖等操作",
                parameters: {
                    type: "object",
                    properties: {
                        command: {
                            type: "string",
                            description: "要执行的命令"
                        },
                        timeout: {
                            type: "number",
                            description: "命令超时时间（毫秒），默认 30000"
                        }
                    },
                    required: ["command"]
                }
            }
        };
    }

    /**
     * 执行命令
     * @param {Object} args - 参数
     * @param {string} args.command - 命令字符串
     * @param {number} [args.timeout] - 超时时间
     * @returns {Promise<Object>} - 包含 stdout、stderr、exitCode 的结果对象
     */
    async execute(args) {
        const command = args.command;
        const timeout = args.timeout || 30000;
        if (!command || command.length === 0) {
            return { error: "命令不能为空" };
        }

        try {
            const result = await execPromise(command, {
                cwd: this.workDir,
                timeout: timeout,
                maxBuffer: 1024 * 1024
            });
            return {
                stdout: result.stdout,
                stderr: result.stderr,
                exitCode: 0
            };
        } catch (error) {
            return {
                stdout: error.stdout || "",
                stderr: error.stderr || error.message,
                exitCode: error.code || -1
            };
        }
    }
}

module.exports = CommandTool;
