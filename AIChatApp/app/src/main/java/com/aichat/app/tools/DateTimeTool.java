package com.aichat.app.tools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Get current date/time in a given timezone and format. */
public class DateTimeTool implements Tool {
    @Override public String getId() { return "datetime"; }
    @Override public String getDisplayName() { return "当前时间"; }
    @Override public String getDescription() { return "返回当前日期/时间，可指定时区与格式"; }
    @Override
    public String getArgumentSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"timezone\":{\"type\":\"string\",\"description\":\"IANA时区，如 Asia/Shanghai\"},"
                + "\"format\":{\"type\":\"string\",\"description\":\"SimpleDateFormat格式\"}"
                + "}}";
    }
    @Override
    public String execute(Map<String, Object> arguments) {
        Args a = new Args(arguments);
        String tz = a.str("timezone", "UTC");
        String fmt = a.str("format", "yyyy-MM-dd HH:mm:ss");
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone(tz));
            return sdf.format(new Date());
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }
}
