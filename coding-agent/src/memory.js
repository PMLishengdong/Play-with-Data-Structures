/**
 * MemorySystem - 记忆系统
 * 支持短期记忆（会话内）和长期记忆（持久化存储）
 * 实现记忆的压缩、摘要和检索
 */

const fs = require('fs');
const path = require('path');

class MemoryEntry {
  constructor(options = {}) {
    this.id = options.id || Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    this.role = options.role || 'user'; // user | assistant | tool | system
    this.content = options.content || '';
    this.summary = options.summary || null;
    this.timestamp = options.timestamp || Date.now();
    this.metadata = options.metadata || {};
    this.tokenCount = options.tokenCount || 0;
  }
}

class MemorySystem {
  /**
   * @param {Object} options
   * @param {number} [options.shortTermLimit] - 短期记忆最大消息数
   * @param {number} [options.summaryTokenThreshold] - 触发摘要的 token 阈值
   * @param {string} [options.persistPath] - 持久化存储路径
   */
  constructor(options = {}) {
    this.shortTermLimit = options.shortTermLimit || 50;
    this.summaryTokenThreshold = options.summaryTokenThreshold || 8000;
    this.persistPath = options.persistPath || null;

    /** @type {MemoryEntry[]} 短期记忆（当前会话） */
    this.shortTerm = [];

    /** @type {Object[]} 长期记忆（关键信息持久化） */
    this.longTerm = [];

    /** @type {string|null} 会话摘要 */
    this.sessionSummary = null;

    /** @type {string|null} 持久化文件路径 */
    this._persistFile = this.persistPath
      ? path.join(this.persistPath, 'memory_store.json')
      : null;

    // 如果持久化文件存在，加载长期记忆
    if (this._persistFile && fs.existsSync(this._persistFile)) {
      this._loadPersistentMemory();
    }
  }

  /**
   * 添加一条记忆
   * @param {string} role - user | assistant | tool | system
   * @param {string} content
   * @param {Object} [metadata]
   * @returns {MemoryEntry}
   */
  add(role, content, metadata = {}) {
    const entry = new MemoryEntry({
      role,
      content,
      metadata,
      tokenCount: this._estimateTokens(content),
    });

    this.shortTerm.push(entry);

    // 如果超出短期记忆限制，压缩最旧的非关键记忆
    if (this.shortTerm.length > this.shortTermLimit) {
      this._compressShortTerm();
    }

    // 提取关键信息到长期记忆
    this._extractToLongTerm(entry);

    return entry;
  }

  /**
   * 获取用于 LLM 调用的消息列表
   * @param {number} [maxMessages] - 最大消息数
   * @returns {Array<{role:string, content:string}>}
   */
  getMessages(maxMessages) {
    const msgs = this.shortTerm.slice(-(maxMessages || this.shortTermLimit)).map(e => ({
      role: e.role,
      content: e.content,
    }));
    return msgs;
  }

  /**
   * 获取带上下文的完整消息列表（含系统提示、会话摘要和计划）
   * @param {Object} context - 附加上下文 { systemPrompt, planSummary, workingDirectory }
   * @returns {Array<{role:string, content:string}>}
   */
  getContextualMessages(context = {}) {
    const messages = [];

    // 1. 系统提示
    if (context.systemPrompt) {
      messages.push({ role: 'system', content: context.systemPrompt });
    }

    // 2. 会话摘要（如果有）
    if (this.sessionSummary) {
      messages.push({
        role: 'system',
        content: `## Session Summary\n${this.sessionSummary}`,
      });
    }

    // 3. 执行计划
    if (context.planSummary) {
      messages.push({
        role: 'system',
        content: context.planSummary,
      });
    }

    // 4. 工作目录
    if (context.workingDirectory) {
      messages.push({
        role: 'system',
        content: `Working directory: ${context.workingDirectory}`,
      });
    }

    // 5. 短期记忆（最近的对话）
    const recentMessages = this.getMessages(context.messageLimit || 30);
    messages.push(...recentMessages);

    return messages;
  }

  /**
   * 更新会话摘要
   * @param {string} summary
   */
  updateSessionSummary(summary) {
    this.sessionSummary = summary;
    this._savePersistentMemory();
  }

  /**
   * 搜索长期记忆
   * @param {string} keyword
   * @returns {Object[]}
   */
  searchLongTerm(keyword) {
    if (!keyword) return this.longTerm.slice(-20);
    const lower = keyword.toLowerCase();
    return this.longTerm.filter(e =>
      (e.content && e.content.toLowerCase().includes(lower)) ||
      (e.summary && e.summary.toLowerCase().includes(lower))
    ).slice(-20);
  }

