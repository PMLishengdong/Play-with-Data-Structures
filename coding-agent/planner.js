"use strict";

/**
 * Task planner for breaking down user requests into actionable sub-tasks.
 * Manages task dependencies, status tracking, and plan generation.
 */
function Planner(options) {
    options = options || {};

    this.tasks = [];
    this.currentPlan = null;
    this.onTaskUpdate = options.onTaskUpdate || null;
}

// ---- Plan Management ----

Planner.prototype.createPlan = function (goal, tasks) {
    var plan = {
        id: "plan_" + Date.now(),
        goal: goal,
        tasks: [],
        createdAt: Date.now(),
        status: "active"
    };

    if (tasks && tasks.length > 0) {
        for (var i = 0; i < tasks.length; i++) {
            var task = this._createTask(tasks[i], plan.id);
            plan.tasks.push(task);
            this.tasks.push(task);
        }
    }

    this.currentPlan = plan;
    return plan;
};

Planner.prototype.updatePlan = function (planId, tasks) {
    var plan = this.getPlan(planId);
    if (!plan) {
        return null;
    }

    // Remove old tasks for this plan
    this.tasks = this.tasks.filter(function (t) {
        return t.planId !== planId;
    });

    plan.tasks = [];
    for (var i = 0; i < tasks.length; i++) {
        var task = this._createTask(tasks[i], planId);
        plan.tasks.push(task);
        this.tasks.push(task);
    }

    return plan;
};

Planner.prototype.getPlan = function (planId) {
    if (this.currentPlan && this.currentPlan.id === planId) {
        return this.currentPlan;
    }
    // Search through all plans if we had multiple stored
    return null;
};

// ---- Task Operations ----

Planner.prototype.addTask = function (taskInfo, planId) {
    planId = planId || (this.currentPlan ? this.currentPlan.id : null);
    var task = this._createTask(taskInfo, planId);
    this.tasks.push(task);

    if (this.currentPlan && this.currentPlan.id === planId) {
        this.currentPlan.tasks.push(task);
    }

    return task;
};

Planner.prototype.updateTask = function (taskId, updates) {
    for (var i = 0; i < this.tasks.length; i++) {
        if (this.tasks[i].id === taskId) {
            var task = this.tasks[i];
            var keys = Object.keys(updates);
            for (var j = 0; j < keys.length; j++) {
                var key = keys[j];
                if (key !== "id") {
                    task[key] = updates[key];
                }
            }
            task.updatedAt = Date.now();

            if (this.onTaskUpdate) {
                this.onTaskUpdate(task);
            }

            return task;
        }
    }
    return null;
};

Planner.prototype.getNextTask = function () {
    // Find the first pending task whose dependencies are all completed
    for (var i = 0; i < this.tasks.length; i++) {
        var task = this.tasks[i];
        if (task.status !== "pending") {
            continue;
        }
        if (this._dependenciesMet(task)) {
            return task;
        }
    }
    return null;
};

Planner.prototype.getTasksByStatus = function (status) {
    return this.tasks.filter(function (t) {
        return t.status === status;
    });
};

Planner.prototype.getAllTasks = function () {
    return this.tasks.slice();
};

Planner.prototype.getPendingCount = function () {
    return this.getTasksByStatus("pending").length;
};

Planner.prototype.getCompletedCount = function () {
    return this.getTasksByStatus("completed").length;
};

Planner.prototype.isComplete = function () {
    return this.getPendingCount() === 0;
};

// ---- Plan Summary ----

Planner.prototype.formatPlan = function () {
    if (!this.currentPlan) {
        return "No active plan.";
    }

    var lines = [];
    lines.push("## Plan: " + this.currentPlan.goal);
    lines.push("");

    var completed = 0;
    var inProgress = 0;
    var pending = 0;

    for (var i = 0; i < this.tasks.length; i++) {
        var t = this.tasks[i];
        var statusIcon = "[ ]";
        if (t.status === "completed") {
            statusIcon = "[x]";
            completed++;
        } else if (t.status === "in_progress") {
            statusIcon = "[~]";
            inProgress++;
        } else if (t.status === "failed") {
            statusIcon = "[!]";
        } else {
            pending++;
        }

        var depInfo = "";
        if (t.dependsOn && t.dependsOn.length > 0) {
            depInfo = " (depends on: " + t.dependsOn.join(", ") + ")";
        }

        lines.push(statusIcon + " " + t.description + depInfo);
    }

    lines.push("");
    lines.push("Progress: " + completed + "/" + this.tasks.length + " tasks completed");

    return lines.join("\n");
};

// ---- Internal ----

Planner.prototype._createTask = function (taskInfo, planId) {
    var now = Date.now();
    return {
        id: taskInfo.id || "t" + now + "_" + Math.random().toString(36).substr(2, 6),
        planId: planId || "",
        description: taskInfo.description,
        status: taskInfo.status || "pending",
        dependsOn: taskInfo.dependsOn || [],
        result: taskInfo.result || null,
        error: taskInfo.error || null,
        createdAt: now,
        updatedAt: now,
        metadata: taskInfo.metadata || {}
    };
};

Planner.prototype._dependenciesMet = function (task) {
    if (!task.dependsOn || task.dependsOn.length === 0) {
        return true;
    }
    for (var i = 0; i < task.dependsOn.length; i++) {
        var depId = task.dependsOn[i];
        var depTask = this._findTask(depId);
        if (!depTask || depTask.status !== "completed") {
            return false;
        }
    }
    return true;
};

Planner.prototype._findTask = function (taskId) {
    for (var i = 0; i < this.tasks.length; i++) {
        if (this.tasks[i].id === taskId) {
            return this.tasks[i];
        }
    }
    return null;
};

// ---- Serialization ----

Planner.prototype.toJSON = function () {
    return {
        tasks: this.tasks,
        currentPlan: this.currentPlan
    };
};

Planner.fromJSON = function (data) {
    var planner = new Planner();
    planner.tasks = data.tasks || [];
    planner.currentPlan = data.currentPlan || null;
    return planner;
};

module.exports = Planner;