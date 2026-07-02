"use strict";

/**
 * 多 Agent 系统编排入口。
 * 统一创建消息总线、黑板、协调者与多个工作 Agent，提供简化的协作接口。
 */

const MessageBus = require("./MessageBus");
const Blackboard = require("./Blackboard");
const CoordinatorAgent = require("./CoordinatorAgent");
const WorkerAgent = require("./WorkerAgent");
const logger = require("../utils/logger");

class MultiAgentSystem {
    /**
     * 创建多 Agent 系统。
     * @param {Object} options - 配置项
     * @param {Object} options.coordinatorConfig - 协调者配置
     * @param {Object} options.sharedConfig - Worker 共享配置，例如 allowCommandExecution
     * @param {Object} options.llmConfig - LLM 配置（当未单独指定 Provider 时使用）
     */
    constructor(options) {
        options = options || {};

        /**
         * 消息总线实例。
         * @type {MessageBus}
         */
        this.bus = new MessageBus();

        /**
         * 黑板实例。
         * @type {Blackboard}
         */
        this.blackboard = new Blackboard();

        /**
         * 协调者 Agent。
         * @type {CoordinatorAgent}
         */
        this.coordinator = null;

        /**
         * Worker Agent 映射。
         * @type {Object}
         */
        this.workers = {};

        /**
         * 共享 LLM 配置。
         * @type {Object}
         */
        this.llmConfig = options.llmConfig || {};

        /**
         * Worker 共享配置。
         * @type {Object}
         */
        this.sharedConfig = options.sharedConfig || {};

        // 创建协调者，默认使用独立的长期记忆文件，避免多 Agent 并发写入冲突
        const coordinatorOptions = options.coordinatorConfig || {};
        this.coordinator = new CoordinatorAgent(
            Object.assign({}, coordinatorOptions, {
                name: coordinatorOptions.name || "coordinator",
                bus: this.bus,
                blackboard: this.blackboard,
                llmConfig: this.llmConfig,
                memoryPath: coordinatorOptions.memoryPath || "./.memory/coordinator.json"
            })
        );
    }

    /**
     * 创建一个 WorkerAgent 并注册到系统。
     * @param {Object} config - Worker 配置
     * @param {string} config.name - Worker 名称
     * @param {string} config.role - 角色
     * @param {string} config.expertise - 专长
     * @returns {WorkerAgent} Worker 实例
     */
    createWorker(config) {
        config = config || {};
        const worker = new WorkerAgent({
            name: config.name,
            role: config.role,
            expertise: config.expertise,
            systemPrompt: config.systemPrompt,
            bus: this.bus,
            blackboard: this.blackboard,
            llmConfig: config.llmConfig || this.llmConfig,
            llmProvider: config.llmProvider,
            allowCommandExecution: this.sharedConfig.allowCommandExecution,
            memoryPath: config.memoryPath || `./.memory/${config.name}.json`,
            worldBookPath: config.worldBookPath,
            maxSteps: config.maxSteps || 10
        });

        this.workers[worker.name] = worker;
        this.coordinator.registerWorker(worker);
        return worker;
    }

    /**
     * 根据名称获取已注册的 Worker。
     * @param {string} name - Worker 名称
     * @returns {WorkerAgent|undefined} Worker 实例
     */
    getWorker(name) {
        return this.workers[name];
    }

    /**
     * 执行一次多 Agent 协作任务。
     * @param {string} userInput - 用户需求
     * @param {Array<Object>} assignments - 子任务分配列表
     * @returns {Promise<string>} 最终汇总结果
     */
    async run(userInput, assignments) {
        logger.info("多 Agent 系统开始协作");
        const result = await this.coordinator.coordinate(userInput, assignments);
        logger.info("多 Agent 系统协作完成");
        return result;
    }

    /**
     * 获取黑板当前全部数据。
     * @returns {Object} 数据副本
     */
    getBlackboardData() {
        return this.blackboard.getAll();
    }
}

module.exports = MultiAgentSystem;
