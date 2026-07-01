"use strict";

var Tool = require("./tool");
var fs = require("fs");

function ReadFileTool(options) {
    Tool.call(this, {
        name: "read_file",
        description: "Read the contents of a file from the filesystem.",
        parameters: {
            file_path: {
                type: "string",
                description: "The absolute path to the file to read",
                required: true
            },
            offset: {
                type: "number",
                description: "Optional line offset to start reading from"
            },
            limit: {
                type: "number",
                description: "Optional number of lines to read"
            }
        },
        config: options || {}
    });
}

ReadFileTool.prototype = Object.create(Tool.prototype);
ReadFileTool.prototype.constructor = ReadFileTool;

ReadFileTool.prototype.execute = function (args) {
    return new Promise(function (resolve, reject) {
        try {
            var filePath = args.file_path;

            if (!fs.existsSync(filePath)) {
                resolve({
                    success: false,
                    data: null,
                    error: "File not found: " + filePath
                });
                return;
            }

            var content = fs.readFileSync(filePath, "utf-8");
            var lines = content.split("\n");

            if (args.offset !== undefined) {
                var startIdx = Math.max(0, args.offset - 1);
                var endIdx = args.limit ? startIdx + args.limit : lines.length;
                lines = lines.slice(startIdx, endIdx);

                // Prepend line numbers
                var numbered = [];
                for (var i = 0; i < lines.length; i++) {
                    numbered.push((startIdx + i + 1) + " | " + lines[i]);
                }
                content = numbered.join("\n");
            }

            resolve({
                success: true,
                data: {
                    file_path: filePath,
                    content: content,
                    line_count: lines.length,
                    size_bytes: Buffer.byteLength(content, "utf-8")
                }
            });
        } catch (err) {
            resolve({
                success: false,
                data: null,
                error: err.message
            });
        }
    });
};

module.exports = ReadFileTool;