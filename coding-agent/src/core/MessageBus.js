"use strict";

/**
 * 消息总线（MessageBus）。
 * 为多 Agent 协作提供发布订阅与点对点通信能力。
 */

class MessageBus {
    constructor() {
        /**
         * 订阅者映射，key 为消息类型，value 为回调数组。
         * @type {Object}
         */
        this.subscribers = {};
    }

    /**
     * 发送点对点消息。
     * @param {Object} message - 消息对象
     * @param {string} message.from - 发送方 Agent 名称
     * @param {string} message.to - 接收方 Agent 名称
     * @param {string} message.type - 消息类型
     * @param {*} message.payload - 消息负载
     */
    send(message) {
        const envelope = {
            from: message.from || "system",
            to: message.to,
            type: message.type || "message",
            payload: message.payload,
            timestamp: new Date().toISOString()
        };

        const type = `to:${envelope.to}`;
        this._dispatch(type, envelope);
        this._dispatch(envelope.type, envelope);
    }

    /**
     * 广播消息给所有订阅者。
     * @param {Object} message - 消息对象
     * @param {string} message.from - 发送方
     * @param {string} message.type - 消息类型
     * @param {*} message.payload - 消息负载
     */
    broadcast(message) {
        const envelope = {
            from: message.from || "system",
            to: "*",
            type: message.type || "broadcast",
            payload: message.payload,
            timestamp: new Date().toISOString()
        };

        this._dispatch("*", envelope);
        this._dispatch(envelope.type, envelope);
    }

    /**
     * 订阅指定类型的消息。
     * @param {string} type - 消息类型，使用 "*" 可订阅所有广播
     * @param {Function} handler - 回调函数，参数为消息对象
     */
    subscribe(type, handler) {
        if (!this.subscribers[type]) {
            this.subscribers[type] = [];
        }
        this.subscribers[type].push(handler);
    }

    /**
     * 取消订阅。
     * @param {string} type - 消息类型
     * @param {Function} handler - 回调函数
     */
    unsubscribe(type, handler) {
        const handlers = this.subscribers[type];
        if (!handlers) {
            return;
        }
        this.subscribers[type] = handlers.filter(function (item) {
            return item !== handler;
        });
    }

    /**
     * 分发消息到指定类型的所有订阅者。
     * @param {string} type - 消息类型
     * @param {Object} envelope - 消息信封
     */
    _dispatch(type, envelope) {
        const handlers = this.subscribers[type];
        if (!handlers) {
            return;
        }
        handlers.forEach(function (handler) {
            try {
                handler(envelope);
            } catch (err) {
                // 消息处理异常不应影响总线继续分发
            }
        });
    }
}

module.exports = MessageBus;
