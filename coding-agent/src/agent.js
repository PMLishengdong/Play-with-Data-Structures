/**
 * CodingAgent - 写代码的 Agent 主类
 *
 * 核心机制：
 * 1. 任务规划 - 将用户需求分解为可执行步骤
 * 2. Tool 调用 - 通过 LLM function calling 调用工具
 * 3. 记忆系统 - 短期记忆 + 长期记忆持久化
 * 4. 上下文管理 - token 预算控制与上下文窗口管理
 * 5. LLM API 集成 - 支持 OpenAI 兼容 API
 */

const { LLMClient } = require('./llm');
const { Planner } = require('./planner');
const { MemorySystem } = require('./memory');
const { ContextManager } = require('./context');
const { createDefaultToolRegistry } = require('./tools');

const DEFAULT_SYSTEM_PROMPT = `You are an expert software engineer agent. You have access to a set of tools to help you accomplish coding tasks.

## Core Principles
1. **Plan first**: For complex tasks, break them down into steps before executing
2. **Read before edit**: Always read files before modifying them
3. **Be concise**: Keep code changes minimal and focused
4. **Verify**: Run tests or checks after making changes
5. **Report**: Summarize what was done after completing tasks

## Available Tools
You have access to file system tools (read, write, edit, delete), search tools (grep, glob), command execution, and web search. Use them as needed.

## Workflow
1. Understand the user's request
2. Create a plan if the task is complex
3. Execute step by step using tools
4. Report results to the user`;

class CodingAgent {
  /**
   * @param {Object} config
   * @param {string} [config.apiKey] - LLM API 密钥
   * @param {string} [config.baseURL] - LLM API 基础地址
   * @param {string} [config.model] - 模型名称
   * @param {string} [config.workingDirectory] - 工作目录
   * @param {string} [config.persistPath] - 记忆持久化存储路径
   * @param {string} [config.systemPrompt] - 自定义系统提示
   * @param {number} [config.maxIterations] - 最大迭代次数
   * @param {boolean} [config.verbose] - 是否输出详细日志
   */
  constructor(config = {}) {
    // LLM 客户端
    this.llm = new LLMClient({
      apiKey: config.apiKey,
      baseURL: config.baseURL,
      model: config.model,
    });

    // 工具注册中心
    this.toolRegistry = createDefaultToolRegistry();

    // 任务规划器
    this.planner = new Planner();

    // 记忆系统
    this.memory = new MemorySystem({
      persistPath: config.persistPath || null,
    });

    // 上下文管理器
    this.contextManager = new ContextManager();

    // 工作目录
    this.workingDirectory = config.workingDirectory || process.cwd();
    this.contextManager.setWorkingDirectory(this.workingDirectory);

    // 系统提示
    this.systemPrompt = config.systemPrompt || DEFAULT_SYSTEM_PROMPT;
    this.contextManager.setSystemPrompt(this.systemPrompt);

    // 运行控制
    this.maxIterations = config.maxIterations || 50;
    this.verbose = config.verbose !== false;
    this.running = false;

    // 统计
    this.stats = {
      totalIterations: 0,
      totalToolCalls: 0,
      totalTokens: 0,
      startTime: null,
      endTime: null,
    };
  }

  /**
   * 注册自定义工具
   * @param {BaseTool} tool
   */
  registerTool(tool) {
    this.toolRegistry.register(tool);
  }

  /**
   * 日志输出
   */
  log(...args) {
    if (this.verbose) {
      console.log(`[Agent]`, ...args);
    }
  }

