"use strict";

/**
 * 任务规划器
 * 将用户目标拆分为可执行的步骤，并跟踪每一步的执行状态。
 */
class TaskPlanner {
    /**
     * 构造函数
     */
    constructor() {
        // 任务列表
        this.tasks = [];
    }

    /**
     * 根据用户目标创建计划
     * @param {string} goal - 用户目标
     * @returns {Array<Object>} - 任务步骤列表
     */
    createPlan(goal) {
        this.tasks = [
            { id: "1", description: `理解需求: ${goal}`, status: "pending" },
            { id: "2", description: "分析现有代码/上下文", status: "pending" },
            { id: "3", description: "制定实现方案", status: "pending" },
            { id: "4", description: "调用工具执行编码/验证", status: "pending" },
            { id: "5", description: "总结并汇报结果", status: "pending" }
        ];
        return this.tasks.slice();
    }

    /**
     * 更新指定任务状态
     * @param {string} taskId - 任务 ID
     * @param {string} status - 新状态：pending / in_progress / completed / failed
     */
    updateStatus(taskId, status) {
        for (let i = 0; i < this.tasks.length; i++) {
            if (this.tasks[i].id === taskId) {
                this.tasks[i].status = status;
                return;
            }
        }
    }

    /**
     * 获取当前计划
     * @returns {Array<Object>} - 任务列表
     */
    getPlan() {
        return this.tasks.slice();
    }

    /**
     * 将计划格式化为文本，便于注入系统提示词
     * @returns {string} - 格式化计划文本
     */
    formatPlan() {
        if (this.tasks.length === 0) {
            return "当前无任务计划。";
        }
        const lines = ["【当前任务计划】"];
        for (let i = 0; i < this.tasks.length; i++) {
            const task = this.tasks[i];
            lines.push(`${i + 1}. [${task.status}] ${task.description}`);
        }
        return lines.join("\n");
    }
}

module.exports = TaskPlanner;
