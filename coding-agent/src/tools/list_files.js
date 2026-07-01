const fs = require("fs");
const BaseTool = require("./base_tool");

// 列出目录内容工具
module.exports = new BaseTool({
    name: "list_files",
    description: "列出指定目录下的文件和子目录，默认列出当前目录。",
    parameters: {
        type: "object",
        properties: {
            dir: { type: "string", description: "目录路径，默认当前目录" }
        },
        required: []
    },
    handler: async function (args) {
        const dir = args.dir || ".";
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        const lines = entries.map(function (e) {
            return `${e.isDirectory() ? "[目录]" : "[文件]"} ${e.name}`;
        });
        return lines.join("\n") || "(空目录)";
    }
});
