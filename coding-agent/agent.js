"use strict";

var EventEmitter = require("events");
var LLMClient = require("./llm");
var Memory = require("./memory");
var ContextManager = require("./context");
var Planner = require("./planner");
var ToolRegistry = require("./tools/index").ToolRegistry;

/**
 * CodingAgent - the core agent that orchestrates everything.
 *
 * Agent Loop:
 *   1. Accept user input
 *   2. Store in memory
 *   3. Update context
 *   4. Plan / refine tasks
 *   5. Build prompt with context
 *   6. Call LLM
 *   7. Parse response (text + tool calls)
 *   8. Execute tools
 *   9. Store results in memory
 *   10. Repeat until done or max iterations reached
 */
function CodingAgent(options) {
    EventEmitter.call(this);

    options = options || {};

    this.config = options.config || {};
    this.systemPrompt = options.systemPrompt || this._defaultSystemPrompt();

    // Core subsystems
    this.llm = new LLMClient(this.config.llm || {});
    this.memory = new Memory(this.config.memory || {});
    this.context = new ContextManager(this.config.agent || {});
    this.planner = new Planner({
        onTaskUpdate: this._onTaskUpdate.bind(this)
    });
    this.tools = new ToolRegistry(this.config.tools || {});

    // Agent state
    this.running = false;
    this.iterationCount = 0;
    this.maxIterations = (this.config.agent && this.config.agent.maxIterations) || 25;
    this.maxToolRetries = (this.config.agent && this.config.agent.maxToolRetries) || 3;

    // Bind event handlers so memory and tool history are tracked
    this._setupEventHandlers();
}

CodingAgent.prototype = Object.create(EventEmitter.prototype);
CodingAgent.prototype.constructor = CodingAgent;

// ---- Public API ----

/**
 * Process a user message and run the agent loop.
 * @param {string} userInput - The user's request
 * @param {Object} taskInfo - Optional task metadata
 * @returns {Promise<Object>} Final result summary
 */
CodingAgent.prototype.run = function (userInput, taskInfo) {
    var self = this;

    if (this.running) {
        return Promise.resolve({
            success: false,
            error: "Agent is already running"
        });
    }

    this.running = true;
    this.iterationCount = 0;

    // Store user input in memory
    this.memory.addMessage("user", userInput, taskInfo);

    // Create a plan if task info is provided
    if (taskInfo && taskInfo.tasks) {
        this.planner.createPlan(userInput, taskInfo.tasks);
    }

    // Emit start event
    this.emit("start", { input: userInput });

    return this._runLoop(userInput);
};

/**
 * Reset the agent state.
 */
CodingAgent.prototype.reset = function () {
    this.memory.reset();
    this.planner = new Planner();
    this.context = new ContextManager(this.config.agent || {});
    this.iterationCount = 0;
    this.running = false;
    this.emit("reset");
};

/**
 * Get all tool definitions for LLM function calling.
 */
CodingAgent.prototype.getToolDefinitions = function () {
    return this.tools.getDefinitions();
};

// ---- Internal Agent Loop ----

CodingAgent.prototype._runLoop = function (userInput) {
    var self = this;

    function loop() {
        if (!self.running) {
            return Promise.resolve({
                success: false,
                error: "Agent stopped"
            });
        }

        if (self.iterationCount >= self.maxIterations) {
            self.running = false;
            var timeoutResult = {
                success: true,
                data: {
                    summary: "Agent reached maximum iterations (" + self.maxIterations + ")",
                    iterations: self.iterationCount,
                    completed: self.planner.getCompletedCount(),
                    total: self.planner.getAllTasks().length
                }
            };
            self.emit("complete", timeoutResult.data);
            return Promise.resolve(timeoutResult);
        }

        self.iterationCount++;
        self.emit("iteration", { count: self.iterationCount });

        // Build messages for LLM
        var messages = self._buildMessages(userInput);
        var tools = self.getToolDefinitions();

        // Call LLM
        return self.llm.chat(messages, tools).then(function (llmResult) {
            if (!llmResult.success) {
                self.emit("error", { error: llmResult.error });
                self.running = false;
                return {
                    success: false,
                    error: llmResult.error
                };
            }

            var parsed = self.llm.parseResponse(llmResult);

            // Store assistant response in memory
            if (parsed.content) {
                self.memory.addMessage("assistant", parsed.content);
            }

            // If no tool calls, we're done
            if (!parsed.tool_calls || parsed.tool_calls.length === 0) {
                self.running = false;
                var result = {
                    success: true,
                    data: {
                        summary: parsed.content,
                        iterations: self.iterationCount,
                        completed: self.planner.getCompletedCount(),
                        total: self.planner.getAllTasks().length
                    }
                };
                self.emit("complete", result.data);
                return result;
            }

            // Execute tool calls
            return self._executeToolCalls(llmResult, parsed.tool_calls).then(function (toolResults) {
                // Store tool results in memory
                self.memory.addMessage("assistant", "[Tool results recorded]");

                // Check if we should continue
                var shouldContinue = true;

                // Emit progress
                self.emit("progress", {
                    iteration: self.iterationCount,
                    tool_calls: parsed.tool_calls.length,
                    results: toolResults.length
                });

                // Continue the loop
                return loop();
            });
        }).catch(function (err) {
            self.emit("error", { error: err.message });
            self.running = false;
            return {
                success: false,
                error: err.message
            };
        });
    }

    return loop();
};

