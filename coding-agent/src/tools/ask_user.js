const BaseTool = require("./base_tool");

// 创建“询问用户”工具
// 通过 options.inputFn 注入真实的输入实现，使工具可在交互式终端或自定义 UI 中复用
function createAskUserTool(options) {
    // 默认输入函数：非交互环境直接返回空，避免阻塞
    const inputFn = options && options.inputFn
        ? options.inputFn
        : function () {
            return Promise.resolve("");
        };

    return new BaseTool({
        name: "ask_user",
        description: "当信息不足或需要用户决策时，向用户提问以获取补充信息。",
        parameters: {
            type: "object",
            properties: {
                question: {
                    type: "string",
                    description: "要问用户的问题"
                }
            },
            required: ["question"]
        },
        handler: async function (args) {
            const question = args.question || "(空问题)";
            // 调用注入的输入函数获取用户回答
            const answer = await inputFn(question);
            if (!answer) {
                return "用户未提供回答";
            }
            return `用户回答: ${answer}`;
        }
    });
}

module.exports = { createAskUserTool };
