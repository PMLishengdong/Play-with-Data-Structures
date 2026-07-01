/**
 * GlobTool - 按模式搜索文件
 * 对应 Trae IDE 的 Glob 工具
 */

const { BaseTool } = require('./base');
const { execSync } = require('child_process');

class GlobTool extends BaseTool {
  constructor() {
    super({
      name: 'glob',
      description: '使用 glob 模式搜索文件路径。支持通配符如 **/*.js。',
      parameters: {
        type: 'object',
        properties: {
          pattern: {
            type: 'string',
            description: 'glob 模式，如 "src/**/*.js" 或 "**/*.ts"',
          },
          path: {
            type: 'string',
            description: '搜索的根目录（绝对路径），默认当前目录',
          },
        },
        required: ['pattern'],
      },
    });
  }

  async execute(args) {
    const { pattern, path } = args;
    const searchPath = path || '.';

    try {
      // 使用 find 命令模拟 glob
      const escapedPattern = pattern.replace(/'/g, "'\\''");
      const output = execSync(
        `find '${searchPath}' -type f -path '*/${escapedPattern}' 2>/dev/null | head -200`,
        { encoding: 'utf-8', timeout: 15000 }
      );
      return output || '(no files matched)';
    } catch (err) {
      return `Glob error: ${err.message}`;
    }
  }
}

module.exports = { GlobTool };