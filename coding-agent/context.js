"use strict";

/**
 * Context manager for the agent.
 * Tracks the current working state including:
 * - current working directory and file
 * - active task
 * - relevant code snippets
 * - build status, error context
 * - conversation summary
 */
function ContextManager(options) {
    options = options || {};

    this.workingDirectory = options.workingDirectory || process.cwd();
    this.currentFile = null;
    this.currentTask = null;
    this.activeTaskId = null;
    this.sessionId = "session_" + Date.now();
    this.startTime = Date.now();

    // Stack of recent contexts for backtracking
    this.historyStack = [];

    // Key-value context store
    this.store = {};

    // Track recently mentioned files
    this.recentFiles = [];

    // Track the current tool call chain
    this.callChain = [];
}

// ---- Directory & File Tracking ----

ContextManager.prototype.setWorkingDirectory = function (dir) {
    this._pushHistory();
    this.workingDirectory = dir;
};

ContextManager.prototype.setCurrentFile = function (filePath) {
    this.currentFile = filePath;
    this._addRecentFile(filePath);
};

ContextManager.prototype._addRecentFile = function (filePath) {
    // Remove if already exists
    var idx = this.recentFiles.indexOf(filePath);
    if (idx !== -1) {
        this.recentFiles.splice(idx, 1);
    }
    this.recentFiles.push(filePath);
    // Keep only last 10
    if (this.recentFiles.length > 10) {
        this.recentFiles.shift();
    }
};

// ---- Task Tracking ----

ContextManager.prototype.setActiveTask = function (taskId, description) {
    this.activeTaskId = taskId;
    this.currentTask = {
        id: taskId,
        description: description,
        startedAt: Date.now()
    };
};

ContextManager.prototype.clearActiveTask = function () {
    this.activeTaskId = null;
    this.currentTask = null;
};

// ---- Generic Store ----

ContextManager.prototype.set = function (key, value) {
    this.store[key] = value;
};

ContextManager.prototype.get = function (key) {
    return this.store[key];
};

// ---- Call Chain ----

ContextManager.prototype.pushCall = function (callInfo) {
    this.callChain.push(callInfo);
};

ContextManager.prototype.popCall = function () {
    return this.callChain.pop();
};

ContextManager.prototype.getCallChain = function () {
    return this.callChain.slice();
};

// ---- History Stack (for undo/backtrack) ----

ContextManager.prototype._pushHistory = function () {
    this.historyStack.push({
        workingDirectory: this.workingDirectory,
        currentFile: this.currentFile,
        activeTaskId: this.activeTaskId,
        timestamp: Date.now()
    });
    if (this.historyStack.length > 20) {
        this.historyStack.shift();
    }
};

ContextManager.prototype.backtrack = function () {
    if (this.historyStack.length === 0) {
        return false;
    }
    var snapshot = this.historyStack.pop();
    this.workingDirectory = snapshot.workingDirectory;
    this.currentFile = snapshot.currentFile;
    this.activeTaskId = snapshot.activeTaskId;
    return true;
};

// ---- Build Context for LLM Prompt ----

ContextManager.prototype.buildContextSummary = function () {
    var lines = [];

    lines.push("=== Session Context ===");
    lines.push("Session ID: " + this.sessionId);
    lines.push("Working Directory: " + this.workingDirectory);
    lines.push("Elapsed: " + this._formatElapsed());

    if (this.currentFile) {
        lines.push("Current File: " + this.currentFile);
    }

    if (this.currentTask) {
        lines.push("Active Task: " + this.currentTask.description + " (" + this.currentTask.id + ")");
    }

    if (this.recentFiles.length > 0) {
        lines.push("Recent Files:");
        for (var i = 0; i < this.recentFiles.length; i++) {
            lines.push("  - " + this.recentFiles[i]);
        }
    }

    return lines.join("\n");
};

ContextManager.prototype.buildSystemPrompt = function (agentRole) {
    agentRole = agentRole || "You are an AI coding assistant that writes and modifies code.";
    var prompt = agentRole + "\n\n";

    prompt += "## Current State\n";
    prompt += "- Working directory: " + this.workingDirectory + "\n";
    if (this.currentFile) {
        prompt += "- Currently working on file: " + this.currentFile + "\n";
    }
    if (this.currentTask) {
        prompt += "- Active task: " + this.currentTask.description + "\n";
    }

    prompt += "\n## Rules\n";
    prompt += "- You can use tools to read, write, and edit files.\n";
    prompt += "- You can use the ask_question tool to ask the user for clarification.\n";
    prompt += "- Plan your approach before writing code.\n";
    prompt += "- After completing a task, summarize what was done.\n";

    return prompt;
};

ContextManager.prototype._formatElapsed = function () {
    var elapsed = Math.floor((Date.now() - this.startTime) / 1000);
    if (elapsed < 60) {
        return elapsed + "s";
    } else if (elapsed < 3600) {
        return Math.floor(elapsed / 60) + "m " + (elapsed % 60) + "s";
    } else {
        var h = Math.floor(elapsed / 3600);
        var m = Math.floor((elapsed % 3600) / 60);
        return h + "h " + m + "m";
    }
};

module.exports = ContextManager;