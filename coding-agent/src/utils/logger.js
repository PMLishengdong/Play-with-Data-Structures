"use strict";

/**
 * 简易日志工具，输出带时间戳和级别前缀的消息。
 * 仅输出到控制台，不引入额外日志依赖。
 */

const LEVELS = {
    DEBUG: 0,
    INFO: 1,
    WARN: 2,
    ERROR: 3
};

// 默认日志级别为 INFO，可通过环境变量 LOG_LEVEL 调整
let currentLevel = LEVELS.INFO;
const envLevel = process.env.LOG_LEVEL && process.env.LOG_LEVEL.toUpperCase();
if (envLevel && LEVELS[envLevel] !== undefined) {
    currentLevel = LEVELS[envLevel];
}

/**
 * 格式化日志消息，附加时间戳和级别。
 * @param {string} level - 日志级别字符串
 * @param {string} message - 日志正文
 * @returns {string} 格式化后的日志字符串
 */
function format(level, message) {
    const now = new Date().toISOString();
    return `[${now}] [${level}] ${message}`;
}

/**
 * 输出 DEBUG 级别日志。
 * @param {string} message - 日志内容
 */
function debug(message) {
    if (currentLevel <= LEVELS.DEBUG) {
        console.debug(format("DEBUG", message));
    }
}

/**
 * 输出 INFO 级别日志。
 * @param {string} message - 日志内容
 */
function info(message) {
    if (currentLevel <= LEVELS.INFO) {
        console.info(format("INFO", message));
    }
}

/**
 * 输出 WARN 级别日志。
 * @param {string} message - 日志内容
 */
function warn(message) {
    if (currentLevel <= LEVELS.WARN) {
        console.warn(format("WARN", message));
    }
}

/**
 * 输出 ERROR 级别日志。
 * @param {string} message - 日志内容
 */
function error(message) {
    if (currentLevel <= LEVELS.ERROR) {
        console.error(format("ERROR", message));
    }
}

module.exports = {
    debug,
    info,
    warn,
    error
};
