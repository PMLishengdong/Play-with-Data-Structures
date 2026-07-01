"use strict";

var path = require("path");
var CodingAgent = require("./agent");
var configLoader = require("./config");

/**
 * Create and configure a new CodingAgent instance.
 *
 * @param {Object} options - Configuration options
 * @param {string} options.configFile - Path to a JSON config file
 * @param {Object} options.config - Inline config overrides
 * @param {string} options.systemPrompt - Custom system prompt
 * @returns {CodingAgent} Configured agent instance
 */
function createAgent(options) {
    options = options || {};

    // Load configuration
    var config = options.config || {};
    if (options.configFile) {
        config = configLoader.loadConfig(options.configFile);
    } else {
        config = configLoader.loadConfig();
    }

    // Merge inline overrides
    if (options.config) {
        var keys = Object.keys(options.config);
        for (var i = 0; i < keys.length; i++) {
            var key = keys[i];
            if (typeof options.config[key] === "object" && !Array.isArray(options.config[key])) {
                if (!config[key]) {
                    config[key] = {};
                }
                var subKeys = Object.keys(options.config[key]);
                for (var j = 0; j < subKeys.length; j++) {
                    config[key][subKeys[j]] = options.config[key][subKeys[j]];
                }
            } else {
                config[key] = options.config[key];
            }
        }
    }

    var agent = new CodingAgent({
        config: config,
        systemPrompt: options.systemPrompt
    });

    return agent;
}

/**
 * Run a command-line interactive session with the agent.
 */
function runCLI(agent) {
    var readline = require("readline");

    var rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: "agent> "
    });

    console.log("Coding Agent CLI");
    console.log("Type 'exit' to quit, 'reset' to reset the agent.");
    console.log("");

    rl.prompt();

    rl.on("line", function (line) {
        var input = line.trim();

        if (input === "exit") {
            rl.close();
            return;
        }

        if (input === "reset") {
            agent.reset();
            console.log("Agent reset.");
            rl.prompt();
            return;
        }

        if (!input) {
            rl.prompt();
            return;
        }

        // Subscribe to events for real-time output
        agent.on("tool_call", function (info) {
            console.log("[Tool] " + info.name + "(" + JSON.stringify(info.args) + ")");
        });

        agent.on("tool_result", function (info) {
            var status = info.success ? "OK" : "FAILED";
            console.log("[Result] " + info.name + " -> " + status);
        });

        agent.on("progress", function (info) {
            console.log("[Iteration " + info.iteration + "] " + info.tool_calls + " tool calls");
        });

        agent.on("error", function (info) {
            console.error("[Error] " + info.error);
        });

        agent.run(input).then(function (result) {
            if (result.success && result.data && result.data.summary) {
                console.log("\n" + result.data.summary + "\n");
            } else if (result.error) {
                console.error("Error: " + result.error);
            }
            rl.prompt();
        }).catch(function (err) {
            console.error("Fatal error: " + err.message);
            rl.prompt();
        });
    }).on("close", function () {
        console.log("Goodbye.");
        process.exit(0);
    });
}

module.exports = {
    CodingAgent: CodingAgent,
    createAgent: createAgent,
    runCLI: runCLI
};

// If run directly, start CLI
if (require.main === module) {
    var agent = createAgent();
    runCLI(agent);
}