  /**
   * 运行 Agent - 处理用户消息的主循环
   * @param {string} userMessage - 用户输入
   * @returns {Promise<string>} 最终响应
   */
  async run(userMessage) {
    this.running = true;
    this.stats.startTime = Date.now();
    this.stats.totalIterations = 0;

    this.log(`Starting agent run for: "${userMessage.slice(0, 100)}..."`);

    // 1. 记忆：存储用户消息
    this.memory.add('user', userMessage, {
      workingDirectory: this.workingDirectory,
    });

    // 2. 初始 LLM 调用：理解需求并生成计划
    const initialResult = await this._llmCall([
      { role: 'user', content: userMessage },
    ], { enablePlanning: true });

    // 3. 工具调用循环
    let currentMessage = initialResult.content;
    let iterationCount = 0;
    let finalResponse = '';

    while (this.running && iterationCount < this.maxIterations) {
      iterationCount++;
      this.stats.totalIterations = iterationCount;

      // 检查 LLM 是否要求 tool calls
      const toolCalls = initialResult.toolCalls;

      if (!toolCalls || toolCalls.length === 0) {
        // LLM 直接返回了文本回复（不调用工具），检查是否完成
        this.log(`LLM text response (iter ${iterationCount})`);

        if (this._isTaskComplete(currentMessage)) {
          finalResponse = currentMessage;
          this.memory.add('assistant', currentMessage);
          this.log('Task appears complete.');
          break;
        }

        // 用当前文本回复继续对话
        this.memory.add('assistant', currentMessage);

        // 获取计划状态并继续
        const planSummary = this.planner.steps.length > 0
          ? this.planner.getPlanSummary()
          : null;

        const nextResult = await this._llmCall(
          this.memory.getMessages(),
          { planSummary }
        );
        currentMessage = nextResult.content;
        // 继续循环以检查是否有 tool_calls
        continue;
      }

      // 处理 Tool Calls
      this.stats.totalToolCalls += toolCalls.length;
      this.log(`Processing ${toolCalls.length} tool call(s) (iter ${iterationCount})`);

      // 注册工具调用数到上下文
      this.contextManager.stats.toolCallCount += toolCalls.length;

      // 执行工具
      const agentContext = this._createAgentContext();
      const toolResults = await this.toolRegistry.handleToolCalls(toolCalls, agentContext);

      // 将 LLM 的 tool call 消息和工具结果存入记忆
      this.memory.add('assistant', currentMessage || `[Tool calls: ${toolCalls.map(t => t.function.name).join(', ')}]`);

      for (const tr of toolResults) {
        this.memory.add('tool', tr.content, {
          toolCallId: tr.tool_call_id,
        });
      }

      // 更新计划进度
      this._updatePlanProgress(toolCalls, toolResults);

      // 将 tool_results 传回 LLM 继续
      const messagesForLLM = this._buildMessagesForLLM(toolResults);

      const nextResult = await this._llmCall(messagesForLLM, {
        planSummary: this.planner.steps.length > 0 ? this.planner.getPlanSummary() : null,
      });

      currentMessage = nextResult.content;

      // 如果 LLM 没有进一步 tool_calls 且任务看起来完成了
      if (!nextResult.toolCalls || nextResult.toolCalls.length === 0) {
        if (this._isTaskComplete(currentMessage) || iterationCount >= this.maxIterations - 1) {
          finalResponse = currentMessage;
          this.memory.add('assistant', currentMessage);
          break;
        }
      }

      // 继续循环
    }

    // 完成
    this.stats.endTime = Date.now();
    this.running = false;

    const duration = ((this.stats.endTime - this.stats.startTime) / 1000).toFixed(1);
    this.log(`Agent run complete in ${duration}s, ${this.stats.totalIterations} iterations, ${this.stats.totalToolCalls} tool calls`);

    // 生成会话摘要
    if (finalResponse) {
      this.memory.updateSessionSummary(
        `Completed task in ${duration}s. Key results: ${finalResponse.slice(0, 300)}`
      );
    }

    return finalResponse || 'Task completed.';
  }

  /**
   * 构建给 LLM 的消息列表（含 tool_results）
   */
  _buildMessagesForLLM(toolResults) {
    // 从记忆中获取最近的对话
    const recentMessages = this.memory.getMessages(20);

    // 追加 tool_results
    return [...recentMessages, ...toolResults];
  }

