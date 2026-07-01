/**
 * DeleteFileTool - 删除文件
 * 对应 Trae IDE 的 DeleteFile 工具
 */

const { BaseTool } = require('./base');
const fs = require('fs');

class DeleteFileTool extends BaseTool {
  constructor() {
    super({
      name: 'delete_file',
      description: '删除一个或多个文件。支持批量删除。',
      parameters: {
        type: 'object',
        properties: {
          file_paths: {
            type: 'array',
            items: { type: 'string' },
            description: '要删除的文件路径列表（绝对路径）',
          },
        },
        required: ['file_paths'],
      },
    });
  }

  async execute(args) {
    const { file_paths } = args;
    const results = [];

    for (const fp of file_paths) {
      if (!fs.existsSync(fp)) {
        results.push(`File not found: "${fp}"`);
        continue;
      }
      fs.unlinkSync(fp);
      results.push(`Deleted: "${fp}"`);
    }

    return results.join('\n');
  }
}

module.exports = { DeleteFileTool };