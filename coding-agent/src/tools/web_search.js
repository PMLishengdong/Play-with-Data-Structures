/**
 * WebSearchTool - 网页搜索
 * 对应 Trae IDE 的 WebSearch 工具
 */

const { BaseTool } = require('./base');
const http = require('http');
const https = require('https');
const url = require('url');

class WebSearchTool extends BaseTool {
  constructor() {
    super({
      name: 'web_search',
      description: '搜索互联网获取实时信息。使用 DuckDuckGo 等搜索引擎。',
      parameters: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: '搜索关键词',
          },
          num_results: {
            type: 'integer',
            description: '返回结果数量，默认 5',
          },
        },
        required: ['query'],
      },
    });
  }

  async execute(args) {
    const { query, num_results = 5 } = args;

    try {
      const encodedQuery = encodeURIComponent(query);
      const searchUrl = `https://html.duckduckgo.com/html/?q=${encodedQuery}`;

      const html = await this._fetchUrl(searchUrl);
      const results = this._parseDuckDuckGoResults(html).slice(0, num_results);

      if (results.length === 0) {
        return `(no search results for "${query}")`;
      }

      return results.map((r, i) =>
        `[${i + 1}] ${r.title}\n    URL: ${r.url}\n    Snippet: ${r.snippet}`
      ).join('\n\n');
    } catch (err) {
      return `Web search error: ${err.message}`;
    }
  }

  _fetchUrl(targetUrl) {
    return new Promise((resolve, reject) => {
      const parsed = url.parse(targetUrl);
      const mod = parsed.protocol === 'https:' ? https : http;
      const req = mod.get(targetUrl, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        },
        timeout: 15000,
      }, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => resolve(data));
      });
      req.on('error', reject);
      req.on('timeout', () => { req.destroy(); reject(new Error('Timeout')); });
    });
  }

  _parseDuckDuckGoResults(html) {
    const results = [];
    // 简单的 HTML 解析提取搜索结果
    const linkRegex = /<a[^>]+class="result__a"[^>]*>([\s\S]*?)<\/a>/gi;
    const snippetRegex = /<a[^>]+class="result__snippet"[^>]*>([\s\S]*?)<\/a>/gi;
    const urlRegex = /<a[^>]+class="result__url"[^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/gi;

    const titles = [];
    let match;
    while ((match = linkRegex.exec(html)) !== null) {
      titles.push(match[1].replace(/<[^>]+>/g, '').trim());
    }

    const snippets = [];
    while ((match = snippetRegex.exec(html)) !== null) {
      snippets.push(match[1].replace(/<[^>]+>/g, '').trim());
    }

    const urls = [];
    while ((match = urlRegex.exec(html)) !== null) {
      urls.push(match[1]);
    }

    for (let i = 0; i < Math.max(titles.length, urls.length, snippets.length); i++) {
      results.push({
        title: titles[i] || '(no title)',
        url: urls[i] || '',
        snippet: snippets[i] || '',
      });
    }

    return results;
  }
}

module.exports = { WebSearchTool };