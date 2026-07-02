"use strict";

/**
 * 询问用户工具。
 * 当 Agent 需要澄清需求或补充信息时调用，结果回传给 LLM。
 */

const BaseTool = require("./BaseTool");
const readline = require("readline");

class AskUserTool extends BaseTool {
    constructor() {
        super();
        this.name = "ask_user";
        this.description = "当需求不明确、缺少关键信息或需要用户确认时，向用户提问。";
        this.parameters = {
            type: "object",
            properties: {
                question: {
                    type: "string",
                    description: "要向用户提出的问题"
                }
            },
            required: ["question"]
        };
    }

    /**
     * 在控制台向用户提问并等待输入。
     * @param {Object} args - 包含 question 字段
     * @returns {Promise<string>} 用户输入内容
     */
    async execute(args) {
        const question = args.question || "请补充信息：";
        const answer = await this._prompt(question);
        return answer;
    }

    /**
     * 使用 readline 读取用户单行输入。
     * @param {string} question - 提示文本
     * @returns {Promise<string>} 用户输入
     */
    _prompt(question) {
        return new Promise(function (resolve) {
            const rl = readline.createInterface({
                input: process.stdin,
                output: process.stdout
            });
            rl.question(`${question} `, function (answer) {
                rl.close();
                resolve(answer);
            });
        });
    }
}

module.exports = AskUserTool;
