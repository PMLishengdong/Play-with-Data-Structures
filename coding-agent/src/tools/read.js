/**
 * ReadTool - 读取文件内容
 * 对应 Trae IDE 的 Read 工具
 */

const { BaseTool } = require('./base');
const fs = require('fs');

class ReadTool extends BaseTool {
  constructor() {
    super({
      name: 'read',
      description: '读取指定文件的内容。支持通过 offset 和 limit 参数读取文件的部分内容，适用于大文件。',
      parameters: {
        type: 'object',
        properties: {
          file_path: {
            type: 'string',
            description: '要读取的文件的绝对路径',
          },
          offset: {
            type: 'integer',
            description: '起始行号（从1开始），可选',
          },
          limit: {
            type: 'integer',
            description: '要读取的行数，可选',
          },
        },
        required: ['file_path'],
      },
    });
  }

  async execute(args) {
    const { file_path, offset, limit } = args;

    if (!fs.existsSync(file_path)) {
      return `Error: File not found at "${file_path}"`;
    }

    const stats = fs.statSync(file_path);
    if (!stats.isFile()) {
      return `Error: "${file_path}" is not a file`;
    }

    const content = fs.readFileSync(file_path, 'utf-8');
    const lines = content.split('\n');

    if (offset || limit) {
      const start = offset ? Math.max(0, offset - 1) : 0;
      const end = limit ? start + limit : lines.length;
      const selected = lines.slice(start, end);
      return selected.map((line, i) => `${start + i + 1}\t${line}`).join('\n');
    }

    return content || '(empty file)';
  }
}

module.exports = { ReadTool };