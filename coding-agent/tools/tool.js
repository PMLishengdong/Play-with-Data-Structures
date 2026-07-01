"use strict";

/**
 * Base Tool class that all tools must extend.
 * Each tool has a name, description, parameter schema, and execute method.
 */
function Tool(options) {
    options = options || {};

    this.name = options.name || "unnamed_tool";
    this.description = options.description || "";
    this.parameters = options.parameters || {};
    this.config = options.config || {};
}

/**
 * Validate the arguments against the parameter schema.
 * Returns { valid: boolean, errors: string[] }
 */
Tool.prototype.validate = function (args) {
    var errors = [];
    args = args || {};

    var paramNames = Object.keys(this.parameters);

    for (var i = 0; i < paramNames.length; i++) {
        var name = paramNames[i];
        var param = this.parameters[name];

        // Check required
        if (param.required && (args[name] === undefined || args[name] === null)) {
            errors.push("Missing required parameter: '" + name + "'");
            continue;
        }

        // If value is provided, check type
        if (args[name] !== undefined && args[name] !== null) {
            if (param.type === "string" && typeof args[name] !== "string") {
                errors.push("Parameter '" + name + "' should be a string");
            } else if (param.type === "number" && typeof args[name] !== "number") {
                errors.push("Parameter '" + name + "' should be a number");
            } else if (param.type === "array" && !Array.isArray(args[name])) {
                errors.push("Parameter '" + name + "' should be an array");
            } else if (param.type === "boolean" && typeof args[name] !== "boolean") {
                errors.push("Parameter '" + name + "' should be a boolean");
            }
        }
    }

    return {
        valid: errors.length === 0,
        errors: errors
    };
};

/**
 * Execute the tool. Subclasses must override this.
 * Returns a promise that resolves to { success: boolean, data: any, error?: string }
 */
Tool.prototype.execute = function (args) {
    throw new Error("Tool '" + this.name + "' does not implement execute()");
};

/**
 * Get the tool definition in OpenAI function-calling format.
 */
Tool.prototype.getDefinition = function () {
    var properties = {};
    var required = [];
    var paramNames = Object.keys(this.parameters);

    for (var i = 0; i < paramNames.length; i++) {
        var name = paramNames[i];
        var param = this.parameters[name];
        var prop = {
            type: param.type || "string",
            description: param.description || ""
        };
        if (param.enum) {
            prop.enum = param.enum;
        }
        properties[name] = prop;
        if (param.required) {
            required.push(name);
        }
    }

    return {
        type: "function",
        function: {
            name: this.name,
            description: this.description,
            parameters: {
                type: "object",
                properties: properties,
                required: required.length > 0 ? required : undefined
            }
        }
    };
};

module.exports = Tool;