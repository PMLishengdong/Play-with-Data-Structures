package com.aichat.app.tools;

import com.aichat.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic HTTP GET tool. Returns a textual excerpt of the response.
 * Useful as a simple "fetch" / "web_lookup" capability.
 */
public class WebFetchTool implements Tool {
    @Override public String getId() { return "web_fetch"; }
    @Override public String getDisplayName() { return "网页抓取"; }
    @Override public String getDescription() { return "通过 HTTP GET 抓取 URL 内容，返回纯文本"; }
    @Override
    public String getArgumentSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"url\":{\"type\":\"string\",\"description\":\"目标 URL\"},"
                + "\"maxLength\":{\"type\":\"integer\",\"description\":\"返回的最大字符数，默认2000\"}"
                + "},\"required\":[\"url\"]}";
    }
    @Override
    public String execute(Map<String, Object> arguments) {
        Args a = new Args(arguments);
        String url = a.str("url", null);
        if (url == null) return "error: url is required";
        int max = a.integer("maxLength", 2000);
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", "AIChatApp/1.0");
            HttpUtil.Response resp = HttpUtil.get(url, headers);
            try {
                String body = resp.body == null ? "" : resp.body;
                if (body.length() > max) body = body.substring(0, max) + "…";
                return "HTTP " + resp.code + "\n" + body;
            } finally {
                HttpUtil.close(resp);
            }
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }
}
