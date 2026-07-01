"use strict";

var CodingAgent = require("./index").CodingAgent;

/**
 * Example usage of the Coding Agent.
 *
 * Set environment variables:
 *   export LLM_API_KEY="your-api-key"
 *   export LLM_BASE_URL="https://api.openai.com/v1"
 *   export LLM_MODEL="gpt-4"
 *
 * Or create an agent.config.json file in the project root.
 */

// Create agent with optional custom system prompt
var agent = new CodingAgent({
    config: {
        llm: {
            apiKey: process.env["LLM_API_KEY"] || "",
            baseURL: process.env["LLM_BASE_URL"] || "https://api.openai.com/v1",
            model: process.env["LLM_MODEL"] || "gpt-4",
            maxTokens: 4096,
            temperature: 0.3
        },
        agent: {
            maxIterations: 25,
            workingDirectory: process.cwd()
        }
    }
});

// Listen for events
agent.on("start", function (info) {
    console.log("\n--- Agent started ---");
    console.log("Input: " + info.input.substring(0, 80) + "...\n");
});

agent.on("tool_call", function (info) {
    console.log("  => Calling tool: " + info.name);
});

agent.on("tool_result", function (info) {
    var status = info.success ? "OK" : "FAILED";
    console.log("  <= Tool result: " + info.name + " [" + status + "]");
});

agent.on("progress", function (info) {
    console.log("  [Iteration " + info.iteration + " complete]\n");
});

agent.on("complete", function (data) {
    console.log("\n--- Agent complete ---");
    console.log("Iterations: " + data.iterations);
    if (data.summary) {
        console.log("Summary: " + data.summary.substring(0, 200));
    }
});

agent.on("error", function (info) {
    console.error("  [Error] " + info.error);
});

// Run the agent
var userRequest = process.argv[2] || "Write a simple Node.js script that prints 'Hello, World!' to the console.";

console.log("User request: " + userRequest);
console.log("");

agent.run(userRequest).then(function (result) {
    if (result.success) {
        console.log("\nFinal result: SUCCESS");
        process.exit(0);
    } else {
        console.error("\nFinal result: FAILED - " + result.error);
        process.exit(1);
    }
}).catch(function (err) {
    console.error("\nFatal error: " + err.message);
    process.exit(1);
});