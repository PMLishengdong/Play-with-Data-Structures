"use strict";

/**
 * 黑板（Blackboard）。
 * 为多 Agent 协作提供共享状态空间，Agent 可以读取、写入和监听变化。
 */

class Blackboard {
    constructor() {
        /**
         * 共享数据存储，key-value 形式。
         * @type {Object}
         */
        this.data = {};

        /**
         * 变更监听器列表。
         * @type {Array<Function>}
         */
        this.listeners = [];
    }

    /**
     * 写入键值到黑板。
     * @param {string} key - 键
     * @param {*} value - 值
     */
    set(key, value) {
        const oldValue = this.data[key];
        this.data[key] = value;
        this._notify(key, value, oldValue);
    }

    /**
     * 从黑板读取键值。
     * @param {string} key - 键
     * @param {*} defaultValue - 不存在时返回的默认值
     * @returns {*} 存储的值或默认值
     */
    get(key, defaultValue) {
        const value = this.data[key];
        if (value === undefined) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 判断黑板中是否存在指定键。
     * @param {string} key - 键
     * @returns {boolean} 是否存在
     */
    has(key) {
        return Object.prototype.hasOwnProperty.call(this.data, key);
    }

    /**
     * 获取黑板中所有数据。
     * @returns {Object} 数据副本
     */
    getAll() {
        return JSON.parse(JSON.stringify(this.data));
    }

    /**
     * 清空黑板数据。
     */
    clear() {
        this.data = {};
    }

    /**
     * 注册变更监听器。
     * @param {Function} listener - 回调函数，参数为 (key, newValue, oldValue)
     */
    subscribe(listener) {
        this.listeners.push(listener);
    }

    /**
     * 移除变更监听器。
     * @param {Function} listener - 回调函数
     */
    unsubscribe(listener) {
        this.listeners = this.listeners.filter(function (item) {
            return item !== listener;
        });
    }

    /**
     * 通知所有监听器数据变更。
     * @param {string} key - 变更的键
     * @param {*} newValue - 新值
     * @param {*} oldValue - 旧值
     */
    _notify(key, newValue, oldValue) {
        this.listeners.forEach(function (listener) {
            try {
                listener(key, newValue, oldValue);
            } catch (err) {
                // 监听器异常不应影响主流程
            }
        });
    }
}

module.exports = Blackboard;
