/**
 * GrepTool - 搜索文件内容
 * 对应 Trae IDE 的 Grep 工具（基于 ripgrep 语义）
 */

const { BaseTool } = require('./base');
const { execSync } = require('child_process');

class GrepTool extends BaseTool {
  constructor() {
    super({
      name: 'grep',
      description: '在文件中搜索文本模式，支持正则表达式、文件类型过滤和上下文行显示。',
      parameters: {
        type: 'object',
        properties: {
          pattern: {
            type: 'string',
            description: '要搜索的正则表达式模式',
          },
          path: {
            type: 'string',
            description: '搜索的目录或文件路径',
          },
          glob: {
            type: 'string',
            description: '文件 glob 过滤，如 "*.js"',
          },
          output_mode: {
            type: 'string',
            enum: ['content', 'files_with_matches', 'count'],
            description: '输出模式：content(显示匹配行), files_with_matches(仅显示文件名), count(计数)',
          },
          case_sensitive: {
            type: 'boolean',
            description: '是否区分大小写，默认 true',
          },
        },
        required: ['pattern'],
      },
    });
  }

  async execute(args) {
    const { pattern, path, glob, output_mode, case_sensitive } = args;

    try {
      let cmd = `grep -n`;

      if (!case_sensitive) {
        cmd += ' -i';
      }

      if (output_mode === 'count') {
        cmd += ' -c';
      } else if (output_mode === 'files_with_matches') {
        cmd += ' -l';
      }

      // 递归搜索目录
      if (path) {
        cmd += ' -r';
      }

      if (glob) {
        cmd += ` --include="${glob}"`;
      }

      // 转义模式并用引号包裹
      const escapedPattern = pattern.replace(/'/g, "'\\''");
      cmd += ` '${escapedPattern}'`;

      if (path) {
        cmd += ` '${path}'`;
      } else {
        cmd += ' .';
      }

      cmd += ' 2>/dev/null | head -200';

      const output = execSync(cmd, { encoding: 'utf-8', timeout: 15000 });
      return output || '(no matches found)';
    } catch (err) {
      if (err.status === 1) {
        return '(no matches found)';
      }
      return `Grep error: ${err.message}`;
    }
  }
}

module.exports = { GrepTool };