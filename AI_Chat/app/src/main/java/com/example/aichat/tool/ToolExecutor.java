package com.example.aichat.tool;

import java.util.Map;

public interface ToolExecutor {
    String execute(Map<String, Object> params);
}