CodingAgent.prototype._executeToolCalls = function (llmResult, toolCalls) {
    var self = this;

    // Add assistant message with tool calls to conversation
    var assistantMsg = {
        role: "assistant",
        content: null
    };

    var choice = llmResult.data.choices && llmResult.data.choices[0];
    if (choice && choice.message) {
        assistantMsg.content = choice.message.content || null;
    }

    // Build tool_call entries for the assistant message
    assistantMsg.tool_calls = [];
    for (var i = 0; i < toolCalls.length; i++) {
        var tc = toolCalls[i];
        assistantMsg.tool_calls.push({
            id: tc.id,
            type: "function",
            function: {
                name: tc.function.name,
                arguments: JSON.stringify(tc.function.arguments)
            }
        });
    }

    // We need to store this in memory differently - let's track it
    self._lastAssistantToolMessage = assistantMsg;

    var results = [];
    var chain = Promise.resolve();

    for (var j = 0; j < toolCalls.length; j++) {
        chain = chain.then(function (index) {
            return function () {
                return self._executeSingleToolCall(llmResult, toolCalls, index, results);
            };
        }(j));
    }

    return chain.then(function () {
        // After all tool calls, add assistant message and tool results to memory's conversation
        // via the raw conversation array
        self.memory.conversations.push(assistantMsg);
        for (var k = 0; k < results.length; k++) {
            self.memory.conversations.push(results[k].toolMessage);
        }

        // Trim if needed
        if (self.memory.conversations.length > self.memory.maxHistoryLength) {
            var excess = self.memory.conversations.length - self.memory.maxHistoryLength;
            self.memory.conversations.splice(0, excess);
        }

        self.memory._save();

        return results;
    });
};

CodingAgent.prototype._executeSingleToolCall = function (llmResult, toolCalls, index, results) {
    var self = this;
    var tc = toolCalls[index];
    var toolName = tc.function.name;
    var toolArgs = tc.function.arguments;

    self.emit("tool_call", {
        name: toolName,
        args: toolArgs,
        index: index
    });

    // Update context
    self.context.pushCall({
        tool: toolName,
        args: toolArgs,
        timestamp: Date.now()
    });

    // Execute with retries
    return self._executeWithRetry(toolName, toolArgs, 0).then(function (execResult) {
        self.context.popCall();

        // Store in memory
        self.memory.addToolCall(toolName, toolArgs, execResult, execResult.error);

        // Build tool result message
        var toolMessage = self.llm.buildToolResultMessage(
            tc.id,
            toolName,
            execResult
        );

        results.push({
            toolCallId: tc.id,
            toolName: toolName,
            result: execResult,
            toolMessage: toolMessage
        });

        self.emit("tool_result", {
            name: toolName,
            success: execResult.success,
            index: index
        });
    });
};

CodingAgent.prototype._executeWithRetry = function (toolName, args, attempt) {
    var self = this;

    return self.tools.executeTool(toolName, args).then(function (result) {
        if (!result.success && attempt < self.maxToolRetries - 1) {
            return self._executeWithRetry(toolName, args, attempt + 1);
        }
        return result;
    });
};

// ---- Message Building ----

CodingAgent.prototype._buildMessages = function (userInput) {
    var messages = [];

    // System message
    var systemPrompt = this.context.buildSystemPrompt(this.systemPrompt);

    // Add plan info if available
    var planStr = this.planner.formatPlan();
    if (planStr && planStr !== "No active plan.") {
        systemPrompt += "\n\n## Current Plan\n" + planStr;
    }

    // Add recent tool history
    var recentTools = this.memory.getRecentToolCalls(5);
    if (recentTools.length > 0) {
        systemPrompt += "\n\n## Recent Tool Calls\n";
        for (var i = 0; i < recentTools.length; i++) {
            var rt = recentTools[i];
            systemPrompt += "- " + rt.tool + "(" + JSON.stringify(rt.args) + ") -> " +
                (rt.success !== false ? "success" : "failed: " + (rt.error || "unknown")) + "\n";
        }
    }

    messages.push({
        role: "system",
        content: systemPrompt
    });

    // Conversation history from memory
    var history = this.memory.getMessages(20);
    for (var j = 0; j < history.length; j++) {
        messages.push({
            role: history[j].role,
            content: history[j].content
        });
    }

    return messages;
};

// ---- Event Handlers ----

CodingAgent.prototype._setupEventHandlers = function () {
    var self = this;

    this.memory.on("message", function (msg) {
        self.emit("memory_message", msg);
    });
};

CodingAgent.prototype._onTaskUpdate = function (task) {
    this.emit("task_update", task);
};

// ---- Defaults ----

CodingAgent.prototype._defaultSystemPrompt = function () {
    return "You are an AI coding assistant that writes and modifies code. " +
        "You have access to tools that let you read, write, and edit files, " +
        "search the codebase, execute commands, and ask the user questions.\n\n" +
        "Guidelines:\n" +
        "- Plan your approach before writing code.\n" +
        "- Use tools to gather information about the codebase before making changes.\n" +
        "- When you need clarification, use ask_question.\n" +
        "- After completing a task, summarize what was done.\n" +
        "- Write clean, maintainable code.";
};

module.exports = CodingAgent;