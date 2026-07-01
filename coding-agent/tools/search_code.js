"use strict";

var Tool = require("./tool");
var fs = require("fs");
var path = require("path");

function SearchCodeTool(options) {
    Tool.call(this, {
        name: "search_code",
        description: "Search for files or text patterns in the codebase using glob and grep.",
        parameters: {
            pattern: {
                type: "string",
                description: "Search pattern (filename glob or text regex)",
                required: true
            },
            base_path: {
                type: "string",
                description: "Base directory to search in (default: working directory)"
            },
            search_type: {
                type: "string",
                description: "Type of search: 'file' for filenames, 'text' for file contents (default: 'file')",
                enum: ["file", "text"]
            },
            file_glob: {
                type: "string",
                description: "File glob filter when searching text (e.g. '*.js')"
            },
            max_results: {
                type: "number",
                description: "Maximum number of results (default: 30)"
            }
        },
        config: options || {}
    });
}

SearchCodeTool.prototype = Object.create(Tool.prototype);
SearchCodeTool.prototype.constructor = SearchCodeTool;

SearchCodeTool.prototype.execute = function (args) {
    var self = this;

    return new Promise(function (resolve, reject) {
        try {
            var basePath = args.base_path || process.cwd();
            var maxResults = args.max_results || 30;
            var results = [];

            if (args.search_type === "text") {
                results = self._grepText(basePath, args.pattern, args.file_glob, maxResults);
            } else {
                results = self._globFiles(basePath, args.pattern, maxResults);
            }

            resolve({
                success: true,
                data: {
                    pattern: args.pattern,
                    search_type: args.search_type || "file",
                    results: results,
                    result_count: results.length
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

SearchCodeTool.prototype._globFiles = function (basePath, pattern, maxResults) {
    var results = [];
    var self = this;

    function walkDir(dirPath) {
        if (results.length >= maxResults) {
            return;
        }

        var entries;
        try {
            entries = fs.readdirSync(dirPath, { withFileTypes: true });
        } catch (_e) {
            return;
        }

        for (var i = 0; i < entries.length; i++) {
            if (results.length >= maxResults) {
                break;
            }

            var entry = entries[i];
            var fullPath = path.join(dirPath, entry.name);

            // Skip node_modules, .git, etc.
            if (entry.name === "node_modules" || entry.name === ".git" || entry.name === ".agent-memory") {
                continue;
            }

            if (entry.isDirectory()) {
                walkDir(fullPath);
            } else if (self._matchesGlob(entry.name, pattern)) {
                results.push(fullPath);
            }
        }
    }

    walkDir(basePath);
    return results;
};

SearchCodeTool.prototype._grepText = function (basePath, pattern, fileGlob, maxResults) {
    var results = [];
    var self = this;

    function walkDir(dirPath) {
        if (results.length >= maxResults) {
            return;
        }

        var entries;
        try {
            entries = fs.readdirSync(dirPath, { withFileTypes: true });
        } catch (_e) {
            return;
        }

        for (var i = 0; i < entries.length; i++) {
            if (results.length >= maxResults) {
                break;
            }

            var entry = entries[i];
            var fullPath = path.join(dirPath, entry.name);

            if (entry.name === "node_modules" || entry.name === ".git" || entry.name === ".agent-memory") {
                continue;
            }

            if (entry.isDirectory()) {
                walkDir(fullPath);
            } else if (!fileGlob || self._matchesGlob(entry.name, fileGlob)) {
                try {
                    var content = fs.readFileSync(fullPath, "utf-8");
                    if (content.indexOf(pattern) !== -1) {
                        results.push(fullPath);
                    }
                } catch (_e) {
                    // Skip binary or unreadable files
                }
            }
        }
    }

    walkDir(basePath);
    return results;
};

SearchCodeTool.prototype._matchesGlob = function (filename, pattern) {
    // Simple glob matching: supports * and ?
    var regexStr = "";
    for (var i = 0; i < pattern.length; i++) {
        var ch = pattern[i];
        if (ch === "*") {
            regexStr += ".*";
        } else if (ch === "?") {
            regexStr += ".";
        } else {
            // Escape special regex chars
            var specialChars = ".+^${}()|[]\\";
            if (specialChars.indexOf(ch) !== -1) {
                regexStr += "\\" + ch;
            } else {
                regexStr += ch;
            }
        }
    }

    var regex = new RegExp("^" + regexStr + "$");
    return regex.test(filename);
};

module.exports = SearchCodeTool;