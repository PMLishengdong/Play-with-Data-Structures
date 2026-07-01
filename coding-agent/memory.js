"use strict";

var fs = require("fs");
var path = require("path");
var EventEmitter = require("events");

/**
 * Memory system for the agent.
 * Stores conversation history, task records, extracted knowledge,
 * and provides persistence to disk.
 */
function Memory(options) {
    EventEmitter.call(this);

    options = options || {};
    this.maxHistoryLength = options.maxHistoryLength || 100;
    this.storagePath = options.storagePath || path.join(process.cwd(), ".agent-memory");

    // conversations: array of { role, content, timestamp, metadata }
    this.conversations = [];

    // tasks: array of { id, description, status, result, createdAt, updatedAt }
    this.tasks = [];

    // key-value knowledge store
    this.knowledge = {};

    // tool execution history
    this.toolHistory = [];

    // ensure storage directory exists
    if (!fs.existsSync(this.storagePath)) {
        try {
            fs.mkdirSync(this.storagePath, { recursive: true });
        } catch (_err) {
            // directory may already exist or fs doesn't support recursive
        }
    }

    // try to restore from disk
    this._load();
}

Memory.prototype = Object.create(EventEmitter.prototype);
Memory.prototype.constructor = Memory;

// ---- Conversation ----

Memory.prototype.addMessage = function (role, content, metadata) {
    this.conversations.push({
        role: role,
        content: content,
        timestamp: Date.now(),
        metadata: metadata || {}
    });

    if (this.conversations.length > this.maxHistoryLength) {
        var excess = this.conversations.length - this.maxHistoryLength;
        this.conversations.splice(0, excess);
    }

    this._save();
    this.emit("message", { role: role, content: content });
};

Memory.prototype.getMessages = function (count) {
    if (count && count < this.conversations.length) {
        return this.conversations.slice(this.conversations.length - count);
    }
    return this.conversations.slice();
};

Memory.prototype.clearMessages = function () {
    this.conversations = [];
    this._save();
};

// ---- Tasks ----

Memory.prototype.addTask = function (task) {
    var now = Date.now();
    var entry = {
        id: task.id || "task_" + now + "_" + Math.random().toString(36).substr(2, 5),
        description: task.description,
        status: task.status || "pending",
        result: task.result || null,
        createdAt: now,
        updatedAt: now
    };
    this.tasks.push(entry);
    this._save();
    this.emit("task_added", entry);
    return entry;
};

Memory.prototype.updateTask = function (taskId, updates) {
    for (var i = 0; i < this.tasks.length; i++) {
        if (this.tasks[i].id === taskId) {
            var task = this.tasks[i];
            var keys = Object.keys(updates);
            for (var j = 0; j < keys.length; j++) {
                var key = keys[j];
                if (key !== "id" && key !== "createdAt") {
                    task[key] = updates[key];
                }
            }
            task.updatedAt = Date.now();
            this._save();
            this.emit("task_updated", task);
            return task;
        }
    }
    return null;
};

Memory.prototype.getTasks = function (status) {
    if (status) {
        return this.tasks.filter(function (t) {
            return t.status === status;
        });
    }
    return this.tasks.slice();
};

Memory.prototype.getTaskById = function (taskId) {
    for (var i = 0; i < this.tasks.length; i++) {
        if (this.tasks[i].id === taskId) {
            return this.tasks[i];
        }
    }
    return null;
};

// ---- Knowledge ----

Memory.prototype.setKnowledge = function (key, value) {
    this.knowledge[key] = {
        value: value,
        timestamp: Date.now()
    };
    this._save();
};

Memory.prototype.getKnowledge = function (key) {
    var entry = this.knowledge[key];
    return entry ? entry.value : null;
};

Memory.prototype.getAllKnowledge = function () {
    var result = {};
    var keys = Object.keys(this.knowledge);
    for (var i = 0; i < keys.length; i++) {
        result[keys[i]] = this.knowledge[keys[i]].value;
    }
    return result;
};

// ---- Tool History ----

Memory.prototype.addToolCall = function (toolName, args, result, error) {
    this.toolHistory.push({
        tool: toolName,
        args: args,
        result: result,
        error: error || null,
        timestamp: Date.now()
    });

    if (this.toolHistory.length > 200) {
        this.toolHistory.splice(0, this.toolHistory.length - 200);
    }

    this._save();
};

Memory.prototype.getRecentToolCalls = function (count) {
    count = count || 10;
    if (count >= this.toolHistory.length) {
        return this.toolHistory.slice();
    }
    return this.toolHistory.slice(this.toolHistory.length - count);
};

// ---- Persistence ----

Memory.prototype._save = function () {
    try {
        var data = {
            conversations: this.conversations,
            tasks: this.tasks,
            knowledge: this.knowledge,
            toolHistory: this.toolHistory
        };
        var filePath = path.join(this.storagePath, "memory.json");
        fs.writeFileSync(filePath, JSON.stringify(data, null, 4), "utf-8");
    } catch (err) {
        console.error("Memory save failed:", err.message);
    }
};

Memory.prototype._load = function () {
    try {
        var filePath = path.join(this.storagePath, "memory.json");
        if (fs.existsSync(filePath)) {
            var raw = fs.readFileSync(filePath, "utf-8");
            var data = JSON.parse(raw);
            this.conversations = data.conversations || [];
            this.tasks = data.tasks || [];
            this.knowledge = data.knowledge || {};
            this.toolHistory = data.toolHistory || [];
        }
    } catch (err) {
        console.error("Memory load failed:", err.message);
    }
};

Memory.prototype.reset = function () {
    this.conversations = [];
    this.tasks = [];
    this.knowledge = {};
    this.toolHistory = [];
    this._save();
};

module.exports = Memory;