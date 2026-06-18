package com.aichat.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aichat.app.R;
import com.aichat.app.mcp.MCPManager;
import com.aichat.app.model.MCPConfig;
import com.aichat.app.storage.ConfigManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * MCP server management: register HTTP/SSE and WebSocket MCP servers. Each
 * server can be enabled / disabled, edited or deleted (uninstalled).
 */
public class MCPMgmtActivity extends AppCompatActivity {

    private ConfigManager config;
    private MCPAdapter adapter;
    private RecyclerView list;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcp);
        config = ConfigManager.get(this);

        Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        list = (RecyclerView) findViewById(R.id.mcpList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MCPAdapter();
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
        adapter.refresh();
    }

    private void showEditor(final MCPConfig existing) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_mcp, null, false);
        final EditText etName = (EditText) root.findViewById(R.id.etName);
        final EditText etEndpoint = (EditText) root.findViewById(R.id.etEndpoint);
        final EditText etDescription = (EditText) root.findViewById(R.id.etDescription);
        final RadioGroup rgType = (RadioGroup) root.findViewById(R.id.rgType);
        final RadioButton rbHttp = (RadioButton) root.findViewById(R.id.rbHttp);
        final RadioButton rbWs = (RadioButton) root.findViewById(R.id.rbWs);

        if (existing != null) {
            etName.setText(existing.name);
            etEndpoint.setText(existing.endpoint);
            etDescription.setText(existing.description);
            if ("ws".equals(existing.type)) rbWs.setChecked(true);
            else rbHttp.setChecked(true);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(existing == null ? "注册 MCP" : "编辑 MCP");
        b.setView(root);
        b.setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                MCPConfig c = existing == null ? new MCPConfig() : existing;
                c.name = etName.getText().toString().trim();
                c.endpoint = etEndpoint.getText().toString().trim();
                c.description = etDescription.getText().toString().trim();
                int id = rgType.getCheckedRadioButtonId();
                c.type = id == R.id.rbWs ? "ws" : "http";
                if (TextUtils.isEmpty(c.name)) c.name = "MCP";
                config.saveMCP(c);
                if (existing == null) MCPManager.getInstance().connect(c);
                adapter.refresh();
            }
        });
        b.setNegativeButton(R.string.action_cancel, null);
        b.show();
    }

    private class MCPAdapter extends RecyclerView.Adapter<MCPAdapter.VH> {
        private List<MCPConfig> data;

        void refresh() {
            data = config.listMCPs();
            notifyDataSetChanged();
            emptyView.setVisibility(data.size() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(data.size() == 0 ? View.GONE : View.VISIBLE);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mcp, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            final MCPConfig c = data.get(position);
            h.title.setText(c.name);
            h.subtitle.setText(("http".equals(c.type) ? "HTTP/SSE" : "WebSocket") + " · " + c.endpoint);
            h.detail.setText(c.description);
            h.swEnabled.setOnCheckedChangeListener(null);
            h.swEnabled.setChecked(c.enabled);
            h.swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    c.enabled = on;
                    config.saveMCP(c);
                    if (on) MCPManager.getInstance().connect(c);
                    else MCPManager.getInstance().disconnect(c.id);
                }
            });
            h.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(MCPMgmtActivity.this)
                            .setTitle("卸载 MCP?")
                            .setMessage(c.name)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    MCPManager.getInstance().disconnect(c.id);
                                    config.deleteMCP(c.id);
                                    refresh();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            });
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { showEditor(c); }
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
}
