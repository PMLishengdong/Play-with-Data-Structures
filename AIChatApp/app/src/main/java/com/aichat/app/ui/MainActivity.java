package com.aichat.app.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aichat.app.R;
import com.aichat.app.model.Conversation;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.storage.ConfigManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * Home screen: shows the list of past conversations and a button to start a
 * new one. Also exposes quick links to Provider / MCP / Tool management.
 */
public class MainActivity extends AppCompatActivity {

    private ConfigManager config;
    private ConversationAdapter adapter;
    private TextView currentProviderLabel;
    private TextView emptyView;
    private RecyclerView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        config = ConfigManager.get(this);

        Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tb);

        currentProviderLabel = (TextView) findViewById(R.id.currentProviderLabel);
        emptyView = (TextView) findViewById(R.id.emptyView);
        list = (RecyclerView) findViewById(R.id.conversationList);
        list.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConversationAdapter();
        list.setAdapter(adapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fabNewChat);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startNewChat(); }
        });

        findViewById(R.id.btnSelectProvider).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showProviderPicker(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentProvider();
        adapter.refresh();
    }

    private void refreshCurrentProvider() {
        String id = config.getCurrentProviderId();
        if (id == null) {
            currentProviderLabel.setText("未选择提供商");
            return;
        }
        ProviderConfig p = config.getProvider(id);
        if (p == null) {
            currentProviderLabel.setText("未选择提供商");
            return;
        }
        String model = config.getCurrentModel();
        currentProviderLabel.setText(p.name + (TextUtils.isEmpty(model) ? "" : (" · " + model)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_mcp) {
            startActivity(new Intent(this, MCPMgmtActivity.class));
            return true;
        } else if (id == R.id.action_tools) {
            startActivity(new Intent(this, ToolMgmtActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startNewChat() {
        String id = config.getCurrentProviderId();
        ProviderConfig p = id == null ? null : config.getProvider(id);
        if (p == null) {
            Toast.makeText(this, R.string.msg_select_provider, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        String model = config.getCurrentModel();
        if (TextUtils.isEmpty(model)) {
            model = p.model;
        }
        if (TextUtils.isEmpty(model)) {
            Toast.makeText(this, R.string.msg_select_model, Toast.LENGTH_SHORT).show();
            return;
        }
        Conversation c = config.createConversation(p.id, model);
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, c.id);
        startActivity(i);
    }

    private void showProviderPicker() {
        final List<ProviderConfig> list = config.listProviders();
        if (list.size() == 0) {
            Toast.makeText(this, R.string.providers_no_items, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ProviderConfig p = list.get(i);
            items[i] = p.name + " (" + p.type + ")";
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.msg_select_provider_title);
        b.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ProviderConfig p = list.get(which);
                pickModelFor(p);
            }
        });
        b.show();
    }

    private void pickModelFor(final ProviderConfig p) {
        final EditText input = new EditText(this);
        input.setHint(p.model == null ? "model" : p.model);
        if (!TextUtils.isEmpty(p.model)) input.setText(p.model);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.msg_select_model_title);
        b.setMessage(p.name);
        b.setView(input);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String m = input.getText().toString().trim();
                if (m.length() == 0) m = p.model;
                config.setCurrentProviderId(p.id, m);
                refreshCurrentProvider();
            }
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    private class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {
        private List<Conversation> data;

        void refresh() {
            data = config.listConversations();
            notifyDataSetChanged();
            emptyView.setVisibility(data.size() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(data.size() == 0 ? View.GONE : View.VISIBLE);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conversation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            final Conversation c = data.get(position);
            h.title.setText(c.title == null ? "新对话" : c.title);
            ProviderConfig p = c.providerId == null ? null : config.getProvider(c.providerId);
            String sub = (p == null ? "未指定" : p.name) + (c.model == null ? "" : " · " + c.model);
            h.subtitle.setText(sub);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(MainActivity.this, ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, c.id);
                    startActivity(i);
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("删除对话?")
                            .setMessage(c.title)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    config.deleteConversation(c.id);
                                    refresh();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            VH(View v) {
                super(v);
                title = (TextView) v.findViewById(R.id.tvTitle);
                subtitle = (TextView) v.findViewById(R.id.tvSubtitle);
            }
        }
    }
}
