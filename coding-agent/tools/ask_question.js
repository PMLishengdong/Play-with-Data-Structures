"use strict";

var Tool = require("./tool");
var readline = require("readline");

/**
 * Ask the user a question and wait for their response.
 * This is the tool for interactive clarification.
 */
function AskQuestionTool(options) {
    Tool.call(this, {
        name: "ask_question",
        description: "Ask the user a question when you need clarification, additional information, or a decision.",
        parameters: {
            question: {
                type: "string",
                description: "The question to ask the user",
                required: true
            },
            options: {
                type: "string",
                description: "Comma-separated list of options if presenting choices (optional)"
            }
        },
        config: options || {}
    });
}

AskQuestionTool.prototype = Object.create(Tool.prototype);
AskQuestionTool.prototype.constructor = AskQuestionTool;

AskQuestionTool.prototype.execute = function (args) {
    var self = this;

    return new Promise(function (resolve, reject) {
        var questionText = args.question;

        if (args.options) {
            questionText += "\nOptions: " + args.options;
        }

        questionText += "\n> ";

        // Try to use process.stdin if available interactively
        var rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });

        rl.question(questionText, function (answer) {
            rl.close();
            resolve({
                success: true,
                data: {
                    answer: answer.trim(),
                    question: args.question
                }
            });
        });

        // Timeout for non-interactive environments
        setTimeout(function () {
            rl.close();
            resolve({
                success: false,
                data: null,
                error: "No response received within timeout. Running in non-interactive mode."
            });
        }, 30000);
    });
};

module.exports = AskQuestionTool;