/**
 * RunCommandTool - 执行终端命令
 * 对应 Trae IDE 的 RunCommand 工具
 */

const { BaseTool } = require('./base');
const { execSync } = require('child_process');

class RunCommandTool extends BaseTool {
  constructor() {
    super({
      name: 'run_command',
      description: '在终端中执行命令并返回输出结果。支持设置工作目录和环境变量。',
      parameters: {
        type: 'object',
        properties: {
          command: {
            type: 'string',
            description: '要执行的终端命令',
          },
          cwd: {
            type: 'string',
            description: '工作目录（绝对路径），可选',
          },
          timeout: {
            type: 'integer',
            description: '超时时间（毫秒），默认 30000',
          },
        },
        required: ['command'],
      },
    });
  }

  async execute(args) {
    const { command, cwd, timeout = 30000 } = args;

    try {
      const output = execSync(command, {
        cwd: cwd || process.cwd(),
        timeout: timeout,
        maxBuffer: 10 * 1024 * 1024, // 10MB
        encoding: 'utf-8',
        env: { ...process.env, PAGER: 'cat' },
      });
      return output || '(command completed with no output)';
    } catch (err) {
      const stderr = err.stderr || '';
      const stdout = err.stdout || '';
      return `Command exited with code ${err.status}:\n${stdout}\n${stderr}`.trim();
    }
  }
}

module.exports = { RunCommandTool };