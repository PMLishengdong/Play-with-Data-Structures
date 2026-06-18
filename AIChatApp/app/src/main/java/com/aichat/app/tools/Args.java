package com.aichat.app.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to read string/number/boolean fields out of an argument map produced
 * by JSON parsing. Provides safe defaults and type coercion for the
 * common-case tool implementations.
 */
public final class Args {
    private final Map<String, Object> m;

    public Args(Map<String, Object> map) {
        this.m = map == null ? new HashMap<String, Object>() : map;
    }

    public String str(String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public String str(String key, String def) {
        Object v = m.get(key);
        return v == null ? def : v.toString();
    }

    public int integer(String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }

    public double dbl(String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (Exception ignored) {}
        }
        return def;
    }

    public boolean bool(String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            String s = (String) v;
            return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
        }
        return def;
    }

    public Map<String, Object> raw() { return m; }
}