  /**
   * 判断任务是否完成
   */
  _isTaskComplete(message) {
    if (!message) return false;
    const completeIndicators = [
      'task complete', 'task completed', 'all done', 'finished',
      '任务完成', '已完成', '全部完成',
    ];
    const lower = message.toLowerCase();
    return completeIndicators.some(ind => lower.includes(ind));
  }

  /**
   * 创建 agent 执行上下文
   */
  _createAgentContext() {
    return {
      workingDirectory: this.workingDirectory,
      memory: this.memory,
      planner: this.planner,
      logger: this.verbose ? (...args) => console.log(...args) : () => {},
    };
  }

  /**
   * 更新计划进度
   */
  _updatePlanProgress(toolCalls, toolResults) {
    // 如果计划中有步骤在执行，标记当前步骤完成
    const currentStep = this.planner.getCurrentStep();
    if (currentStep && currentStep.status === 'in_progress') {
      const resultSummary = toolResults.map(r => r.content.slice(0, 100)).join('; ');
      currentStep.markComplete(resultSummary);
    }

    // 查找下一个 pending 步骤
    const nextStep = this.planner.getNextStep();
    if (nextStep) {
      nextStep.markInProgress();
      const idx = this.planner.steps.indexOf(nextStep);
      this.planner.currentStepIndex = idx;
    }
  }

  /**
   * 调用 LLM
   */
  async _llmCall(messages, options = {}) {
    const planSummary = options.planSummary || null;
    const memorySummary = this.memory.getLongTermSummary();

    // 构建上下文消息
    const contextualMessages = this.contextManager.buildMessages(messages, {
      planSummary,
      memorySummary,
    });

    // 获取工具定义
    const tools = this.toolRegistry.getToolDefinitions();

    // 检查 token 使用
    const tokenCheck = this.contextManager.checkTokenUsage();
    if (tokenCheck.isNearLimit) {
      this.log(`Token usage warning: ${tokenCheck.ratio}`);
      contextualMessages.push({
        role: 'system',
        content: `Note: Context is near capacity (${tokenCheck.ratio}). Please complete the current task concisely.`,
      });
    }

    // 调用 LLM
    try {
      const result = await this.llm.chat(contextualMessages, {
        tools: options.enablePlanning ? [] : tools,
        toolChoice: options.enablePlanning ? undefined : 'auto',
      });

      this.stats.totalTokens += result.usage?.total_tokens || 0;

      return result;
    } catch (err) {
      this.log(`LLM call failed: ${err.message}`);
      return {
        content: `Error communicating with LLM: ${err.message}. Please check your API configuration.`,
        toolCalls: null,
      };
    }
  }

  /**
   * 让 Agent 规划任务
   * @param {string} taskDescription
   * @returns {Promise<string>}
   */
  async plan(taskDescription) {
    const planningPrompt = `Please create a detailed execution plan for the following task. Break it down into numbered steps.\n\nTask: ${taskDescription}\n\nPlan:`;

    this.planner.reset();

    const result = await this._llmCall([
      { role: 'user', content: planningPrompt },
    ], { enablePlanning: true });

    this.planner.parsePlan(result.content);
    this.memory.add('assistant', `[Plan Created]\n${result.content}`);

    return result.content;
  }

  /**
   * 获取当前状态报告
   * @returns {Object}
   */
  getStatus() {
    return {
      running: this.running,
      iteration: this.stats.totalIterations,
      toolCalls: this.stats.totalToolCalls,
      memory: this.memory.getStats(),
      context: this.contextManager.checkTokenUsage(),
      planProgress: this.planner.getProgress(),
    };
  }

  /**
   * 停止 Agent
   */
  stop() {
    this.running = false;
    this.log('Agent stopped by user.');
  }
}

module.exports = { CodingAgent };