"use strict";

const readline = require("readline");

/**
 * 询问用户工具
 * 当 Agent 需要补充信息时，通过命令行向用户提问并返回答案。
 */
class AskUserTool {
    /**
     * 构造函数
     */
    constructor() {
        this.name = "askUser";
        this.definition = {
            type: "function",
            function: {
                name: "askUser",
                description: "当需要补充信息或确认时，向用户提出一个具体问题",
                parameters: {
                    type: "object",
                    properties: {
                        question: {
                            type: "string",
                            description: "要向用户提出的问题"
                        }
                    },
                    required: ["question"]
                }
            }
        };
    }

    /**
     * 执行询问
     * @param {Object} args - 参数
     * @param {string} args.question - 问题内容
     * @returns {Promise<string>} - 用户输入的答案
     */
    async execute(args) {
        const question = args.question || "请补充信息：";
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });

        try {
            const answer = await this.prompt(rl, question);
            return answer;
        } finally {
            rl.close();
        }
    }

    /**
     * 包装 readline 提问为 Promise
     * @param {Object} rl - readline 接口
     * @param {string} question - 问题
     * @returns {Promise<string>} - 用户输入
     */
    prompt(rl, question) {
        return new Promise(function (resolve) {
            rl.question(question + " ", function (answer) {
                resolve(answer);
            });
        });
    }
}

module.exports = AskUserTool;
