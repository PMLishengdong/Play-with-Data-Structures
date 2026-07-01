"use strict";

const readline = require("readline");

/**
 * 询问用户工具
 * 当 Agent 需要补充信息时，通过命令行向用户提问并返回答案。
 * 支持直接输入答案，或提供候选选项让用户选择。
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
                description: "当需要补充信息或确认时，向用户提出一个具体问题，可提供候选选项",
                parameters: {
                    type: "object",
                    properties: {
                        question: {
                            type: "string",
                            description: "要向用户提出的问题"
                        },
                        options: {
                            type: "array",
                            items: { type: "string" },
                            description: "候选选项列表，最后一个会自动追加\"其他（请输入）\"选项"
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
     * @param {Array<string>} [args.options] - 候选选项
     * @returns {Promise<string>} - 用户选择的答案
     */
    async execute(args) {
        const question = args.question || "请补充信息：";
        const options = args.options || [];
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });

        try {
            let answer;
            if (options.length > 0) {
                answer = await this.promptWithOptions(rl, question, options);
            } else {
                answer = await this.prompt(rl, question);
            }
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

    /**
     * 显示候选选项并让用户选择
     * @param {Object} rl - readline 接口
     * @param {string} question - 问题
     * @param {Array<string>} options - 候选选项
     * @returns {Promise<string>} - 用户选择的选项内容或自定义输入
     */
    async promptWithOptions(rl, question, options) {
        const otherLabel = "其他（请输入）";
        const lines = [question];
        for (let i = 0; i < options.length; i++) {
            lines.push(`${i + 1}. ${options[i]}`);
        }
        lines.push(`${options.length + 1}. ${otherLabel}`);
        lines.push("请输入选项编号或你的回答：");
        const promptText = lines.join("\n");

        while (true) {
            const raw = await this.prompt(rl, promptText);
            const trimmed = raw.trim();
            const index = parseInt(trimmed, 10);

            // 用户直接输入了非数字内容，视为自定义回答
            if (isNaN(index)) {
                return trimmed;
            }

            // 选择了某个候选选项
            if (index >= 1 && index <= options.length) {
                return options[index - 1];
            }

            // 选择了\"其他\"
            if (index === options.length + 1) {
                return await this.prompt(rl, "请输入其他内容：");
            }

            // 编号超出范围，重新提示
            console.log("输入的编号无效，请重新选择。");
        }
    }
}

module.exports = AskUserTool;
