/**
 * LLM API Client - 支持 OpenAI 兼容接口
 * 支持：DeepSeek、OpenAI、通义千问等兼容 OpenAI API 格式的 LLM 服务
 */

const axios = require('axios');

class LLMClient {
  /**
   * @param {Object} config
   * @param {string} config.apiKey - API 密钥
   * @param {string} [config.baseURL] - API 基础地址，默认 DeepSeek
   * @param {string} [config.model] - 模型名称
   * @param {number} [config.maxTokens] - 最大输出 token 数
   * @param {number} [config.temperature] - 采样温度
   */
  constructor(config = {}) {
    this.apiKey = config.apiKey || process.env.LLM_API_KEY || 'sk-your-key';
    this.baseURL = config.baseURL || process.env.LLM_BASE_URL || 'https://api.deepseek.com/v1';
    this.model = config.model || process.env.LLM_MODEL || 'deepseek-chat';
    this.maxTokens = config.maxTokens || 4096;
    this.temperature = config.temperature ?? 0.1;
    this.timeout = config.timeout || 120000;

    this.client = axios.create({
      baseURL: this.baseURL,
      timeout: this.timeout,
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * 发送聊天补全请求
   * @param {Array<Object>} messages - 消息列表 [{role, content}]
   * @param {Object} [options] - 可选参数
   * @returns {Promise<Object>} { content, usage, model }
   */
  async chat(messages, options = {}) {
    const payload = {
      model: options.model || this.model,
      messages: messages,
      max_tokens: options.maxTokens || this.maxTokens,
      temperature: options.temperature ?? this.temperature,
      stream: false,
    };

    // 如果启用工具调用，添加 tools 参数
    if (options.tools && options.tools.length > 0) {
      payload.tools = options.tools;
      payload.tool_choice = options.toolChoice || 'auto';
    }

    try {
      const response = await this.client.post('/chat/completions', payload);
      const result = response.data;
      const choice = result.choices[0];

      return {
        content: choice.message.content || '',
        toolCalls: choice.message.tool_calls || null,
        usage: result.usage || null,
        model: result.model,
        finishReason: choice.finish_reason,
      };
    } catch (err) {
      const status = err.response?.status;
      const data = err.response?.data;
      throw new Error(`LLM API error (${status}): ${JSON.stringify(data || err.message)}`);
    }
  }

  /**
   * 流式聊天补全（返回 AsyncIterator）
   * @param {Array<Object>} messages
   * @param {Object} [options]
   * @returns {AsyncGenerator<string>}
   */
  async *chatStream(messages, options = {}) {
    const payload = {
      model: options.model || this.model,
      messages: messages,
      max_tokens: options.maxTokens || this.maxTokens,
      temperature: options.temperature ?? this.temperature,
      stream: true,
    };

    if (options.tools && options.tools.length > 0) {
      payload.tools = options.tools;
      payload.tool_choice = options.toolChoice || 'auto';
    }

    const response = await this.client.post('/chat/completions', payload, {
      responseType: 'stream',
    });

    let buffer = '';
    for await (const chunk of response.data) {
      buffer += chunk.toString();
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith('data: ')) continue;
        const jsonStr = trimmed.slice(6);
        if (jsonStr === '[DONE]') return;

        try {
          const parsed = JSON.parse(jsonStr);
          const delta = parsed.choices?.[0]?.delta;
          if (delta?.content) {
            yield delta.content;
          }
        } catch {
          // 跳过解析失败的行
        }
      }
    }
  }
}

module.exports = { LLMClient };