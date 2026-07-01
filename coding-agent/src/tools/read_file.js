const fs = require("fs");
const BaseTool = require("./base_tool");

// 读取文件内容工具（无状态，导出单例即可复用）
module.exports = new BaseTool({
    name: "read_file",
    description: "读取指定路径的文件内容，返回文本字符串。",
    parameters: {
        type: "object",
        properties: {
            path: { type: "string", description: "文件路径" }
        },
        required: ["path"]
    },
    handler: async function (args) {
        const filePath = args.path;
        if (!filePath) {
            return "缺少参数 path";
        }
        const content = fs.readFileSync(filePath, "utf-8");
        return content;
    }
});
