"use strict";

var Tool = require("./tool");
var cp = require("child_process");

function ExecuteCommandTool(options) {
    Tool.call(this, {
        name: "execute_command",
        description: "Execute a shell command and return its output.",
        parameters: {
            command: {
                type: "string",
                description: "The shell command to execute",
                required: true
            },
            cwd: {
                type: "string",
                description: "Working directory for the command (default: current)"
            },
            timeout: {
                type: "number",
                description: "Timeout in milliseconds (default: 30000)"
            }
        },
        config: options || {}
    });
}

ExecuteCommandTool.prototype = Object.create(Tool.prototype);
ExecuteCommandTool.prototype.constructor = ExecuteCommandTool;

ExecuteCommandTool.prototype.execute = function (args) {
    return new Promise(function (resolve, reject) {
        var options = {
            cwd: args.cwd || process.cwd(),
            timeout: args.timeout || 30000,
            maxBuffer: 10 * 1024 * 1024,  // 10MB
            env: Object.assign({}, process.env)
        };

        cp.exec(args.command, options, function (err, stdout, stderr) {
            var result = {
                command: args.command,
                exit_code: err ? (err.code || err.status || -1) : 0,
                stdout: stdout || "",
                stderr: stderr || "",
                error: null
            };

            if (err && err.killed) {
                result.error = "Command timed out after " + (args.timeout || 30000) + "ms";
            } else if (err) {
                result.error = err.message;
            }

            resolve({
                success: !err || err.code === 0,
                data: result,
                error: result.error
            });
        });
    });
};

module.exports = ExecuteCommandTool;