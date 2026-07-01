"use strict";

var AskQuestionTool = require("./ask_question");
var ReadFileTool = require("./read_file");
var WriteFileTool = require("./write_file");
var EditFileTool = require("./edit_file");
var ExecuteCommandTool = require("./execute_command");
var SearchCodeTool = require("./search_code");

/**
 * ToolRegistry manages all available tools.
 * Provides lookup by name, validation, and execution dispatch.
 */
function ToolRegistry(options) {
    options = options || {};
    this.tools = {};
    this._registerDefaults(options);
}

ToolRegistry.prototype._registerDefaults = function (options) {
    this.register(new AskQuestionTool(options.ask_question));
    this.register(new ReadFileTool(options.read_file));
    this.register(new WriteFileTool(options.write_file));
    this.register(new EditFileTool(options.edit_file));
    this.register(new ExecuteCommandTool(options.execute_command));
    this.register(new SearchCodeTool(options.search_code));
};

ToolRegistry.prototype.register = function (tool) {
    if (!tool || !tool.name) {
        throw new Error("Tool must have a name");
    }
    this.tools[tool.name] = tool;
};

ToolRegistry.prototype.get = function (name) {
    return this.tools[name] || null;
};

ToolRegistry.prototype.getAll = function () {
    var result = [];
    var names = Object.keys(this.tools);
    for (var i = 0; i < names.length; i++) {
        result.push(this.tools[names[i]]);
    }
    return result;
};

ToolRegistry.prototype.getDefinitions = function () {
    var defs = [];
    var names = Object.keys(this.tools);
    for (var i = 0; i < names.length; i++) {
        defs.push(this.tools[names[i]].getDefinition());
    }
    return defs;
};

ToolRegistry.prototype.executeTool = function (toolName, args) {
    var tool = this.get(toolName);
    if (!tool) {
        return Promise.resolve({
            success: false,
            data: null,
            error: "Unknown tool: " + toolName
        });
    }

    var validation = tool.validate(args);
    if (!validation.valid) {
        return Promise.resolve({
            success: false,
            data: null,
            error: "Validation failed: " + validation.errors.join("; ")
        });
    }

    return tool.execute(args);
};

module.exports.ToolRegistry = ToolRegistry;
module.exports.AskQuestionTool = AskQuestionTool;
module.exports.ReadFileTool = ReadFileTool;
module.exports.WriteFileTool = WriteFileTool;
module.exports.EditFileTool = EditFileTool;
module.exports.ExecuteCommandTool = ExecuteCommandTool;
module.exports.SearchCodeTool = SearchCodeTool;