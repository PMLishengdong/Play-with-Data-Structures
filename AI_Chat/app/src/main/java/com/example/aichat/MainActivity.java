package com.example.aichat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aichat.adapter.ChatAdapter;
import com.example.aichat.ai.ChatService;
import com.example.aichat.ai.StreamCallback;
import com.example.aichat.model.AIModel;
import com.example.aichat.model.Message;
import com.example.aichat.util.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<Message> messages;
    private EditText etMessage;
    private Button btnSend;
    private Button btnStop;
    private Button btnSettings;
    private TextView tvToolbarTitle;

    private ChatService chatService;
    private ConfigManager configManager;

    private String currentStreamingMessageId;
    private Map<String, StringBuilder> streamingContents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatService = ChatService.getInstance(this);
        configManager = ConfigManager.getInstance(this);
        streamingContents = new HashMap<String, StringBuilder>();

        recyclerView = findViewById(R.id.recycler_view);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnStop = findViewById(R.id.btn_stop);
        btnSettings = findViewById(R.id.btn_settings);
        tvToolbarTitle = findViewById(R.id.tv_toolbar_title);

        messages = new ArrayList<Message>();
        chatAdapter = new ChatAdapter(messages);
        recyclerView.setAdapter(chatAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        updateToolbarTitle();

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGeneration();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        etMessage.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
    }

    private void updateToolbarTitle() {
        AIModel model = configManager.getSelectedModel();
        tvToolbarTitle.setText(getString(R.string.app_name) + " - " + model.getName());
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            return;
        }

        etMessage.setText("");
        btnSend.setEnabled(false);

        String userMessageId = UUID.randomUUID().toString();
        Message userMessage = new Message(userMessageId, Message.TYPE_USER, content);
        messages.add(userMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        String aiMessageId = UUID.randomUUID().toString();
        Message aiMessage = new Message(aiMessageId, Message.TYPE_AI, "");
        aiMessage.setStatus(Message.STATUS_RECEIVING);
        messages.add(aiMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        currentStreamingMessageId = aiMessageId;
        streamingContents.put(aiMessageId, new StringBuilder());

        btnStop.setVisibility(View.VISIBLE);

        chatService.sendMessage(content, new ArrayList<Message>(messages.subList(0, messages.size() - 1)),
                new StreamCallback() {
                    @Override
                    public void onMessageId(String messageId, String id) {}

                    @Override
                    public void onContent(String messageId, String content) {
                        if (!messageId.equals(currentStreamingMessageId)) {
                            return;
                        }
                        StringBuilder sb = streamingContents.get(messageId);
                        if (sb != null) {
                            sb.append(content);
                            chatAdapter.updateMessage(messageId, sb.toString());
                            scrollToBottom();
                        }
                    }

                    @Override
                    public void onComplete(String messageId) {
                        if (!messageId.equals(currentStreamingMessageId)) {
                            return;
                        }
                        chatAdapter.updateMessageStatus(messageId, Message.STATUS_RECEIVED);
                        btnSend.setEnabled(true);
                        btnStop.setVisibility(View.GONE);
                        currentStreamingMessageId = null;
                        streamingContents.remove(messageId);
                    }

                    @Override
                    public void onError(String messageId, Exception e) {
                        chatAdapter.updateMessageStatus(messageId, Message.STATUS_ERROR);
                        btnSend.setEnabled(true);
                        btnStop.setVisibility(View.GONE);
                        currentStreamingMessageId = null;
                        streamingContents.remove(messageId);
                    }
                });
    }

    private void stopGeneration() {
        if (currentStreamingMessageId != null) {
            chatService.stop(currentStreamingMessageId);
            chatAdapter.updateMessageStatus(currentStreamingMessageId, Message.STATUS_CANCELLED);
            btnSend.setEnabled(true);
            btnStop.setVisibility(View.GONE);
            currentStreamingMessageId = null;
        }
    }

    private void scrollToBottom() {
        recyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        }, 100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToolbarTitle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        chatService.stopAll();
    }
}
