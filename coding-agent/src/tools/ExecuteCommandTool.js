"use strict";

/**
 * 执行命令工具。
 * 允许 Agent 在受控范围内执行 shell 命令，例如安装依赖、运行测试。
 * 出于安全考虑，默认处于禁用状态，需在 Agent 配置中显式启用。
 */

const { exec } = require("child_process");
const util = require("util");
const BaseTool = require("./BaseTool");
const logger = require("../utils/logger");

const execAsync = util.promisify(exec);

class ExecuteCommandTool extends BaseTool {
    constructor() {
        super();
        this.name = "execute_command";
        this.description = "执行 shell 命令，例如 npm install、node 脚本。使用前需确认环境安全。";
        this.parameters = {
            type: "object",
            properties: {
                command: {
                    type: "string",
                    description: "要执行的 shell 命令"
                },
                cwd: {
                    type: "string",
                    description: "命令工作目录，默认为当前工作目录"
                },
                timeout: {
                    type: "number",
                    description: "命令超时时间（毫秒），默认 30000"
                }
            },
            required: ["command"]
        };
    }

    /**
     * 执行 shell 命令并返回标准输出与标准错误。
     * @param {Object} args - 命令参数
     * @param {Object} context - Agent 上下文，用于检查是否允许执行命令
     * @returns {Promise<string>} 命令输出结果
     */
    async execute(args, context) {
        const allowCommand = context && context.config && context.config.allowCommandExecution;
        if (!allowCommand) {
            return "命令执行未启用，请在 Agent 配置中设置 allowCommandExecution: true";
        }

        const command = args.command || "";
        const cwd = args.cwd || process.cwd();
        const timeout = args.timeout || 30000;

        if (!command) {
            return "命令不能为空";
        }

        logger.info(`执行命令: ${command} (cwd: ${cwd})`);

        try {
            const { stdout, stderr } = await execAsync(command, {
                cwd: cwd,
                timeout: timeout,
                env: process.env
            });

            let result = "";
            if (stdout) {
                result += `STDOUT:\n${stdout}\n`;
            }
            if (stderr) {
                result += `STDERR:\n${stderr}\n`;
            }
            return result || "（命令无输出）";
        } catch (err) {
            logger.error(`命令执行失败: ${err.message}`);
            let result = `命令执行失败: ${err.message}\n`;
            if (err.stdout) {
                result += `STDOUT:\n${err.stdout}\n`;
            }
            if (err.stderr) {
                result += `STDERR:\n${err.stderr}\n`;
            }
            return result;
        }
    }
}

module.exports = ExecuteCommandTool;
