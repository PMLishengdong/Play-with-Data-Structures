"use strict";

/**
 * 工作 Agent。
 * 继承自基础 Agent，专注于执行协调者分配的具体子任务，
 * 并通过消息总线与黑板参与多 Agent 协作。
 */

const Agent = require("./Agent");
const logger = require("../utils/logger");

class WorkerAgent extends Agent {
    /**
     * 创建工作 Agent。
     * @param {Object} options - 配置项
     * @param {string} options.name - Agent 名称
     * @param {string} options.role - 角色描述，例如 "frontend-expert"
     * @param {string} options.expertise - 专长说明
     * @param {MessageBus} options.bus - 所属消息总线
     * @param {Blackboard} options.blackboard - 所属黑板
     */
    constructor(options) {
        options = options || {};

        // 在系统提示中注入角色与专长，让 LLM 明确自身定位
        const roleHint = options.role ? `你的角色是：${options.role}。` : "";
        const expertiseHint = options.expertise ? `你的专长：${options.expertise}。` : "";
        const basePrompt = options.systemPrompt || "你是一个专业的编程助手 Agent。";
        options.systemPrompt = `${basePrompt}\n${roleHint}\n${expertiseHint}`;

        super(options);

        this.name = options.name || "worker";
        this.role = options.role || "";
        this.expertise = options.expertise || "";
        this.bus = options.bus || null;
        this.blackboard = options.blackboard || null;

        this._registerBusHandlers();
    }

    /**
     * 注册消息总线处理器，监听派发给自己的任务。
     */
    _registerBusHandlers() {
        const self = this;
        if (!self.bus) {
            return;
        }

        // 订阅发给当前 Agent 的消息
        self.bus.subscribe(`to:${self.name}`, function (envelope) {
            if (envelope.type === "task") {
                self._onTaskMessage(envelope);
            }
        });
    }

    /**
     * 处理收到的任务消息。
     * @param {Object} envelope - 消息信封
     */
    async _onTaskMessage(envelope) {
        const task = envelope.payload;
        logger.info(`[${this.name}] 收到来自 ${envelope.from} 的任务`);
        const result = await this.receiveTask(task);

        // 将结果写回黑板
        if (this.blackboard && task && task.id) {
            this.blackboard.set(`result:${task.id}`, result);
        }

        // 向协调者回复完成消息
        if (this.bus) {
            this.bus.send({
                from: this.name,
                to: envelope.from,
                type: "task_done",
                payload: {
                    taskId: task && task.id,
                    result: result
                }
            });
        }
    }

    /**
     * 接收并执行一个子任务。
     * @param {Object} task - 任务对象，包含 id、description、context 等
     * @returns {Promise<string>} 任务执行结果
     */
    async receiveTask(task) {
        const taskDescription = task && task.description ? task.description : "";
        const contextInfo = task && task.context ? task.context : "";

        logger.info(`[${this.name}] 开始执行任务: ${taskDescription}`);

        const input = contextInfo
            ? `任务背景：${contextInfo}\n具体任务：${taskDescription}`
            : taskDescription;

        try {
            const result = await this.run(input);
            logger.info(`[${this.name}] 任务完成`);
            return result;
        } catch (err) {
            logger.error(`[${this.name}] 任务执行失败: ${err.message}`);
            return `任务执行失败: ${err.message}`;
        }
    }

    /**
     * 向指定 Agent 发送消息。
     * @param {string} to - 接收方名称
     * @param {string} type - 消息类型
     * @param {*} payload - 消息负载
     */
    sendTo(to, type, payload) {
        if (!this.bus) {
            return;
        }
        this.bus.send({
            from: this.name,
            to: to,
            type: type,
            payload: payload
        });
    }

    /**
     * 向所有 Agent 广播消息。
     * @param {string} type - 消息类型
     * @param {*} payload - 消息负载
     */
    broadcast(type, payload) {
        if (!this.bus) {
            return;
        }
        this.bus.broadcast({
            from: this.name,
            type: type,
            payload: payload
        });
    }

    /**
     * 在黑板上写入共享数据。
     * @param {string} key - 键
     * @param {*} value - 值
     */
    writeBlackboard(key, value) {
        if (this.blackboard) {
            this.blackboard.set(key, value);
        }
    }

    /**
     * 从黑板读取共享数据。
     * @param {string} key - 键
     * @param {*} defaultValue - 默认值
     * @returns {*} 值
     */
    readBlackboard(key, defaultValue) {
        if (this.blackboard) {
            return this.blackboard.get(key, defaultValue);
        }
        return defaultValue;
    }
}

module.exports = WorkerAgent;
