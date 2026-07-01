"use strict";

var http = require("http");
var https = require("https");

/**
 * LLM client for communicating with OpenAI-compatible APIs.
 * Supports streaming, function calling, and configurable parameters.
 */
function LLMClient(options) {
    options = options || {};

    this.apiKey = options.apiKey || "";
    this.baseURL = options.baseURL || "https://api.openai.com/v1";
    this.model = options.model || "gpt-4";
    this.maxTokens = options.maxTokens || 4096;
    this.temperature = options.temperature !== undefined ? options.temperature : 0.3;
    this.timeout = options.timeout || 60000;
}

/**
 * Send a chat completion request.
 * @param {Array} messages - Array of { role, content } objects
 * @param {Array} tools - Optional array of tool definitions
 * @returns {Promise<Object>} LLM response
 */
LLMClient.prototype.chat = function (messages, tools) {
    var self = this;

    return new Promise(function (resolve, reject) {
        var payload = {
            model: self.model,
            messages: messages,
            max_tokens: self.maxTokens,
            temperature: self.temperature
        };

        if (tools && tools.length > 0) {
            payload.tools = tools;
            payload.tool_choice = "auto";
        }

        var body = JSON.stringify(payload);
        var url = self._buildURL("/chat/completions");

        var options = {
            method: "POST",
            hostname: url.hostname,
            port: url.port,
            path: url.pathname,
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + self.apiKey,
                "Content-Length": Buffer.byteLength(body)
            },
            timeout: self.timeout
        };

        var transport = url.protocol === "https:" ? https : http;
        var req = transport.request(options, function (res) {
            var data = "";

            res.on("data", function (chunk) {
                data += chunk;
            });

            res.on("end", function () {
                if (res.statusCode !== 200) {
                    var errMsg = "LLM API error: " + res.statusCode;
                    try {
                        var errData = JSON.parse(data);
                        if (errData.error && errData.error.message) {
                            errMsg += " - " + errData.error.message;
                        }
                    } catch (_e) {
                        errMsg += " - " + data.substring(0, 200);
                    }
                    resolve({
                        success: false,
                        data: null,
                        error: errMsg
                    });
                    return;
                }

                try {
                    var parsed = JSON.parse(data);
                    resolve({
                        success: true,
                        data: parsed
                    });
                } catch (e) {
                    resolve({
                        success: false,
                        data: null,
                        error: "Failed to parse LLM response: " + e.message
                    });
                }
            });
        });

        req.on("error", function (err) {
            resolve({
                success: false,
                data: null,
                error: "LLM request failed: " + err.message
            });
        });

        req.on("timeout", function () {
            req.destroy();
            resolve({
                success: false,
                data: null,
                error: "LLM request timed out after " + self.timeout + "ms"
            });
        });

        req.write(body);
        req.end();
    });
};

/**
 * Extract the response content from the LLM result.
 * Returns { content, tool_calls }
 */
LLMClient.prototype.parseResponse = function (llmResult) {
    if (!llmResult.success || !llmResult.data) {
        return {
            content: null,
            tool_calls: []
        };
    }

    var choice = llmResult.data.choices && llmResult.data.choices[0];
    if (!choice) {
        return {
            content: null,
            tool_calls: []
        };
    }

    var message = choice.message || {};
    var toolCalls = [];

    if (message.tool_calls) {
        for (var i = 0; i < message.tool_calls.length; i++) {
            var tc = message.tool_calls[i];
            if (tc.type === "function") {
                try {
                    var parsedArgs = JSON.parse(tc.function.arguments);
                    toolCalls.push({
                        id: tc.id,
                        type: "function",
                        function: {
                            name: tc.function.name,
                            arguments: parsedArgs
                        }
                    });
                } catch (e) {
                    toolCalls.push({
                        id: tc.id,
                        type: "function",
                        function: {
                            name: tc.function.name,
                            arguments: tc.function.arguments,
                            parseError: e.message
                        }
                    });
                }
            }
        }
    }

    return {
        content: message.content || "",
        tool_calls: toolCalls
    };
};

/**
 * Build a tool call result message for the conversation.
 */
LLMClient.prototype.buildToolResultMessage = function (toolCallId, toolName, result) {
    return {
        role: "tool",
        tool_call_id: toolCallId,
        content: JSON.stringify(result)
    };
};

/**
 * Build the system message for the agent.
 */
LLMClient.prototype.buildSystemMessage = function (systemPrompt) {
    return {
        role: "system",
        content: systemPrompt
    };
};

LLMClient.prototype._buildURL = function (pathname) {
    var base = this.baseURL.replace(/\/+$/, "");
    var fullURL = base + pathname;
    var parsed = require("url").parse(fullURL);
    return parsed;
};

module.exports = LLMClient;