/**
 * ListDirectoryTool - 列出目录内容
 * 对应 Trae IDE 的 LS 工具
 */

const { BaseTool } = require('./base');
const fs = require('fs');
const path = require('path');

class ListDirectoryTool extends BaseTool {
  constructor() {
    super({
      name: 'list_directory',
      description: '列出目录中的文件和子目录。',
      parameters: {
        type: 'object',
        properties: {
          path: {
            type: 'string',
            description: '要列出的目录的绝对路径',
          },
        },
        required: ['path'],
      },
    });
  }

  async execute(args) {
    const { path: dirPath } = args;

    if (!fs.existsSync(dirPath)) {
      return `Error: Directory not found at "${dirPath}"`;
    }

    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    const lines = entries.map(entry => {
      const prefix = entry.isDirectory() ? '[DIR]' : '[FILE]';
      const size = entry.isFile() ? ` (${fs.statSync(path.join(dirPath, entry.name)).size} bytes)` : '';
      return `${prefix} ${entry.name}${size}`;
    });

    return lines.join('\n') || '(empty directory)';
  }
}

module.exports = { ListDirectoryTool };