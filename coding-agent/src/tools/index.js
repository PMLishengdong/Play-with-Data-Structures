/**
 * Tools 统一导出
 */

const { BaseTool } = require('./base');
const { ToolRegistry } = require('./registry');
const { ReadTool } = require('./read');
const { WriteTool } = require('./write');
const { EditTool } = require('./edit');
const { RunCommandTool } = require('./run_command');
const { GrepTool } = require('./grep');
const { GlobTool } = require('./glob');
const { WebSearchTool } = require('./web_search');
const { DeleteFileTool } = require('./delete_file');
const { ListDirectoryTool } = require('./list_directory');

/**
 * 创建并返回默认工具集
 * @returns {ToolRegistry}
 */
function createDefaultToolRegistry() {
  const registry = new ToolRegistry();
  registry.registerAll([
    new ReadTool(),
    new WriteTool(),
    new EditTool(),
    new RunCommandTool(),
    new GrepTool(),
    new GlobTool(),
    new WebSearchTool(),
    new DeleteFileTool(),
    new ListDirectoryTool(),
  ]);
  return registry;
}

module.exports = {
  BaseTool,
  ToolRegistry,
  ReadTool,
  WriteTool,
  EditTool,
  RunCommandTool,
  GrepTool,
  GlobTool,
  WebSearchTool,
  DeleteFileTool,
  ListDirectoryTool,
  createDefaultToolRegistry,
};