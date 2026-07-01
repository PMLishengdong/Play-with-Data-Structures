const ToolRegistry = require("./registry");
const readFileTool = require("./read_file");
const writeFileTool = require("./write_file");
const listFilesTool = require("./list_files");
const { createAskUserTool } = require("./ask_user");

// 创建默认工具注册表，注册内置工具并返回
function createDefaultRegistry(options) {
    const registry = new ToolRegistry();
    registry.register(readFileTool);
    registry.register(writeFileTool);
    registry.register(listFilesTool);
    // 询问用户工具需要输入函数，单独通过工厂创建
    registry.register(createAskUserTool(options || {}));
    return registry;
}

module.exports = {
    ToolRegistry,
    createDefaultRegistry,
    readFileTool,
    writeFileTool,
    listFilesTool,
    createAskUserTool
};
