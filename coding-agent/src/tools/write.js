/**
 * WriteTool - 创建或覆盖文件
 * 对应 Trae IDE 的 Write 工具
 */

const { BaseTool } = require('./base');
const fs = require('fs');
const path = require('path');

class WriteTool extends BaseTool {
  constructor() {
    super({
      name: 'write',
      description: '创建新文件或覆盖已有文件。如果父目录不存在则会自动创建。',
      parameters: {
        type: 'object',
        properties: {
          file_path: {
            type: 'string',
            description: '要写入的文件的绝对路径',
          },
          content: {
            type: 'string',
            description: '文件内容',
          },
        },
        required: ['file_path', 'content'],
      },
    });
  }

  async execute(args) {
    const { file_path, content } = args;

    const dir = path.dirname(file_path);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }

    fs.writeFileSync(file_path, content, 'utf-8');
    return `Successfully wrote ${content.length} characters to "${file_path}"`;
  }
}

module.exports = { WriteTool };