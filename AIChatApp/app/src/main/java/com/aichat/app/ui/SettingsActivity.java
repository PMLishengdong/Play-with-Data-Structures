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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aichat.app.R;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.storage.ConfigManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * Provider management screen. Add, edit, toggle and delete AI provider configs.
 */
public class SettingsActivity extends AppCompatActivity {

    private ConfigManager config;
    private ProviderAdapter adapter;
    private RecyclerView list;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        config = ConfigManager.get(this);

        Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        list = (RecyclerView) findViewById(R.id.providerList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter();
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

    private void showEditor(final ProviderConfig existing) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_provider, null, false);
        final EditText etName = (EditText) root.findViewById(R.id.etName);
        final EditText etBaseUrl = (EditText) root.findViewById(R.id.etBaseUrl);
        final EditText etApiKey = (EditText) root.findViewById(R.id.etApiKey);
        final EditText etModel = (EditText) root.findViewById(R.id.etModel);
        final RadioGroup rgType = (RadioGroup) root.findViewById(R.id.rgType);
        final RadioButton rbOpen = (RadioButton) root.findViewById(R.id.rbOpenAI);
        final RadioButton rbAnt = (RadioButton) root.findViewById(R.id.rbAnthropic);
        final RadioButton rbCust = (RadioButton) root.findViewById(R.id.rbCustom);

        if (existing != null) {
            etName.setText(existing.name);
            etBaseUrl.setText(existing.baseUrl);
            etApiKey.setText(existing.apiKey);
            etModel.setText(existing.model);
            if ("anthropic".equals(existing.type)) rbAnt.setChecked(true);
            else if ("custom".equals(existing.type)) rbCust.setChecked(true);
            else rbOpen.setChecked(true);
        } else {
            etBaseUrl.setText("https://api.openai.com/v1");
            etModel.setText("gpt-4o-mini");
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(existing == null ? "新增提供商" : "编辑提供商");
        b.setView(root);
        b.setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                ProviderConfig p = existing == null ? new ProviderConfig() : existing;
                p.name = etName.getText().toString().trim();
                p.baseUrl = etBaseUrl.getText().toString().trim();
                p.apiKey = etApiKey.getText().toString().trim();
                p.model = etModel.getText().toString().trim();
                int id = rgType.getCheckedRadioButtonId();
                if (id == R.id.rbAnthropic) p.type = "anthropic";
                else if (id == R.id.rbCustom) p.type = "custom";
                else p.type = "openai";
                if (TextUtils.isEmpty(p.name)) p.name = "未命名";
                config.saveProvider(p);
                if (existing == null && config.getCurrentProviderId() == null) {
                    config.setCurrentProviderId(p.id, p.model);
                }
                adapter.refresh();
            }
        });
        b.setNegativeButton(R.string.action_cancel, null);
        b.show();
    }

    private class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.VH> {
        private List<ProviderConfig> data;

        void refresh() {
            data = config.listProviders();
            notifyDataSetChanged();
            emptyView.setVisibility(data.size() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(data.size() == 0 ? View.GONE : View.VISIBLE);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            final ProviderConfig p = data.get(position);
            h.title.setText(p.name + (p.model == null ? "" : (" · " + p.model)));
            h.subtitle.setText(typeLabel(p.type) + " · " + p.baseUrl);
            h.detail.setText("API Key: " + maskKey(p.apiKey));
            h.swEnabled.setOnCheckedChangeListener(null);
            h.swEnabled.setChecked(p.enabled);
            h.swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    p.enabled = on;
                    config.saveProvider(p);
                }
            });
            h.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("删除提供商?")
                            .setMessage(p.name)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    config.deleteProvider(p.id);
                                    refresh();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            });
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { showEditor(p); }
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
        if ("anthropic".equals(t)) return "Anthropic";
        if ("custom".equals(t)) return "Custom";
        return "OpenAI";
    }

    private static String maskKey(String k) {
        if (k == null || k.length() == 0) return "(空)";
        if (k.length() <= 6) return "******";
        return k.substring(0, 3) + "****" + k.substring(k.length() - 3);
    }
}