  /**
   * 添加关键信息到长期记忆
   * @param {string} key
   * @param {string} value
   */
  remember(key, value) {
    // 更新或插入
    const existing = this.longTerm.find(e => e.key === key);
    if (existing) {
      existing.value = value;
      existing.timestamp = Date.now();
    } else {
      this.longTerm.push({ key, value, timestamp: Date.now() });
    }
    this._savePersistentMemory();
  }

  /**
   * 从长期记忆中检索
   * @param {string} key
   * @returns {string|null}
   */
  recall(key) {
    const entry = this.longTerm.find(e => e.key === key);
    return entry ? entry.value : null;
  }

  /**
   * 获取长期记忆文本摘要（用于注入上下文）
   * @returns {string}
   */
  getLongTermSummary() {
    if (this.longTerm.length === 0) return '';
    const lines = this.longTerm.map(e => `  ${e.key}: ${String(e.value).slice(0, 200)}`);
    return `## Long-term Memory\n${lines.join('\n')}`;
  }

  /**
   * 获取统计信息
   * @returns {Object}
   */
  getStats() {
    return {
      shortTermCount: this.shortTerm.length,
      longTermCount: this.longTerm.length,
      hasSessionSummary: !!this.sessionSummary,
      totalTokens: this.shortTerm.reduce((s, e) => s + e.tokenCount, 0),
    };
  }

  /** 压缩短期记忆 */
  _compressShortTerm() {
    // 保留系统消息，合并最旧的历史消息
    const systemEntries = this.shortTerm.filter(e => e.role === 'system');
    const nonSystem = this.shortTerm.filter(e => e.role !== 'system');

    // 保留最近的 70%，压缩最旧的 30%
    const keepCount = Math.floor(this.shortTermLimit * 0.7);
    const compressCount = nonSystem.length - keepCount;

    if (compressCount > 0) {
      const toCompress = nonSystem.slice(0, compressCount);
      const toKeep = nonSystem.slice(compressCount);

      // 为被压缩的部分生成摘要
      const compressedSummary = toCompress
        .filter(e => e.role === 'assistant')
        .map(e => e.content.slice(0, 100))
        .join('\n');

      if (compressedSummary) {
        this.shortTerm = [
          ...systemEntries,
          new MemoryEntry({
            role: 'system',
            content: `[Compressed history: ${toCompress.length} messages summarized]\n${compressedSummary.slice(0, 500)}`,
          }),
          ...toKeep,
        ];
      } else {
        this.shortTerm = [...systemEntries, ...toKeep];
      }
    }
  }

  /** 提取关键信息到长期记忆 */
  _extractToLongTerm(entry) {
    // 将 tool 执行结果中的关键文件路径等信息存入长期记忆
    if (entry.role === 'tool') {
      const filePathMatch = entry.content.match(/"([^"]+\.(js|ts|py|json|md))"/);
      if (filePathMatch) {
        this.remember(`file:${filePathMatch[1]}`, `Modified at ${new Date().toISOString()}`);
      }
    }

    // 将 assistant 的关键决策存入长期记忆
    if (entry.role === 'assistant' && entry.content.length > 200) {
      const decisionMatch = entry.content.match(/Decision:\s*(.+?)(?:\n|$)/);
      if (decisionMatch) {
        this.remember(`decision_${entry.id}`, decisionMatch[1].trim());
      }
    }
  }

  /** 估算 token 数 */
  _estimateTokens(text) {
    if (!text) return 0;
    // 简单估算：中文字符 ~2 tokens，英文 ~1 token
    let tokens = 0;
    for (const ch of text) {
      tokens += ch.charCodeAt(0) > 127 ? 2 : 0.5;
    }
    return Math.ceil(tokens);
  }

  /** 保存长期记忆到文件 */
  _savePersistentMemory() {
    if (!this._persistFile) return;
    try {
      const dir = path.dirname(this._persistFile);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(this._persistFile, JSON.stringify({
        longTerm: this.longTerm,
        sessionSummary: this.sessionSummary,
        updatedAt: Date.now(),
      }, null, 2), 'utf-8');
    } catch (err) {
      console.error(`Failed to persist memory: ${err.message}`);
    }
  }

  /** 加载持久化记忆 */
  _loadPersistentMemory() {
    try {
      const data = JSON.parse(fs.readFileSync(this._persistFile, 'utf-8'));
      this.longTerm = data.longTerm || [];
      this.sessionSummary = data.sessionSummary || null;
    } catch {
      // 文件损坏则忽略
    }
  }

  /** 重置（清空短期、保留长期） */
  resetShortTerm() {
    this.shortTerm = [];
  }

  /** 完全重置 */
  reset() {
    this.shortTerm = [];
    this.longTerm = [];
    this.sessionSummary = null;
    if (this._persistFile && fs.existsSync(this._persistFile)) {
      fs.unlinkSync(this._persistFile);
    }
  }
}

module.exports = { MemorySystem, MemoryEntry };