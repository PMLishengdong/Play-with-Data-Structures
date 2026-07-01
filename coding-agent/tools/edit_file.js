"use strict";

var Tool = require("./tool");
var fs = require("fs");

function EditFileTool(options) {
    Tool.call(this, {
        name: "edit_file",
        description: "Edit a file by replacing text. Uses exact string replacement.",
        parameters: {
            file_path: {
                type: "string",
                description: "The absolute path to the file to edit",
                required: true
            },
            old_string: {
                type: "string",
                description: "The exact text to find and replace",
                required: true
            },
            new_string: {
                type: "string",
                description: "The text to replace it with",
                required: true
            },
            replace_all: {
                type: "boolean",
                description: "Replace all occurrences (default: false)"
            }
        },
        config: options || {}
    });
}

EditFileTool.prototype = Object.create(Tool.prototype);
EditFileTool.prototype.constructor = EditFileTool;

EditFileTool.prototype.execute = function (args) {
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

            if (args.replace_all) {
                // Split by old_string and join with new_string
                var parts = content.split(args.old_string);
                if (parts.length === 1) {
                    resolve({
                        success: false,
                        data: null,
                        error: "String not found in file: " + args.old_string.substring(0, 50)
                    });
                    return;
                }
                content = parts.join(args.new_string);
            } else {
                // Replace only the first occurrence
                var idx = content.indexOf(args.old_string);
                if (idx === -1) {
                    resolve({
                        success: false,
                        data: null,
                        error: "String not found in file: " + args.old_string.substring(0, 50)
                    });
                    return;
                }
                content = content.substring(0, idx) + args.new_string + content.substring(idx + args.old_string.length);
            }

            fs.writeFileSync(filePath, content, "utf-8");

            resolve({
                success: true,
                data: {
                    file_path: filePath,
                    replaced: args.replace_all ? "all" : "first"
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

module.exports = EditFileTool;