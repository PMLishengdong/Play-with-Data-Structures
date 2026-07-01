#!/usr/bin/env node

/**
 * Coding Agent - 入口文件
 * 支持 CLI 模式和编程模式使用
 */

const { CodingAgent } = require('./agent');

/**
 * 创建新的 CodingAgent 实例
 * @param {Object} config
 * @returns {CodingAgent}
 */
function createAgent(config = {}) {
  return new CodingAgent(config);
}

// CLI 模式
async function main() {
  const args = process.argv.slice(2);
  const userMessage = args.join(' ');

  if (!userMessage) {
    const pkg = require('../package.json');
    console.log(`
Coding Agent v${pkg.version}
A code-writing agent with Tool calling, Task planning, Memory system & Context management

Usage:
  # CLI mode (requires LLM_API_KEY env)
  export LLM_API_KEY=sk-your-key
  node src/index.js "Create a REST API with Express"

  # Programming mode
  const { createAgent } = require('./src');
  const agent = createAgent({ apiKey: 'sk-xxx' });
  const result = await agent.run('Write a unit test for utils.js');

Environment Variables:
  LLM_API_KEY    API 密钥（必需）
  LLM_BASE_URL   API 基础地址（默认 https://api.deepseek.com/v1）
  LLM_MODEL      模型名称（默认 deepseek-chat）
    `);
    return;
  }

  // 从环境变量读取配置
  const agent = createAgent({
    apiKey: process.env.LLM_API_KEY,
    baseURL: process.env.LLM_BASE_URL,
    model: process.env.LLM_MODEL,
    workingDirectory: process.cwd(),
    verbose: true,
  });

  console.log(`\n🤖 Coding Agent started`);
  console.log(`📁 Working directory: ${process.cwd()}`);
  console.log(`📝 Task: ${userMessage}\n`);

  try {
    const result = await agent.run(userMessage);
    console.log('\n' + '='.repeat(50));
    console.log('Final Result:');
    console.log(result);
  } catch (err) {
    console.error('\nAgent error:', err.message);
    process.exit(1);
  }
}

// 直接运行
if (require.main === module) {
  main().catch(err => {
    console.error('Fatal error:', err);
    process.exit(1);
  });
}

module.exports = { createAgent, CodingAgent };