/**
 * EditTool - 编辑文件（精确字符串替换）
 * 对应 Trae IDE 的 Edit 工具
 */

const { BaseTool } = require('./base');
const fs = require('fs');

class EditTool extends BaseTool {
  constructor() {
    super({
      name: 'edit',
      description: '对文件执行精确字符串替换。old_string 必须在文件中唯一出现，否则会失败。设置 replace_all=true 可替换所有匹配项。',
      parameters: {
        type: 'object',
        properties: {
          file_path: {
            type: 'string',
            description: '要修改的文件的绝对路径',
          },
          old_string: {
            type: 'string',
            description: '要被替换的现有文本（必须在文件中唯一）',
          },
          new_string: {
            type: 'string',
            description: '替换后的新文本',
          },
          replace_all: {
            type: 'boolean',
            description: '是否替换所有匹配项，默认 false',
          },
        },
        required: ['file_path', 'old_string', 'new_string'],
      },
    });
  }

  async execute(args) {
    const { file_path, old_string, new_string, replace_all } = args;

    if (!fs.existsSync(file_path)) {
      return `Error: File not found at "${file_path}"`;
    }

    const content = fs.readFileSync(file_path, 'utf-8');

    if (replace_all) {
      if (!content.includes(old_string)) {
        return `Error: old_string not found in "${file_path}"`;
      }
      const updated = content.split(old_string).join(new_string);
      fs.writeFileSync(file_path, updated, 'utf-8');
      const count = (content.match(new RegExp(old_string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
      return `Successfully replaced ${count} occurrence(s) in "${file_path}"`;
    }

    // 精确匹配一次
    const idx = content.indexOf(old_string);
    if (idx === -1) {
      return `Error: old_string not found in "${file_path}"`;
    }
    const secondIdx = content.indexOf(old_string, idx + 1);
    if (secondIdx !== -1) {
      return `Error: old_string appears multiple times in "${file_path}". Use replace_all=true or provide more context.`;
    }

    const updated = content.slice(0, idx) + new_string + content.slice(idx + old_string.length);
    fs.writeFileSync(file_path, updated, 'utf-8');
    return `Successfully edited "${file_path}"`;
  }
}

module.exports = { EditTool };