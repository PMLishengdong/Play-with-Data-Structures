"use strict";

const readline = require("readline");

/**
 * 创建 askUser 工具，用于在关键节点向用户提问获取输入。
 * 注意：在 CI / 无 TTY 环境下会返回默认回答或抛出提示。
 */
function createAskUserTool() {
    return {
        name: "askUser",
        description: "当需要用户确认、补充信息或做选择时，使用该工具向用户提问。",
        parameters: {
            type: "object",
            properties: {
                question: {
                    type: "string",
                    description: "向用户展示的问题"
                }
            },
            required: ["question"]
        },

        /**
         * 执行 askUser 工具，读取用户的一行输入并返回。
         * @param {Object} args
         * @param {string} args.question
         * @returns {Promise<string>}
         */
        async execute(args) {
            const question = args.question || "请输入：";

            if (!process.stdin.isTTY) {
                const defaultAnswer = "（在非交互环境中跳过用户提问）";
                console.log(`[askUser] ${question}`);
                console.log(`[askUser] 默认回答: ${defaultAnswer}`);
                return defaultAnswer;
            }

            const rl = readline.createInterface({
                input: process.stdin,
                output: process.stdout
            });

            return new Promise(function (resolve) {
                rl.question(`${question} `, function (answer) {
                    rl.close();
                    resolve(answer.trim());
                });
            });
        }
    };
}

module.exports = createAskUserTool;
