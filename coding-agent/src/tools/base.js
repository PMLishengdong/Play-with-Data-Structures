/**
 * BaseTool - 所有工具的基类
 * 定义工具的标准接口：名称、描述、参数Schema、执行方法
 */

class BaseTool {
  /**
   * @param {Object} options
   * @param {string} options.name - 工具名称（供 LLM 调用）
   * @param {string} options.description - 工具描述
   * @param {Object} options.parameters - JSON Schema 格式的参数定义
   */
  constructor(options = {}) {
    this.name = options.name || 'unnamed_tool';
    this.description = options.description || '';
    this.parameters = options.parameters || {
      type: 'object',
      properties: {},
      required: [],
    };
  }

  /**
   * 将工具转换为 LLM function calling 格式
   * @returns {Object} OpenAI-compatible tool definition
   */
  toToolDefinition() {
    return {
      type: 'function',
      function: {
        name: this.name,
        description: this.description,
        parameters: this.parameters,
      },
    };
  }

  /**
   * 执行工具 - 子类必须实现此方法
   * @param {Object} args - 工具参数
   * @param {Object} context - 执行上下文（agent实例等）
   * @returns {Promise<string>} 执行结果文本
   */
  async execute(args, context) {
    throw new Error(`Tool "${this.name}" must implement execute() method`);
  }

  /**
   * 验证参数是否符合 schema
   * @param {Object} args
   * @returns {boolean}
   */
  validate(args) {
    const required = this.parameters.required || [];
    for (const key of required) {
      if (args[key] === undefined || args[key] === null) {
        throw new Error(`Missing required parameter: "${key}" for tool "${this.name}"`);
      }
    }
    return true;
  }
}

module.exports = { BaseTool };