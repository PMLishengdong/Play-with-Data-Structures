/**
 * ContextManager - 上下文管理器
 * 管理 Agent 的完整上下文窗口：系统提示、对话历史、token 预算、工作目录等
 */

class ContextManager {
  /**
   * @param {Object} options
   * @param {number} [options.maxTokens] - 最大 token 数
   * @param {number} [options.warningThreshold] - 告警阈值比例 (0-1)
   */
  constructor(options = {}) {
    this.maxTokens = options.maxTokens || 64000;
    this.warningThreshold = options.warningThreshold || 0.85;

    /** @type {string} */
    this.systemPrompt = '';
    this.workingDirectory = process.cwd();
    this.additionalContext = [];

    this._tokenBudget = this.maxTokens;
    this._resetStats();
  }

  /** @private */
  _resetStats() {
    this.stats = {
      totalTokens: 0,
      systemTokens: 0,
      historyTokens: 0,
      toolResultTokens: 0,
      otherTokens: 0,
      messageCount: 0,
      toolCallCount: 0,
    };
  }

  /**
   * 设置系统提示
   * @param {string} prompt
   */
  setSystemPrompt(prompt) {
    this.systemPrompt = prompt;
    this.stats.systemTokens = this._estimateTokens(prompt);
  }

  /**
   * 设置工作目录
   * @param {string} dir
   */
  setWorkingDirectory(dir) {
    this.workingDirectory = dir;
  }

  /**
   * 添加上下文片段
   * @param {string} key
   * @param {string} content
   */
  addContext(key, content) {
    this.additionalContext.push({ key, content, timestamp: Date.now() });
    this.stats.otherTokens += this._estimateTokens(content);
  }

  /**
   * 移除上下文片段
   * @param {string} key
   */
  removeContext(key) {
    this.additionalContext = this.additionalContext.filter(c => c.key !== key);
  }

  /**
   * 构建完整的消息列表（含系统提示 + 附加上下文 + 对话历史）
   * @param {Array} messages - 对话消息
   * @param {Object} [planData] - 计划数据 { planSummary, memorySummary }
   * @returns {Array<{role:string, content:string}>}
   */
  buildMessages(messages, planData = {}) {
    const result = [];

    // 1. 系统提示
    if (this.systemPrompt) {
      result.push({ role: 'system', content: this.systemPrompt });
    }

    // 2. 附加上下文
    for (const ctx of this.additionalContext) {
      result.push({
        role: 'system',
        content: `## ${ctx.key}\n${ctx.content}`,
      });
    }

    // 3. 计划与记忆摘要
    if (planData.planSummary) {
      result.push({ role: 'system', content: planData.planSummary });
    }
    if (planData.memorySummary) {
      result.push({ role: 'system', content: planData.memorySummary });
    }

    // 4. 工作目录
    result.push({
      role: 'system',
      content: `Current working directory: ${this.workingDirectory}`,
    });

    // 5. 对话历史
    result.push(...messages);

    // 更新统计
    this.stats.messageCount = result.length;
    let totalTokens = 0;
    for (const msg of result) {
      totalTokens += this._estimateTokens(msg.content);
    }
    this.stats.totalTokens = totalTokens;
    this.stats.historyTokens = totalTokens - this.stats.systemTokens - this.stats.otherTokens;

    return result;
  }

  /**
   * 判断上下文是否接近限制
   * @returns {{ isNearLimit: boolean, usage: number, remaining: number }}
   */
  checkTokenUsage() {
    const usage = this.stats.totalTokens;
    const ratio = usage / this.maxTokens;
    return {
      isNearLimit: ratio >= this.warningThreshold,
      usage,
      maxTokens: this.maxTokens,
      remaining: this.maxTokens - usage,
      ratio: Math.round(ratio * 100) + '%',
    };
  }

  /**
   * 估算 token 数（简单估算）
   * @param {string} text
   * @returns {number}
   */
  _estimateTokens(text) {
    if (!text) return 0;
    let tokens = 0;
    for (const ch of text) {
      tokens += ch.charCodeAt(0) > 127 ? 2 : 0.5;
    }
    return Math.ceil(tokens);
  }

  /**
   * 获取上下文报告
   * @returns {string}
   */
  getReport() {
    const usage = this.checkTokenUsage();
    return [
      '## Context Stats',
      `- Total tokens: ${usage.usage} / ${usage.maxTokens} (${usage.ratio})`,
      `- System: ${this.stats.systemTokens}`,
      `- History: ${this.stats.historyTokens}`,
      `- Other context: ${this.stats.otherTokens}`,
      `- Messages: ${this.stats.messageCount}`,
      `- Tool calls: ${this.stats.toolCallCount}`,
      usage.isNearLimit ? '- WARNING: Token usage is near limit!' : '',
    ].filter(Boolean).join('\n');
  }

  /**
   * 重置上下文
   */
  reset() {
    this.additionalContext = [];
    this._resetStats();
  }
}

module.exports = { ContextManager };