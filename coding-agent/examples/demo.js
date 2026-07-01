/**
 * Coding Agent Demo
 * 演示如何使用编程模式创建和使用 Agent
 *
 * 运行: node examples/demo.js
 */

const { CodingAgent } = require('../src/agent');

async function main() {
  // 1. 创建 Agent（需要设置环境变量 LLM_API_KEY）
  const agent = new CodingAgent({
    // apiKey: 'sk-your-key',          // 或通过环境变量 LLM_API_KEY 设置
    // baseURL: 'https://api.deepseek.com/v1',  // 或通过 LLM_BASE_URL 设置
    // model: 'deepseek-chat',         // 或通过 LLM_MODEL 设置
    workingDirectory: process.cwd(),
    verbose: true,
  });

  console.log('=== Coding Agent Demo ===\n');

  // 2. 查看可用工具
  console.log('Available tools:', agent.toolRegistry.listTools().join(', '), '\n');

  // 3. 查看初始状态
  const status = agent.getStatus();
  console.log('Agent status:', JSON.stringify(status, null, 2), '\n');

  // 4. 运行一个任务（需要有效的 API 密钥）
  const useLLM = process.env.LLM_API_KEY && process.env.LLM_API_KEY !== 'sk-your-key';

  if (useLLM) {
    console.log('Running agent with LLM...\n');
    const result = await agent.run('Hello! Please list the tools you have available and describe what each one does.');
    console.log('\nAgent response:\n', result);
  } else {
    console.log('=== No LLM API key configured ===');
    console.log('To run with a real LLM, set the environment variable:');
    console.log('  export LLM_API_KEY=sk-your-actual-key');
    console.log('');
    console.log('=== Testing tool system directly ===\n');

    // 测试工具系统
    const ctx = {
      workingDirectory: process.cwd(),
      memory: agent.memory,
      planner: agent.planner,
      logger: console.log,
    };

    // 测试 ListDirectory
    console.log('--- Testing list_directory tool ---');
    const lsResult = await agent.toolRegistry.executeTool('list_directory', {
      path: __dirname,
    }, ctx);
    console.log(lsResult);

    // 测试 Read
    console.log('\n--- Testing read tool ---');
    const readResult = await agent.toolRegistry.executeTool('read', {
      file_path: __filename,
      limit: 10,
    }, ctx);
    console.log(readResult);

    // 测试 Write
    console.log('\n--- Testing write tool ---');
    const writeResult = await agent.toolRegistry.executeTool('write', {
      file_path: '/tmp/agent_test.txt',
      content: 'Hello from Coding Agent!',
    }, ctx);
    console.log(writeResult);

    // 测试 Glob
    console.log('\n--- Testing glob tool ---');
    const globResult = await agent.toolRegistry.executeTool('glob', {
      pattern: '*.js',
      path: __dirname,
    }, ctx);
    console.log(globResult);

    // 测试 Grep
    console.log('\n--- Testing grep tool ---');
    const grepResult = await agent.toolRegistry.executeTool('grep', {
      pattern: 'CodingAgent',
      path: __dirname,
      output_mode: 'content',
    }, ctx);
    console.log(grepResult);
  }
}

main().catch(err => {
  console.error('Demo error:', err);
  process.exit(1);
});