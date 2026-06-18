package com.aichat.app.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Thin convenience helpers around org.json for our Java 7 codebase.
 */
public final class JsonUtil {
    private JsonUtil() {}

    public static JSONObject toJsonObject(Map<String, ?> map) throws JSONException {
        JSONObject o = new JSONObject();
        if (map == null) return o;
        for (Object k : map.keySet()) {
            if (k == null) continue;
            o.put(k.toString(), map.get(k));
        }
        return o;
    }

    public static JSONArray toJsonArray(List<?> list) throws JSONException {
        JSONArray a = new JSONArray();
        if (list == null) return a;
        for (int i = 0; i < list.size(); i++) {
            a.put(list.get(i));
        }
        return a;
    }

    public static String optString(JSONObject o, String key, String def) {
        if (o == null) return def;
        if (!o.has(key) || o.isNull(key)) return def;
        return o.optString(key, def);
    }

    public static JSONObject optObject(JSONObject o, String key) {
        if (o == null) return null;
        if (!o.has(key) || o.isNull(key)) return null;
        return o.optJSONObject(key);
    }

    public static JSONArray optArray(JSONObject o, String key) {
        if (o == null) return null;
        if (!o.has(key) || o.isNull(key)) return null;
        return o.optJSONArray(key);
    }

    public static String[] toStringArray(JSONArray a) {
        if (a == null) return new String[0];
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < a.length(); i++) {
            list.add(a.optString(i, ""));
        }
        return list.toArray(new String[list.size()]);
    }
}
