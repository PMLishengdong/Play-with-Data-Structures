package com.example.aichat.mcp;

import android.os.AsyncTask;

import com.example.aichat.model.MCPInfo;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MCPInterface {
    private final MCPInfo mcpInfo;
    private final OkHttpClient client;
    private final Gson gson;

    public MCPInterface(MCPInfo mcpInfo) {
        this.mcpInfo = mcpInfo;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public MCPInfo getMcpInfo() {
        return mcpInfo;
    }

    public void call(String method, Map<String, Object> params, MCPResponseCallback callback) {
        new MCPCallTask(method, params, callback).execute();
    }

    public interface MCPResponseCallback {
        void onSuccess(String result);
        void onError(Exception e);
    }

    private class MCPCallTask extends AsyncTask<Void, Void, String> {
        private final String method;
        private final Map<String, Object> params;
        private final MCPResponseCallback callback;
        private Exception error;

        public MCPCallTask(String method, Map<String, Object> params, MCPResponseCallback callback) {
            this.method = method;
            this.params = params;
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                Map<String, Object> requestBody = new HashMap<String, Object>();
                requestBody.put("method", method);
                requestBody.put("params", params);

                RequestBody body = RequestBody.create(
                        gson.toJson(requestBody),
                        okhttp3.MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url(mcpInfo.getBaseUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                } else {
                    error = new IOException("MCP request failed: " + response.code());
                    return null;
                }
            } catch (Exception e) {
                error = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && callback != null) {
                callback.onSuccess(result);
            } else if (error != null && callback != null) {
                callback.onError(error);
            }
        }
    }
}
