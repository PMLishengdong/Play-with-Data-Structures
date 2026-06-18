package com.aichat.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aichat.app.R;
import com.aichat.app.chat.ChatManager;
import com.aichat.app.markdown.MarkdownRenderer;
import com.aichat.app.model.ChatMessage;
import com.aichat.app.model.Conversation;
import com.aichat.app.model.MessageRole;
import com.aichat.app.model.ProviderConfig;
import com.aichat.app.storage.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat screen. Streams assistant text from {@link ChatManager} into a markdown
 * rendered TextView without using WebView. Persists messages through
 * {@link ConfigManager} so the conversation survives app restarts.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    private ConfigManager config;
    private String conversationId;
    private Conversation conversation;
    private ProviderConfig provider;

    private RecyclerView messageList;
    private MessageAdapter adapter;
    private EditText inputBox;
    private ImageButton btnSend;
    private ImageButton btnStop;
    private TextView emptyView;
    private TextView subtitleBar;

    private ChatManager chatManager;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MarkdownRenderer currentRenderer;
    private boolean streaming;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        config = ConfigManager.get(this);

        Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        if (conversationId == null) conversationId = config.getCurrentConversationId();
        if (conversationId == null) {
            finish();
            return;
        }
        conversation = config.getConversation(conversationId);
        if (conversation == null) {
            finish();
            return;
        }
        provider = conversation.providerId == null ? null : config.getProvider(conversation.providerId);

        messageList = (RecyclerView) findViewById(R.id.messageList);
        messageList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);

        emptyView = (TextView) findViewById(R.id.emptyView);
        subtitleBar = (TextView) findViewById(R.id.subtitleBar);
        inputBox = (EditText) findViewById(R.id.inputBox);
        btnSend = (ImageButton) findViewById(R.id.btnSend);
        btnStop = (ImageButton) findViewById(R.id.btnStop);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onSend(); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { stopStreaming(); }
        });
        inputBox.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    onSend();
                    return true;
                }
                return false;
            }
        });

        refreshSubtitle();
        loadMessages();
    }

    private void refreshSubtitle() {
        String pname = provider == null ? "未配置" : provider.name;
        subtitleBar.setText(pname + (conversation.model == null ? "" : (" · " + conversation.model)));
    }

    private void loadMessages() {
        List<ChatMessage> list = config.loadMessages(conversationId);
        adapter.setMessages(list);
        messageList.post(new Runnable() {
            @Override
            public void run() { messageList.scrollToPosition(adapter.getItemCount() - 1); }
        });
        emptyView.setVisibility(list.size() == 0 ? View.VISIBLE : View.GONE);
    }

    private void onSend() {
        if (streaming) return;
        if (provider == null) {
            Toast.makeText(this, R.string.msg_select_provider, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = inputBox.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputBox.setText("");

        ChatMessage m = new ChatMessage(MessageRole.USER, text);
        adapter.appendMessage(m);
        config.saveMessages(conversationId, adapter.getMessages());
        messageList.scrollToPosition(adapter.getItemCount() - 1);
        emptyView.setVisibility(View.GONE);
        if ("新对话".equals(conversation.title)) {
            String t = text.length() > 30 ? text.substring(0, 30) : text;
            conversation.title = t;
            config.saveConversation(conversation);
        }
        startStreaming();
    }

    private void startStreaming() {
        streaming = true;
        btnSend.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
        currentRenderer = new MarkdownRenderer(this);
        // Add a streaming assistant placeholder
        ChatMessage placeholder = new ChatMessage(MessageRole.ASSISTANT, "");
        int pos = adapter.appendMessage(placeholder);
        config.saveMessages(conversationId, adapter.getMessages());
        messageList.scrollToPosition(adapter.getItemCount() - 1);

        final List<ChatMessage> history = new ArrayList<ChatMessage>(adapter.getMessages());
        // Remove last (placeholder) from the history we send
        final List<ChatMessage> sendHistory;
        if (history.size() > 0) sendHistory = new ArrayList<ChatMessage>(history.subList(0, history.size() - 1));
        else sendHistory = history;

        chatManager = new ChatManager(config);
        chatManager.send(sendHistory, provider, new ChatManager.Listener() {
            @Override
            public void onText(String text) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (currentRenderer == null) return;
                        currentRenderer.appendBlock(text);
                        ChatMessage m = adapter.getMessages().get(adapter.getItemCount() - 1);
                        m.content = currentRenderer.getText().toString();
                        adapter.notifyItemChanged(adapter.getItemCount() - 1);
                        messageList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onToolCallStart(final String toolId, final String name) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        // Insert a system message in UI describing the call
                        ChatMessage m = new ChatMessage(MessageRole.SYSTEM, "调用工具: " + name + " (" + toolId + ")");
                        adapter.insertBeforeLast(m);
                        config.saveMessages(conversationId, adapter.getMessages());
                        messageList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onToolCallArgs(final String toolId, final String args) {
                // Optional: show args preview
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        // No-op for brevity
                    }
                });
            }

            @Override
            public void onToolCallResult(final String toolId, final String result) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        ChatMessage m = new ChatMessage(MessageRole.TOOL, truncate(result, 2000));
                        m.toolCallId = toolId;
                        adapter.insertBeforeLast(m);
                        config.saveMessages(conversationId, adapter.getMessages());
                        messageList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onComplete() {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        streaming = false;
                        btnSend.setVisibility(View.VISIBLE);
                        btnStop.setVisibility(View.GONE);
                        if (currentRenderer != null) {
                            currentRenderer.finalizeStream();
                            ChatMessage m = adapter.getMessages().get(adapter.getItemCount() - 1);
                            m.content = currentRenderer.getText().toString();
                            adapter.notifyItemChanged(adapter.getItemCount() - 1);
                        }
                        config.saveMessages(conversationId, adapter.getMessages());
                    }
                });
            }

            @Override
            public void onError(final Throwable error) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        streaming = false;
                        btnSend.setVisibility(View.VISIBLE);
                        btnStop.setVisibility(View.GONE);
                        if (currentRenderer != null) {
                            currentRenderer.finalizeStream();
                        }
                        ChatMessage m = adapter.getMessages().get(adapter.getItemCount() - 1);
                        if (m.content == null || m.content.length() == 0) {
                            m.content = getString(R.string.msg_error, String.valueOf(error.getMessage()));
                            adapter.notifyItemChanged(adapter.getItemCount() - 1);
                        } else {
                            m.content = m.content + "\n\n" + getString(R.string.msg_error, String.valueOf(error.getMessage()));
                            adapter.notifyItemChanged(adapter.getItemCount() - 1);
                        }
                        config.saveMessages(conversationId, adapter.getMessages());
                    }
                });
            }
        });
    }

    private void stopStreaming() {
        if (chatManager != null) chatManager.stop();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n) + "…";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatManager != null) chatManager.stop();
    }

    // ---------- Adapter ----------

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        private final List<ChatMessage> data = new ArrayList<ChatMessage>();

        void setMessages(List<ChatMessage> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        List<ChatMessage> getMessages() { return data; }

        int appendMessage(ChatMessage m) {
            data.add(m);
            notifyItemInserted(data.size() - 1);
            return data.size() - 1;
        }

        void insertBeforeLast(ChatMessage m) {
            int idx = data.size() - 1;
            if (idx < 0) { appendMessage(m); return; }
            data.add(idx, m);
            notifyItemInserted(idx);
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage m = data.get(position);
            if (MessageRole.USER.equals(m.role)) return 0;
            if (MessageRole.ASSISTANT.equals(m.role)) return 1;
            if (MessageRole.TOOL.equals(m.role)) return 2;
            return 3;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            ChatMessage m = data.get(position);
            int type = getItemViewType(position);
            if (type == 0) {
                h.tvRole.setText("我");
                h.tvRole.setTextColor(0xFF1565C0);
                h.tvContent.setBackgroundResource(R.drawable.bg_user_bubble);
            } else if (type == 1) {
                h.tvRole.setText("AI");
                h.tvRole.setTextColor(0xFF2E7D32);
                h.tvContent.setBackgroundResource(R.drawable.bg_assistant_bubble);
                if (currentRenderer != null && m == data.get(data.size() - 1) && streaming) {
                    h.tvContent.setText(currentRenderer.getText());
                    h.tvContent.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                } else {
                    MarkdownRenderer r = new MarkdownRenderer(ChatActivity.this);
                    r.appendBlock(m.content == null ? "" : m.content);
                    r.finalizeStream();
                    h.tvContent.setText(r.getText());
                    h.tvContent.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                }
                return;
            } else if (type == 2) {
                h.tvRole.setText("工具结果");
                h.tvRole.setTextColor(0xFF6A1B9A);
                h.tvContent.setBackgroundResource(R.drawable.bg_tool_bubble);
            } else {
                h.tvRole.setText("系统");
                h.tvRole.setTextColor(0xFFE65100);
                h.tvContent.setBackgroundResource(R.drawable.bg_system_bubble);
            }
            h.tvContent.setText(m.content == null ? "" : m.content);
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvRole;
            TextView tvContent;
            VH(View v) {
                super(v);
                tvRole = (TextView) v.findViewById(R.id.tvRole);
                tvContent = (TextView) v.findViewById(R.id.tvContent);
            }
        }
    }
}
