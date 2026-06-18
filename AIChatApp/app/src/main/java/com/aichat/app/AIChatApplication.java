package com.aichat.app;

import android.app.Application;

import com.aichat.app.mcp.MCPManager;
import com.aichat.app.storage.ConfigManager;
import com.aichat.app.tools.ToolManager;

/**
 * App entry point. Initializes the configuration manager, MCP and tool
 * registries. Keep all init light; anything heavy should run on a worker.
 */
public class AIChatApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ConfigManager cfg = ConfigManager.get(this);
        ToolManager.getInstance().init(cfg);
        MCPManager.getInstance().init(cfg);
    }
}
