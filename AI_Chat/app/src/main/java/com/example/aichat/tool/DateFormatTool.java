package com.example.aichat.tool;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

public class DateFormatTool implements ToolExecutor {
    @Override
    public String execute(Map<String, Object> params) {
        try {
            long timestamp = params.containsKey("timestamp") 
                    ? ((Number) params.get("timestamp")).longValue() 
                    : System.currentTimeMillis();
            
            String format = params.containsKey("format") 
                    ? (String) params.get("format") 
                    : "yyyy-MM-dd HH:mm:ss";
            
            String timezone = params.containsKey("timezone") 
                    ? (String) params.get("timezone") 
                    : "UTC";
            
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            sdf.setTimeZone(TimeZone.getTimeZone(timezone));
            
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
