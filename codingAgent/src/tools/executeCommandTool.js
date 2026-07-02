"use strict";

const { exec } = require("child_process");
const path = require("path");

/**
 * 危险命令黑名单，禁止执行可能破坏系统的命令。
 */
const DANGEROUS_PATTERNS = [
    /^rm\s+-rf\s+\//,
    /^mkfs/,
    /^dd\s+/,
    /:(){ :|:& };:/,
    />\s*\/dev\/(sda|hd.)/
];

/**
 * 执行终端命令工具，用于编译、测试、安装依赖等场景。
 */
const executeCommandTool = {
    name: "executeCommand",
    description: "在指定工作目录下执行一条 shell 命令，返回标准输出与错误。",
    parameters: {
        type: "object",
        properties: {
            command: {
                type: "string",
                description: "要执行的命令"
            },
            cwd: {
                type: "string",
                description: "工作目录，默认为当前进程目录"
            },
            timeoutMs: {
                type: "number",
                description: "超时时间（毫秒），默认 60000"
            }
        },
        required: ["command"]
    },

    /**
     * 执行命令并返回结果。
     * @param {Object} args
     * @returns {Promise<string>}
     */
    async execute(args) {
        const command = args.command || "";
        const cwd = args.cwd ? path.resolve(args.cwd) : process.cwd();
        const timeoutMs = args.timeoutMs || 60000;

        for (let i = 0; i < DANGEROUS_PATTERNS.length; i += 1) {
            if (DANGEROUS_PATTERNS[i].test(command)) {
                return `错误：命令 "${command}" 包含危险操作，已被拦截`;
            }
        }

        return new Promise(function (resolve) {
            exec(command, { cwd: cwd, timeout: timeoutMs }, function (error, stdout, stderr) {
                let output = "";
                if (stdout) {
                    output += `STDOUT:\n${stdout}\n`;
                }
                if (stderr) {
                    output += `STDERR:\n${stderr}\n`;
                }
                if (error) {
                    output += `EXIT_CODE: ${error.code || "unknown"}\n`;
                }
                resolve(output || "命令执行完成，无输出");
            });
        });
    }
};

module.exports = executeCommandTool;
