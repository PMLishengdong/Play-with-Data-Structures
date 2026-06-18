package com.example.aichat;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aichat.mcp.MCPManager;
import com.example.aichat.model.AIModel;
import com.example.aichat.model.MCPInfo;
import com.example.aichat.model.ToolInfo;
import com.example.aichat.tool.ToolManager;
import com.example.aichat.util.ConfigManager;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private ConfigManager configManager;
    private MCPManager mcpManager;
    private ToolManager toolManager;

    private Spinner spinnerModel;
    private LinearLayout llMCPList;
    private LinearLayout llToolList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        configManager = ConfigManager.getInstance(this);
        mcpManager = MCPManager.getInstance(this);
        toolManager = ToolManager.getInstance(this);

        spinnerModel = findViewById(R.id.spinner_model);
        llMCPList = findViewById(R.id.ll_mcp_list);
        llToolList = findViewById(R.id.ll_tool_list);

        setupModelSpinner();
        setupMCPList();
        setupToolList();
    }

    private void setupModelSpinner() {
        final List<AIModel> models = configManager.getModels();
        ArrayAdapter<AIModel> adapter = new ArrayAdapter<AIModel>(
                this, android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(adapter);

        String selectedId = configManager.getSelectedModelId();
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).getId().equals(selectedId)) {
                spinnerModel.setSelection(i);
                break;
            }
        }

        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AIModel model = models.get(position);
                configManager.setSelectedModelId(model.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupMCPList() {
        llMCPList.removeAllViews();
        List<MCPInfo> mcps = mcpManager.getAllMCPS();

        for (final MCPInfo mcp : mcps) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(8, 8, 8, 8);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            infoLayout.setLayoutParams(infoParams);

            TextView nameView = new TextView(this);
            nameView.setText(mcp.getName());
            nameView.setTextSize(16);
            nameView.setTextColor(getResources().getColor(R.color.text_primary));
            infoLayout.addView(nameView);

            TextView descView = new TextView(this);
            descView.setText(mcp.getDescription());
            descView.setTextSize(14);
            descView.setTextColor(getResources().getColor(R.color.text_secondary));
            infoLayout.addView(descView);

            itemLayout.addView(infoLayout);

            final Button toggleBtn = new Button(this);
            toggleBtn.setText(mcp.isEnabled() ? R.string.disable : R.string.enable);
            toggleBtn.setTextColor(getResources().getColor(R.color.white));
            toggleBtn.setBackgroundColor(mcp.isEnabled() 
                    ? getResources().getColor(R.color.colorAccent)
                    : getResources().getColor(R.color.text_secondary));
            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean enabled = !mcp.isEnabled();
                    mcp.setEnabled(enabled);
                    mcpManager.enableMCP(mcp.getId(), enabled);
                    toggleBtn.setText(enabled ? R.string.disable : R.string.enable);
                    toggleBtn.setBackgroundColor(enabled
                            ? getResources().getColor(R.color.colorAccent)
                            : getResources().getColor(R.color.text_secondary));
                }
            });
            itemLayout.addView(toggleBtn);

            Button uninstallBtn = new Button(this);
            uninstallBtn.setText(R.string.uninstall);
            uninstallBtn.setTextColor(getResources().getColor(R.color.white));
            uninstallBtn.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            uninstallBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mcpManager.unregisterMCP(mcp.getId());
                    setupMCPList();
                }
            });
            itemLayout.addView(uninstallBtn);

            llMCPList.addView(itemLayout);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(getResources().getColor(R.color.border));
            llMCPList.addView(divider);
        }
    }

    private void setupToolList() {
        llToolList.removeAllViews();
        List<ToolInfo> tools = toolManager.getAllTools();

        for (final ToolInfo tool : tools) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(8, 8, 8, 8);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            infoLayout.setLayoutParams(infoParams);

            TextView nameView = new TextView(this);
            nameView.setText(tool.getName());
            nameView.setTextSize(16);
            nameView.setTextColor(getResources().getColor(R.color.text_primary));
            infoLayout.addView(nameView);

            TextView descView = new TextView(this);
            descView.setText(tool.getDescription());
            descView.setTextSize(14);
            descView.setTextColor(getResources().getColor(R.color.text_secondary));
            infoLayout.addView(descView);

            itemLayout.addView(infoLayout);

            final Button toggleBtn = new Button(this);
            toggleBtn.setText(tool.isEnabled() ? R.string.disable : R.string.enable);
            toggleBtn.setTextColor(getResources().getColor(R.color.white));
            toggleBtn.setBackgroundColor(tool.isEnabled()
                    ? getResources().getColor(R.color.colorAccent)
                    : getResources().getColor(R.color.text_secondary));
            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean enabled = !tool.isEnabled();
                    tool.setEnabled(enabled);
                    toolManager.enableTool(tool.getId(), enabled);
                    toggleBtn.setText(enabled ? R.string.disable : R.string.enable);
                    toggleBtn.setBackgroundColor(enabled
                            ? getResources().getColor(R.color.colorAccent)
                            : getResources().getColor(R.color.text_secondary));
                }
            });
            itemLayout.addView(toggleBtn);

            Button uninstallBtn = new Button(this);
            uninstallBtn.setText(R.string.uninstall);
            uninstallBtn.setTextColor(getResources().getColor(R.color.white));
            uninstallBtn.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            uninstallBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toolManager.unregisterTool(tool.getId());
                    setupToolList();
                }
            });
            itemLayout.addView(uninstallBtn);

            llToolList.addView(itemLayout);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(getResources().getColor(R.color.border));
            llToolList.addView(divider);
        }
    }
}
