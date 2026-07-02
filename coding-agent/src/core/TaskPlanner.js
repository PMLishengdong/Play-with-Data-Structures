"use strict";

/**
 * 任务规划器。
 * 负责将用户需求拆分为可执行的子任务，并跟踪每个子任务的状态。
 */

class TaskPlanner {
    constructor() {
        /**
         * 子任务列表。
         * @type {Array<Object>}
         */
        this.tasks = [];

        /**
         * 当前执行中的子任务索引。
         * @type {number}
         */
        this.currentIndex = 0;
    }

    /**
     * 根据用户输入创建任务计划。
     * 这里采用简单规则：将输入按行拆分并生成待处理任务。
     * 实际使用中可由 LLM 生成更精细的计划。
     * @param {string} userInput - 用户需求
     * @returns {Array<Object>} 子任务列表
     */
    plan(userInput) {
        this.tasks = [];
        this.currentIndex = 0;

        // 简单示例：如果用户输入包含换行或分号，则拆分为多个步骤
        const steps = userInput.split(/[\n;]+/).filter(function (line) {
            return line.trim().length > 0;
        });

        if (steps.length === 0) {
            steps.push(userInput);
        }

        const self = this;
        steps.forEach(function (description, index) {
            self.tasks.push({
                id: index + 1,
                description: description.trim(),
                status: "pending",
                result: ""
            });
        });

        return this.tasks.slice();
    }

    /**
     * 获取下一个待执行的子任务。
     * @returns {Object|null} 子任务对象或 null
     */
    nextTask() {
        while (this.currentIndex < this.tasks.length) {
            const task = this.tasks[this.currentIndex];
            if (task.status === "pending") {
                return task;
            }
            this.currentIndex += 1;
        }
        return null;
    }

    /**
     * 将当前任务标记为完成并记录结果。
     * @param {string} result - 执行结果
     */
    completeCurrent(result) {
        if (this.currentIndex < this.tasks.length) {
            const task = this.tasks[this.currentIndex];
            task.status = "done";
            task.result = result || "";
            this.currentIndex += 1;
        }
    }

    /**
     * 标记当前任务失败。
     * @param {string} error - 错误信息
     */
    failCurrent(error) {
        if (this.currentIndex < this.tasks.length) {
            const task = this.tasks[this.currentIndex];
            task.status = "failed";
            task.result = error || "";
            this.currentIndex += 1;
        }
    }

    /**
     * 判断所有任务是否已完成。
     * @returns {boolean} 是否全部完成
     */
    isDone() {
        const pending = this.tasks.filter(function (task) {
            return task.status === "pending";
        });
        return this.tasks.length > 0 && pending.length === 0;
    }

    /**
     * 生成任务进度文本，便于注入系统提示。
     * @returns {string} 进度文本
     */
    toPromptText() {
        if (this.tasks.length === 0) {
            return "";
        }

        const lines = ["【当前任务计划】"];
        this.tasks.forEach(function (task) {
            lines.push(`${task.id}. [${task.status}] ${task.description}`);
            if (task.result) {
                lines.push(`   结果: ${task.result.replace(/\n/g, " ")}`);
            }
        });
        return lines.join("\n");
    }
}

module.exports = TaskPlanner;
