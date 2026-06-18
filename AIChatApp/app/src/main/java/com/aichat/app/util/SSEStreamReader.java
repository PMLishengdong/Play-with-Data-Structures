package com.aichat.app.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * SSE (Server-Sent Events) line reader. Parses "data: ..." lines and
 * dispatches them via {@link SSEHandler}. Supports multi-line events separated
 * by blank lines.
 */
public class SSEStreamReader {
    public interface SSEHandler {
        /** Called for every non-empty "data:" line payload (just the data, prefix stripped). */
        void onData(String data);
        /** Called when a blank line is seen after data lines (event boundary). */
        void onEventEnd();
        /** Called on stream end. */
        void onEnd();
    }

    private final InputStream is;
    private final SSEHandler handler;
    private volatile boolean stopped;

    public SSEStreamReader(InputStream is, SSEHandler handler) {
        this.is = is;
        this.handler = handler;
    }

    public void stop() {
        stopped = true;
    }

    public void readAll() throws Exception {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            StringBuilder dataBuf = new StringBuilder();
            while (!stopped && (line = br.readLine()) != null) {
                if (line.length() == 0) {
                    if (dataBuf.length() > 0) {
                        handler.onData(dataBuf.toString());
                        dataBuf.setLength(0);
                        handler.onEventEnd();
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    // comment / heartbeat
                    continue;
                }
                int idx = line.indexOf(':');
                String field;
                String value;
                if (idx < 0) {
                    field = line;
                    value = "";
                } else {
                    field = line.substring(0, idx);
                    value = idx + 1 < line.length() && line.charAt(idx + 1) == ' '
                            ? line.substring(idx + 2) : line.substring(idx + 1);
                }
                if ("data".equals(field)) {
                    if (dataBuf.length() > 0) dataBuf.append('\n');
                    dataBuf.append(value);
                } else if ("event".equals(field) || "id".equals(field) || "retry".equals(field)) {
                    // ignore for now
                }
            }
            if (dataBuf.length() > 0) {
                handler.onData(dataBuf.toString());
                dataBuf.setLength(0);
                handler.onEventEnd();
            }
            // Normal end of stream; only signal onEnd when nothing blew up.
            handler.onEnd();
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) {}
        }
    }
}
