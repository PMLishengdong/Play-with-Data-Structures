/**
 * Planner - 任务规划器
 * 负责将复杂任务分解为可执行的子步骤，并维护执行计划
 */

const crypto = require('crypto');

class TaskStep {
  /**
   * @param {Object} options
   * @param {string} options.id - 步骤ID
   * @param {string} options.description - 步骤描述
   * @param {string} options.status - pending | in_progress | completed | failed | skipped
   * @param {string} [options.assignedTool] - 建议使用的工具
   * @param {string} [options.dependsOn] - 依赖的步骤ID
   * @param {string} [options.result] - 执行结果摘要
   */
  constructor(options = {}) {
    this.id = options.id || crypto.randomUUID().slice(0, 8);
    this.description = options.description || '';
    this.status = options.status || 'pending';
    this.assignedTool = options.assignedTool || null;
    this.dependsOn = options.dependsOn || null;
    this.result = options.result || null;
    this.createdAt = Date.now();
    this.updatedAt = Date.now();
  }

  markComplete(result) {
    this.status = 'completed';
    this.result = result;
    this.updatedAt = Date.now();
  }

  markFailed(error) {
    this.status = 'failed';
    this.result = `Error: ${error}`;
    this.updatedAt = Date.now();
  }

  markInProgress() {
    this.status = 'in_progress';
    this.updatedAt = Date.now();
  }

  markSkipped(reason) {
    this.status = 'skipped';
    this.result = reason || 'Skipped';
    this.updatedAt = Date.now();
  }

  toJSON() {
    return {
      id: this.id,
      description: this.description,
      status: this.status,
      assignedTool: this.assignedTool,
      dependsOn: this.dependsOn,
      result: this.result ? this.result.slice(0, 200) : null,
      updatedAt: this.updatedAt,
    };
  }
}

class Planner {
  constructor() {
    /** @type {TaskStep[]} */
    this.steps = [];
    this.currentStepIndex = -1;
    this.planSummary = '';
    this.planCreatedAt = null;
  }

  /**
   * 解析 LLM 生成的计划，创建步骤列表
   * @param {string} planText - LLM 返回的计划文本
   */
  parsePlan(planText) {
    this.steps = [];
    this.planSummary = planText;
    this.planCreatedAt = Date.now();

    // 尝试从计划文本中提取步骤列表
    // 匹配格式: 1. xxx 或 - xxx 或 **Step 1:** xxx
    const lines = planText.split('\n');
    let stepCount = 0;
    let lastDep = null;

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;

      // 匹配编号步骤: "1. xxx", "1) xxx", "- xxx", "* xxx"
      const numberedMatch = trimmed.match(/^(?:\d+[\.\)]|[-*])\s+(.+)/);
      if (numberedMatch) {
        stepCount++;
        const step = new TaskStep({
          id: `step_${stepCount}`,
          description: numberedMatch[1],
          dependsOn: lastDep,
        });
        this.steps.push(step);
        lastDep = step.id;
      }
    }

    // 如果没有解析出步骤，创建一个默认步骤
    if (this.steps.length === 0) {
      this.steps.push(new TaskStep({
        id: 'step_1',
        description: planText.slice(0, 200),
      }));
    }

    return this.steps;
  }

  /**
   * 手动添加一个步骤
   * @param {Object} options
   * @returns {TaskStep}
   */
  addStep(options = {}) {
    const step = new TaskStep(options);
    this.steps.push(step);
    return step;
  }

  /**
   * 获取当前要执行的步骤（第一个 pending 状态且依赖已完成的步骤）
   * @returns {TaskStep|null}
   */
  getNextStep() {
    for (const step of this.steps) {
      if (step.status !== 'pending') continue;
      // 检查依赖
      if (step.dependsOn) {
        const dep = this.steps.find(s => s.id === step.dependsOn);
        if (dep && dep.status !== 'completed') continue;
      }
      return step;
    }
    return null;
  }

  /**
   * 获取当前步骤
   * @returns {TaskStep|null}
   */
  getCurrentStep() {
    if (this.currentStepIndex < 0 || this.currentStepIndex >= this.steps.length) {
      return null;
    }
    return this.steps[this.currentStepIndex];
  }

  /**
   * 获取步骤统计
   * @returns {{ total: number, completed: number, failed: number, pending: number, inProgress: number }}
   */
  getProgress() {
    const stats = { total: 0, completed: 0, failed: 0, pending: 0, inProgress: 0, skipped: 0 };
    for (const s of this.steps) {
      stats.total++;
      stats[s.status] = (stats[s.status] || 0) + 1;
    }
    return stats;
  }

  /**
   * 判断计划是否全部完成
   * @returns {boolean}
   */
  isComplete() {
    return this.steps.every(s => s.status === 'completed' || s.status === 'skipped');
  }

  /**
   * 获取计划摘要文本（用于注入到 LLM 上下文中）
   * @returns {string}
   */
  getPlanSummary() {
    const lines = this.steps.map((s, i) => {
      const statusIcon = s.status === 'completed' ? '[✓]' :
        s.status === 'failed' ? '[✗]' :
        s.status === 'in_progress' ? '[...]' :
        s.status === 'skipped' ? '[-]' : '[ ]';
      return `${statusIcon} Step ${i + 1}: ${s.description}${s.result ? `\n    -> ${s.result.slice(0, 150)}` : ''}`;
    });
    return `## Execution Plan\n\n${lines.join('\n')}\n\nProgress: ${this.getProgress().completed}/${this.getProgress().total} steps completed`;
  }

  /**
   * 重置计划
   */
  reset() {
    this.steps = [];
    this.currentStepIndex = -1;
    this.planSummary = '';
    this.planCreatedAt = null;
  }
}

module.exports = { Planner, TaskStep };