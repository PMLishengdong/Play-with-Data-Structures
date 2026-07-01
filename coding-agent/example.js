const readline = require("readline");
const { createAgent } = require("./src");

// 创建基于 readline 的用户输入函数，供 ask_user 工具调用
// 非交互环境（无 TTY）下返回空回答，避免阻塞
function createInputFn() {
    if (!process.stdin.isTTY) {
        return async function () {
            return "";
        };
    }
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });
    return async function (question) {
        return new Promise(function (resolve) {
            rl.question(`[Agent 提问] ${question}\n> `, function (answer) {
                resolve(answer);
            });
        });
    };
}

async function main() {
    const agent = createAgent({
        onLog: function (msg) {
            console.log(`[log] ${msg}`);
        },
        inputFn: createInputFn()
    });

    // 目标可由命令行参数传入，默认创建一个示例文件
    const goal = process.argv[2] || "在当前目录创建一个 hello.txt 文件，内容为 Hello Agent";
    const result = await agent.run(goal);

    console.log("\n===== 结果 =====");
    console.log("成功:", result.success);
    console.log("回答:", result.answer);
    console.log("步数:", result.steps.length);
    console.log("迭代:", result.iterations);
    process.exit(0);
}

main().catch(function (e) {
    console.error("运行出错:", e);
    process.exit(1);
});
