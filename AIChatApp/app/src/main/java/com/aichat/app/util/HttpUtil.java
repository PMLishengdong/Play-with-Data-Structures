package com.aichat.app.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Tiny HTTP helper. Supports JSON body POST/GET and chunked streaming reads
 * (line-by-line, suitable for SSE).
 */
public final class HttpUtil {
    public static final int CONNECT_TIMEOUT_MS = 15000;
    public static final int READ_TIMEOUT_MS = 60000;

    private HttpUtil() {}

    public static class Response {
        public int code;
        public String body;
        public InputStream stream; // for streaming, only available if keepStream=true
        public HttpURLConnection conn;
    }

    public static Response post(String url, Map<String, String> headers, String jsonBody) throws Exception {
        return doRequest("POST", url, headers, jsonBody, false);
    }

    public static Response postStream(String url, Map<String, String> headers, String jsonBody) throws Exception {
        return doRequest("POST", url, headers, jsonBody, true);
    }

    public static Response get(String url, Map<String, String> headers) throws Exception {
        return doRequest("GET", url, headers, null, false);
    }

    private static Response doRequest(String method, String url, Map<String, String> headers, String body, boolean keepStream) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (body != null) {
            conn.setDoOutput(true);
            byte[] bytes = body.getBytes("UTF-8");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = null;
            try {
                os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
            } finally {
                if (os != null) try { os.close(); } catch (Exception ignored) {}
            }
        }
        Response resp = new Response();
        resp.code = conn.getResponseCode();
        resp.conn = conn;
        if (keepStream) {
            resp.stream = new BufferedInputStream(conn.getInputStream());
        } else {
            InputStream is = null;
            try {
                is = conn.getInputStream();
            } catch (Exception ex) {
                is = conn.getErrorStream();
            }
            if (is == null) {
                resp.body = "";
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                try {
                    while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
                } finally {
                    try { is.close(); } catch (Exception ignored) {}
                }
                resp.body = baos.toString("UTF-8");
            }
        }
        return resp;
    }

    public static void close(Response resp) {
        if (resp == null) return;
        if (resp.stream != null) {
            try { resp.stream.close(); } catch (Exception ignored) {}
        }
        if (resp.conn != null) {
            try { resp.conn.disconnect(); } catch (Exception ignored) {}
        }
    }
}
