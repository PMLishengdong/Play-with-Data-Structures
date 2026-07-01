// 上下文管理器：组装发送给 LLM 的消息序列，并在超出窗口上限时裁剪
class ContextManager {
    constructor(config) {
        this.config = config || {};
        this.maxTokens = this.config.maxTokens || 8000;
        this.systemPrompt = this.config.systemPrompt || "";
        // 对话消息列表（不含系统提示，系统提示在 build 时前置）
        this.messages = [];
    }

    // 设置系统提示
    setSystemPrompt(prompt) {
        this.systemPrompt = prompt || "";
    }

    // 追加一条消息；若带 toolCalls 则按 OpenAI 格式附加 tool_calls 字段
    addMessage(role, content, toolCalls) {
        const msg = { role: role, content: content || "" };
        if (toolCalls && toolCalls.length) {
            msg.tool_calls = toolCalls.map(function (tc) {
                return {
                    id: tc.id,
                    type: "function",
                    function: {
                        name: tc.name,
                        // 工具参数需序列化为字符串
                        arguments: JSON.stringify(tc.arguments)
                    }
                };
            });
        }
        this.messages.push(msg);
    }

    // 追加工具调用结果消息（OpenAI 要求 role=tool 并带 tool_call_id）
    addToolResult(toolCallId, content) {
        this.messages.push({
            role: "tool",
            tool_call_id: toolCallId,
            content: content || ""
        });
    }

    // 粗略估算文本 token 数：中文按 1 字符 ≈ 1 token，英文按 4 字符 ≈ 1 token
    estimateTokens(text) {
        if (!text) {
            return 0;
        }
        let tokens = 0;
        for (let i = 0; i < text.length; i++) {
            const code = text.charCodeAt(i);
            if (code > 127) {
                tokens += 1;
            } else {
                tokens += 0.25;
            }
        }
        return Math.ceil(tokens);
    }

    // 计算当前全部消息（含系统提示）的 token 估算总量
    countTokens() {
        let total = this.estimateTokens(this.systemPrompt);
        for (let i = 0; i < this.messages.length; i++) {
            const m = this.messages[i];
            total += this.estimateTokens(m.content || "");
            if (m.tool_calls) {
                total += this.estimateTokens(JSON.stringify(m.tool_calls));
            }
        }
        return total;
    }

    // 裁剪上下文：在超限时从最早消息开始丢弃，直至低于上限或仅剩少量消息
    // 注意：此处为简化实现，未严格保证 tool 调用与结果成对保留
    trim() {
        while (this.countTokens() > this.maxTokens && this.messages.length > 2) {
            this.messages.shift();
        }
    }

    // 构建发送给 LLM 的完整消息序列（系统提示在前）
    build() {
        this.trim();
        const result = [];
        if (this.systemPrompt) {
            result.push({ role: "system", content: this.systemPrompt });
        }
        for (let i = 0; i < this.messages.length; i++) {
            result.push(this.messages[i]);
        }
        return result;
    }

    // 清空对话消息（保留系统提示）
    clear() {
        this.messages = [];
    }
}

module.exports = ContextManager;
