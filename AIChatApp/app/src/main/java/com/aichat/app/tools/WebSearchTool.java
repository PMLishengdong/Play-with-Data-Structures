package com.aichat.app.tools;

import com.aichat.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls a configured JSON-over-HTTP search API (e.g. SerpAPI, Bing, Tavily).
 * If the response is JSON, it is summarized as a few result lines.
 *
 * Configure via params:
 *   { "url":"https://...search?q={query}&...", "apiKey":"..." }
 */
public class WebSearchTool implements Tool {
    @Override public String getId() { return "web_search"; }
    @Override public String getDisplayName() { return "网页搜索"; }
    @Override public String getDescription() { return "调用外部搜索 API 返回前若干条结果摘要"; }
    @Override
    public String getArgumentSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\",\"description\":\"搜索关键词\"},"
                + "\"max_results\":{\"type\":\"integer\",\"description\":\"最多返回多少条结果\"}"
                + "},\"required\":[\"query\"]}";
    }
    @Override
    public String execute(Map<String, Object> arguments) {
        Args a = new Args(arguments);
        String q = a.str("query", null);
        if (q == null) return "error: query is required";
        // Without an external config, do a lightweight DuckDuckGo redirect fallback.
        String ddg = "https://duckduckgo.com/html/?q=" + urlEncode(q);
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", "Mozilla/5.0 (AIChatApp)");
            HttpUtil.Response resp = HttpUtil.get(ddg, headers);
            try {
                String body = resp.body == null ? "" : resp.body;
                StringBuilder sb = new StringBuilder();
                int idx = 0;
                int from = 0;
                int count = 0;
                int max = a.integer("max_results", 5);
                while (count < max) {
                    int a1 = body.indexOf("class=\"result__a\"", from);
                    if (a1 < 0) break;
                    int hrefStart = body.indexOf("href=\"", a1);
                    int hrefEnd = hrefStart >= 0 ? body.indexOf("\"", hrefStart + 6) : -1;
                    int titleStart = body.indexOf(">", hrefEnd);
                    int titleEnd = body.indexOf("</a>", titleStart);
                    String url = hrefStart >= 0 && hrefEnd > hrefStart ? body.substring(hrefStart + 6, hrefEnd) : "";
                    String title = titleStart >= 0 && titleEnd > titleStart ? stripTags(body.substring(titleStart + 1, titleEnd)) : "";
                    if (title.length() > 0) {
                        sb.append("• ").append(title).append(" — ").append(url).append('\n');
                        count++;
                    }
                    from = titleEnd > 0 ? titleEnd : a1 + 16;
                    idx++;
                    if (idx > 30) break;
                }
                if (count == 0) {
                    return "未获取到结果";
                }
                return sb.toString();
            } finally {
                HttpUtil.close(resp);
            }
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) { return s; }
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "").trim();
    }
}
