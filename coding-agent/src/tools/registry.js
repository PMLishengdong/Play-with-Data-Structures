/**
 * ToolRegistry - 工具注册中心
 * 管理所有可用工具的注册、查找和调用
 */

class ToolRegistry {
  constructor() {
    /** @type {Map<string, BaseTool>} */
    this._tools = new Map();
  }

  /**
   * 注册一个工具
   * @param {BaseTool} tool
   */
  register(tool) {
    if (!tool || !tool.name) {
      throw new Error('Cannot register tool without a name');
    }
    if (this._tools.has(tool.name)) {
      throw new Error(`Tool "${tool.name}" is already registered`);
    }
    this._tools.set(tool.name, tool);
  }

  /**
   * 批量注册工具
   * @param {BaseTool[]} tools
   */
  registerAll(tools) {
    for (const tool of tools) {
      this.register(tool);
    }
  }

  /**
   * 获取已注册的工具
   * @param {string} name
   * @returns {BaseTool|undefined}
   */
  get(name) {
    return this._tools.get(name);
  }

  /**
   * 获取所有工具定义（LLM function calling 格式）
   * @returns {Object[]}
   */
  getToolDefinitions() {
    const defs = [];
    for (const tool of this._tools.values()) {
      defs.push(tool.toToolDefinition());
    }
    return defs;
  }

  /**
   * 列出所有已注册工具名称
   * @returns {string[]}
   */
  listTools() {
    return Array.from(this._tools.keys());
  }

  /**
   * 执行工具调用
   * @param {string} toolName
   * @param {Object} args
   * @param {Object} context - agent上下文
   * @returns {Promise<string>}
   */
  async executeTool(toolName, args, context) {
    const tool = this._tools.get(toolName);
    if (!tool) {
      return `Error: Unknown tool "${toolName}". Available tools: ${this.listTools().join(', ')}`;
    }

    try {
      tool.validate(args);
      const result = await tool.execute(args, context);
      return typeof result === 'string' ? result : JSON.stringify(result, null, 2);
    } catch (err) {
      return `Error executing tool "${toolName}": ${err.message}`;
    }
  }

  /**
   * 处理 LLM 返回的 tool_calls
   * @param {Array} toolCalls - LLM 返回的 tool_calls 数组
   * @param {Object} context
   * @returns {Promise<Array<{role:string, tool_call_id:string, content:string}>>}
   */
  async handleToolCalls(toolCalls, context) {
    const results = [];
    for (const tc of toolCalls) {
      const toolName = tc.function.name;
      let args = {};
      try {
        args = JSON.parse(tc.function.arguments);
      } catch {
        args = { raw: tc.function.arguments };
      }

      context.logger?.(`[Tool Call] ${toolName}(${JSON.stringify(args)})`);
      const result = await this.executeTool(toolName, args, context);
      context.logger?.(`[Tool Result] ${result.slice(0, 200)}${result.length > 200 ? '...' : ''}`);

      results.push({
        role: 'tool',
        tool_call_id: tc.id,
        content: result,
      });
    }
    return results;
  }
}

module.exports = { ToolRegistry };