package com.example.aichat.tool;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class UrlParserTool implements ToolExecutor {
    @Override
    public String execute(Map<String, Object> params) {
        try {
            String urlStr = (String) params.get("url");
            if (urlStr == null || urlStr.isEmpty()) {
                return "Error: No URL provided";
            }
            
            URL url = new URL(urlStr);
            Map<String, String> result = new HashMap<String, String>();
            result.put("protocol", url.getProtocol());
            result.put("host", url.getHost());
            result.put("port", String.valueOf(url.getPort()));
            result.put("path", url.getPath());
            result.put("query", url.getQuery());
            result.put("ref", url.getRef());
            
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : result.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().equals("-1")) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
