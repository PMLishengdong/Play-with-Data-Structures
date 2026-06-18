package com.aichat.app.mcp;

import android.util.Base64;
import android.util.Log;

import com.aichat.app.model.MCPConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Minimal RFC 6455 WebSocket client. Text frames only. This is intentionally
 * lightweight: we only need a single connection per MCP server, send JSON-RPC
 * requests, and read responses one at a time. Sufficient for AIDE without
 * extra dependencies.
 */
public class WebSocketMCPClient implements MCPClient {
    private static final String TAG = "WebSocketMCPClient";

    private final MCPConfig config;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int idCounter = 0;
    private boolean ready;
    private final Object writeLock = new Object();
    private final Random random = new Random();

    public WebSocketMCPClient(MCPConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        URI uri = URI.create(config.endpoint);
        String scheme = uri.getScheme();
        if (scheme == null) throw new IllegalArgumentException("invalid endpoint");
        int port = uri.getPort();
        if (port < 0) {
            port = "wss".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        boolean ssl = "wss".equalsIgnoreCase(scheme);
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (path == null || path.length() == 0) path = "/";
        if (uri.getRawQuery() != null) path = path + "?" + uri.getRawQuery();

        socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(host, port), 15000);
        socket.setSoTimeout(0);

        in = socket.getInputStream();
        out = socket.getOutputStream();

        // Send handshake
        String key = randomKey();
        String handshake = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Origin: http://localhost\r\n"
                + "\r\n";
        synchronized (writeLock) {
            out.write(handshake.getBytes("ASCII"));
            out.flush();
        }
        // Read handshake response using raw byte reads so we don't gobble
        // post-handshake bytes via BufferedReader's internal buffer.
        String statusLine = readLineRaw(in);
        if (statusLine == null || statusLine.indexOf("101") < 0) {
            throw new IOException("WebSocket handshake failed: " + statusLine);
        }
        while (true) {
            String line = readLineRaw(in);
            if (line == null || line.length() == 0) break;
        }
        ready = true;
    }

    /** Read one CRLF-terminated line as ASCII without buffering past the newline. */
    private static String readLineRaw(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int b = is.read();
            if (b < 0) return sb.length() == 0 ? null : sb.toString();
            if (prev == '\r' && b == '\n') {
                sb.setLength(sb.length() - 1);
                return sb.toString();
            }
            if (b != '\r') sb.append((char) b);
            prev = b;
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void close() {
        ready = false;
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    @Override
    public MCPConfig getConfig() {
        return config;
    }

    private int nextId() {
        synchronized (this) { return ++idCounter; }
    }

    @Override
    public List<MCPTool> listTools() throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId());
        req.put("method", "tools/list");
        req.put("params", new JSONObject());
        JSONObject resp = roundTrip(req);
        if (resp == null) return new ArrayList<MCPTool>();
        JSONArray result = resp.optJSONArray("result");
        if (result == null) {
            JSONObject r = resp.optJSONObject("result");
            if (r != null) result = r.optJSONArray("tools");
        }
        List<MCPTool> tools = new ArrayList<MCPTool>();
        if (result != null) {
            for (int i = 0; i < result.length(); i++) {
                JSONObject o = result.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                if (name.length() == 0) continue;
                String desc = o.optString("description", "");
                JSONObject schema = o.optJSONObject("inputSchema");
                if (schema == null) schema = o.optJSONObject("input_schema");
                String schemaStr = schema == null ? "{}" : schema.toString();
                tools.add(new MCPTool(config.id, name, desc, schemaStr));
            }
        }
        return tools;
    }

    @Override
    public MCPTool.Result callTool(String toolName, Map<String, Object> arguments) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId());
        req.put("method", "tools/call");
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", mapToJson(arguments));
        req.put("params", params);
        JSONObject resp = roundTrip(req);
        if (resp == null) return new MCPTool.Result(false, "no response", null);
        if (resp.has("error")) return new MCPTool.Result(false, resp.optJSONObject("error").toString(), null);
        JSONObject result = resp.optJSONObject("result");
        String text = result == null ? "" : result.toString();
        return new MCPTool.Result(true, text, result);
    }

    private JSONObject roundTrip(JSONObject req) throws Exception {
        if (!ready) throw new IllegalStateException("not connected");
        String payload = req.toString();
        int reqId = req.optInt("id", -1);
        sendTextFrame(payload);
        // Read frames until we get one with matching id
        StringBuilder text = new StringBuilder();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            String frame = readTextFrame();
            if (frame == null) return null;
            try {
                JSONObject obj = new JSONObject(frame);
                if (obj.has("id") && obj.optInt("id", -2) == reqId) {
                    return obj;
                }
                // Not ours; ignore
            } catch (JSONException ex) {
                // ignore
            }
        }
        throw new RuntimeException("timeout waiting for response");
    }

    private void sendTextFrame(String text) throws IOException {
        byte[] payload = text.getBytes("UTF-8");
        ByteArrayOutputStream2 baos = new ByteArrayOutputStream2();
        int b0 = 0x80 | 0x1; // FIN + text opcode
        baos.write(b0);
        int len = payload.length;
        if (len < 126) {
            baos.write(0x80 | len); // mask bit set
        } else if (len <= 0xFFFF) {
            baos.write(0x80 | 126);
            baos.write((len >> 8) & 0xFF);
            baos.write(len & 0xFF);
        } else {
            baos.write(0x80 | 127);
            long lv = len;
            for (int i = 7; i >= 0; i--) baos.write((int) ((lv >> (8 * i)) & 0xFF));
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        baos.write(mask);
        for (int i = 0; i < payload.length; i++) {
            baos.write(payload[i] ^ mask[i % 4]);
        }
        synchronized (writeLock) {
            out.write(baos.toByteArray());
            out.flush();
        }
    }

    private String readTextFrame() throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read();
        if (b1 < 0) return null;
        boolean masked = (b1 & 0x80) != 0;
        int len = b1 & 0x7F;
        if (len == 126) {
            int hi = in.read(); int lo = in.read();
            len = (hi << 8) | lo;
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
        }
        byte[] payload = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(payload, read, len - read);
            if (n < 0) throw new IOException("eof");
            read += n;
        }
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        int opcode = b0 & 0x0F;
        if (opcode == 0x8) return null; // close
        if (opcode == 0x9) return ""; // ping, ignore
        return new String(payload, "UTF-8");
    }

    private static String randomKey() {
        byte[] b = new byte[16];
        new Random().nextBytes(b);
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static JSONObject mapToJson(Map<String, Object> map) {
        if (map == null) return new JSONObject();
        JSONObject o = new JSONObject();
        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            Object v = e.getValue();
            try {
                if (v instanceof Map) o.put(e.getKey(), mapToJson((Map<String, Object>) v));
                else if (v instanceof List) {
                    JSONArray a = new JSONArray();
                    List<?> l = (List<?>) v;
                    for (int i = 0; i < l.size(); i++) {
                        Object item = l.get(i);
                        if (item instanceof Map) a.put(mapToJson((Map<String, Object>) item));
                        else a.put(item);
                    }
                    o.put(e.getKey(), a);
                } else o.put(e.getKey(), v);
            } catch (JSONException ex) {
                Log.w(TAG, "skip arg: " + e.getKey());
            }
        }
        return o;
    }

    /** Local stand-in for ByteArrayOutputStream to avoid extra import noise. */
    private static class ByteArrayOutputStream2 extends java.io.ByteArrayOutputStream {}
}
