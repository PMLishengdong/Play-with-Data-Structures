const fs = require("fs");
const path = require("path");
const BaseTool = require("./base_tool");

// 写入文件工具：不存在则创建，存在则覆盖，并自动创建父目录
module.exports = new BaseTool({
    name: "write_file",
    description: "将内容写入指定文件（如不存在则创建，存在则覆盖）。",
    parameters: {
        type: "object",
        properties: {
            path: { type: "string", description: "文件路径" },
            content: { type: "string", description: "要写入的内容" }
        },
        required: ["path", "content"]
    },
    handler: async function (args) {
        const filePath = args.path;
        const content = args.content;
        if (!filePath) {
            return "缺少参数 path";
        }
        // 自动创建缺失的父目录
        const dir = path.dirname(filePath);
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
        fs.writeFileSync(filePath, content || "", "utf-8");
        return `已写入文件: ${filePath} (${(content || "").length} 字符)`;
    }
});
