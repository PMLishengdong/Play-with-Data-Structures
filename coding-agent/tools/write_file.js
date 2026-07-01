"use strict";

var Tool = require("./tool");
var fs = require("fs");
var path = require("path");

function WriteFileTool(options) {
    Tool.call(this, {
        name: "write_file",
        description: "Write content to a file. Creates the file and any necessary parent directories.",
        parameters: {
            file_path: {
                type: "string",
                description: "The absolute path to the file to write",
                required: true
            },
            content: {
                type: "string",
                description: "The content to write to the file",
                required: true
            }
        },
        config: options || {}
    });
}

WriteFileTool.prototype = Object.create(Tool.prototype);
WriteFileTool.prototype.constructor = WriteFileTool;

WriteFileTool.prototype.execute = function (args) {
    return new Promise(function (resolve, reject) {
        try {
            var filePath = args.file_path;
            var dir = path.dirname(filePath);

            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }

            fs.writeFileSync(filePath, args.content, "utf-8");

            resolve({
                success: true,
                data: {
                    file_path: filePath,
                    size_bytes: Buffer.byteLength(args.content, "utf-8"),
                    line_count: args.content.split("\n").length
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

module.exports = WriteFileTool;