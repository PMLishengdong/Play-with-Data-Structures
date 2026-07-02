"use strict";

/**
 * 协调者 Agent。
 * 负责将复杂任务分解为子任务，分发给多个 WorkerAgent，
 * 收集执行结果并合成最终输出。
 */

const Agent = require("./Agent");
const logger = require("../utils/logger");

class CoordinatorAgent extends Agent {
    /**
     * 创建协调者 Agent。
     * @param {Object} options - 配置项
     * @param {string} options.name - Agent 名称，默认 "coordinator"
     * @param {MessageBus} options.bus - 所属消息总线
     * @param {Blackboard} options.blackboard - 所属黑板
     */
    constructor(options) {
        options = options || {};

        const basePrompt = options.systemPrompt || "你是一个任务协调专家。";
        options.systemPrompt = `${basePrompt}\n你的职责是将复杂需求拆分为多个子任务，分发给不同专家 Agent，并汇总结果。`;

        super(options);

        this.name = options.name || "coordinator";
        this.bus = options.bus || null;
        this.blackboard = options.blackboard || null;

        /**
         * 已注册的 WorkerAgent 实例映射。
         * @type {Object}
         */
        this.workers = {};

        /**
         * 任务结果收集器，key 为 taskId。
         * @type {Object}
         */
        this.pendingResults = {};

        /**
         * 任务 ID 计数器，用于生成唯一 taskId。
         * @type {number}
         */
        this.taskCounter = 0;

        this._registerBusHandlers();
    }

    /**
     * 注册消息总线处理器，监听 Worker 的任务完成消息。
     */
    _registerBusHandlers() {
        const self = this;
        if (!self.bus) {
            return;
        }

        self.bus.subscribe(`to:${self.name}`, function (envelope) {
            if (envelope.type === "task_done") {
                self._onTaskDone(envelope);
            }
        });
    }

    /**
     * 处理 Worker 的任务完成消息。
     * @param {Object} envelope - 消息信封
     */
    _onTaskDone(envelope) {
        const payload = envelope.payload || {};
        const taskId = payload.taskId;
        const result = payload.result;

        logger.info(`[${this.name}] 收到 Worker ${envelope.from} 的任务完成消息，taskId=${taskId}`);

        if (taskId && this.pendingResults[taskId] !== undefined) {
            this.pendingResults[taskId] = {
                from: envelope.from,
                result: result
            };
        }
    }

    /**
     * 注册 WorkerAgent。
     * @param {WorkerAgent} worker - 工作 Agent 实例
     */
    registerWorker(worker) {
        if (!worker || !worker.name) {
            throw new Error("注册 Worker 失败：实例或名称为空");
        }
        this.workers[worker.name] = worker;
        logger.info(`[${this.name}] 注册 Worker: ${worker.name}`);
    }

    /**
     * 将任务分发给指定 Worker。
     * @param {string} workerName - Worker 名称
     * @param {Object} task - 任务对象
     * @returns {Promise<Object>} 包含 taskId 的对象
     */
    async assignTask(workerName, task) {
        const worker = this.workers[workerName];
        if (!worker) {
            throw new Error(`Worker 未找到: ${workerName}`);
        }

        const taskId = task && task.id ? task.id : `task_${Date.now()}_${this.taskCounter++}`;
        const taskWithId = Object.assign({}, task, { id: taskId });

        this.pendingResults[taskId] = null;

        if (this.bus) {
            this.bus.send({
                from: this.name,
                to: workerName,
                type: "task",
                payload: taskWithId
            });
        } else {
            // 无总线时直接调用
            const result = await worker.receiveTask(taskWithId);
            this.pendingResults[taskId] = {
                from: workerName,
                result: result
            };
        }

        return { taskId: taskId };
    }

    /**
     * 并行分发多个子任务到不同 Worker，并等待全部完成。
     * @param {Array<Object>} assignments - 分配列表，每项 { workerName, task }
     * @returns {Promise<Array<Object>>} 结果列表
     */
    async runParallel(assignments) {
        const self = this;

        // 先发起所有任务分配
        const taskIds = [];
        for (let i = 0; i < assignments.length; i += 1) {
            const item = assignments[i];
            const info = await self.assignTask(item.workerName, item.task);
            taskIds.push(info.taskId);
        }

        // 轮询等待所有结果（简单实现，避免引入额外依赖）
        const maxWaitMs = 120000;
        const intervalMs = 200;
        let waited = 0;

        while (waited < maxWaitMs) {
            let allDone = true;
            for (let i = 0; i < taskIds.length; i += 1) {
                if (self.pendingResults[taskIds[i]] === null) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                break;
            }

            await self._sleep(intervalMs);
            waited += intervalMs;
        }

        // 收集结果
        const results = [];
        for (let i = 0; i < taskIds.length; i += 1) {
            const taskId = taskIds[i];
            const entry = self.pendingResults[taskId];
            results.push({
                taskId: taskId,
                workerName: entry && entry.from,
                result: entry && entry.result
            });
        }

        return results;
    }

    /**
     * 协调者主入口：分解任务、分发、收集、汇总。
     * @param {string} userInput - 用户需求
     * @param {Array<Object>} assignments - 预定义的任务分配
     * @returns {Promise<string>} 最终汇总结果
     */
    async coordinate(userInput, assignments) {
        logger.info(`[${this.name}] 开始协调任务: ${userInput}`);

        // 1. 使用 LLM 对任务做整体分析（可选）
        const analysis = await this._singleChat(`请分析以下需求并给出任务拆分建议：${userInput}`);

        // 2. 并行执行子任务
        const workerResults = await this.runParallel(assignments);

        // 3. 合成最终结果
        const summaryInput = this._buildSummaryInput(userInput, analysis, workerResults);
        const finalResult = await this._singleChat(summaryInput);

        // 4. 将协作结果写入黑板
        if (this.blackboard) {
            this.blackboard.set("coordinator:finalResult", finalResult);
        }

        return finalResult;
    }

    /**
     * 单次 LLM 对话，不走 Agent 主循环，用于协调者内部分析与汇总。
     * @param {string} userInput - 用户输入
     * @returns {Promise<string>} LLM 回复文本
     */
    async _singleChat(userInput) {
        const messages = await this.contextManager.buildMessages(userInput);
        const response = await this.llm.chat(messages, []);
        return response.content;
    }

    /**
     * 构造汇总提示。
     * @param {string} userInput - 用户需求
     * @param {string} analysis - 任务分析结果
     * @param {Array<Object>} workerResults - Worker 执行结果
     * @returns {string} 汇总提示文本
     */
    _buildSummaryInput(userInput, analysis, workerResults) {
        const lines = [
            "请汇总以下分析与各 Worker 的执行结果，给出最终可交付答案。",
            "",
            "【用户需求】",
            userInput,
            "",
            "【任务分析】",
            analysis,
            "",
            "【各 Worker 执行结果】"
        ];

        workerResults.forEach(function (item) {
            lines.push(`- ${item.workerName} (${item.taskId}):`);
            lines.push(item.result || "（无结果）");
            lines.push("");
        });

        lines.push("请输出完整、可交付的最终结果。");
        return lines.join("\n");
    }

    /**
     * 辅助休眠函数。
     * @param {number} ms - 毫秒
     * @returns {Promise<void>}
     */
    _sleep(ms) {
        return new Promise(function (resolve) {
            setTimeout(resolve, ms);
        });
    }
}

module.exports = CoordinatorAgent;
