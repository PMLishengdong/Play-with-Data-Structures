package com.aichat.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aichat.app.R;
import com.aichat.app.model.MCPConfig;
import com.aichat.app.model.ToolConfig;
import com.aichat.app.storage.ConfigManager;
import com.aichat.app.tools.ToolManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool management: register, enable / disable, and remove tools available to
 * the LLM. Built-in tools come pre-registered; you can also bridge MCP tools
 * or create generic HTTP tools.
 */
public class ToolMgmtActivity extends AppCompatActivity {

    private static final String[] TYPES = new String[]{"builtin", "mcp", "http"};
    private static final String[] TYPE_LABELS = new String[]{"内置", "MCP", "HTTP"};

    private ConfigManager config;
    private ToolAdapter adapter;
    private RecyclerView list;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);
        config = ConfigManager.get(this);

        Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        list = (RecyclerView) findViewById(R.id.toolList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ToolAdapter();
        list.setAdapter(adapter);
        emptyView = findViewById(R.id.emptyView);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fabAdd);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showEditor(null); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pre-register built-in tools on first run
        ensureBuiltins();
        adapter.refresh();
    }

    private void ensureBuiltins() {
        List<ToolConfig> tools = config.listTools();
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < tools.size(); i++) ids.add(tools.get(i).id);
        if (!ids.contains("calculator")) addBuiltin("calculator", "计算器", "对算术表达式求值");
        if (!ids.contains("datetime")) addBuiltin("datetime", "当前时间", "返回当前日期时间");
        if (!ids.contains("web_search")) addBuiltin("web_search", "网页搜索", "调用外部搜索 API");
        if (!ids.contains("web_fetch")) addBuiltin("web_fetch", "网页抓取", "HTTP GET 抓取");
    }

    private void addBuiltin(String id, String name, String desc) {
        ToolConfig t = new ToolConfig();
        t.id = id;
        t.name = name;
        t.type = "builtin";
        t.description = desc;
        t.params = "{}";
        config.saveTool(t);
    }

    private void showEditor(final ToolConfig existing) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_tool, null, false);
        final EditText etId = (EditText) root.findViewById(R.id.etId);
        final EditText etName = (EditText) root.findViewById(R.id.etName);
        final EditText etParams = (EditText) root.findViewById(R.id.etParams);
        final EditText etDescription = (EditText) root.findViewById(R.id.etDescription);
        final Spinner spType = (Spinner) root.findViewById(R.id.spType);

        ArrayAdapter<String> spAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, TYPE_LABELS);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(spAdapter);

        if (existing != null) {
            etId.setText(existing.id);
            etName.setText(existing.name);
            etParams.setText(existing.params);
            etDescription.setText(existing.description);
            for (int i = 0; i < TYPES.length; i++) {
                if (TYPES[i].equals(existing.type)) { spType.setSelection(i); break; }
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(existing == null ? "添加工具" : "编辑工具");
        b.setView(root);
        b.setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                ToolConfig t = existing == null ? new ToolConfig() : existing;
                t.id = etId.getText().toString().trim();
                t.name = etName.getText().toString().trim();
                t.params = etParams.getText().toString().trim();
                t.description = etDescription.getText().toString().trim();
                t.type = TYPES[spType.getSelectedItemPosition()];
                if (TextUtils.isEmpty(t.id)) t.id = "tool_" + System.currentTimeMillis();
                if (TextUtils.isEmpty(t.name)) t.name = t.id;
                // For MCP type, allow params like {serverId:<id>,toolName:<name>}
                // We'll just store as-is; runtime reads it.
                config.saveTool(t);
                ToolManager.getInstance().invalidate();
                adapter.refresh();
            }
        });
        b.setNeutralButton("从 MCP 选择", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                pickMCPTool();
            }
        });
        b.setNegativeButton(R.string.action_cancel, null);
        b.show();
    }

    private void pickMCPTool() {
        final List<MCPConfig> mcps = config.listMCPs();
        if (mcps.size() == 0) {
            new AlertDialog.Builder(this).setMessage("请先在 MCP 管理中添加 MCP 服务").setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        final List<String> labels = new ArrayList<String>();
        for (int i = 0; i < mcps.size(); i++) labels.add(mcps.get(i).name);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("选择 MCP 服务");
        b.setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                final MCPConfig m = mcps.get(which);
                final EditText etToolName = new EditText(ToolMgmtActivity.this);
                etToolName.setHint("MCP 工具名 (从服务器 tools/list 中获得)");
                new AlertDialog.Builder(ToolMgmtActivity.this)
                        .setTitle(m.name)
                        .setView(etToolName)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String tname = etToolName.getText().toString().trim();
                                if (tname.length() == 0) return;
                                ToolConfig t = new ToolConfig();
                                t.id = "mcp_" + m.id + "_" + tname;
                                t.name = m.name + " / " + tname;
                                t.type = "mcp";
                                t.description = "MCP tool from " + m.name;
                                JSONObject p = new JSONObject();
                                try {
                                    p.put("serverId", m.id);
                                    p.put("toolName", tname);
                                } catch (Exception ignored) {}
                                t.params = p.toString();
                                config.saveTool(t);
                                ToolManager.getInstance().invalidate();
                                adapter.refresh();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        b.show();
    }

    private class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.VH> {
        private List<ToolConfig> data;

        void refresh() {
            data = config.listTools();
            notifyDataSetChanged();
            emptyView.setVisibility(data.size() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(data.size() == 0 ? View.GONE : View.VISIBLE);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            final ToolConfig t = data.get(position);
            h.title.setText(t.name + (t.id == null ? "" : "  (" + t.id + ")"));
            h.subtitle.setText(typeLabel(t.type));
            h.detail.setText(t.description);
            h.swEnabled.setOnCheckedChangeListener(null);
            h.swEnabled.setChecked(t.enabled);
            h.swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    t.enabled = on;
                    config.saveTool(t);
                    ToolManager.getInstance().invalidate();
                }
            });
            h.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(ToolMgmtActivity.this)
                            .setTitle("删除工具?")
                            .setMessage(t.name)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    config.deleteTool(t.id);
                                    ToolManager.getInstance().invalidate();
                                    refresh();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            });
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { showEditor(t); }
            });
        }

        @Override
        public int getItemCount() { return data == null ? 0 : data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            TextView detail;
            Switch swEnabled;
            android.widget.ImageButton btnDelete;
            VH(View v) {
                super(v);
                title = (TextView) v.findViewById(R.id.tvTitle);
                subtitle = (TextView) v.findViewById(R.id.tvSubtitle);
                detail = (TextView) v.findViewById(R.id.tvDetail);
                swEnabled = (Switch) v.findViewById(R.id.swEnabled);
                btnDelete = (android.widget.ImageButton) v.findViewById(R.id.btnDelete);
            }
        }
    }

    private static String typeLabel(String t) {
        if ("mcp".equals(t)) return "MCP";
        if ("http".equals(t)) return "HTTP";
        return "内置";
    }
}
