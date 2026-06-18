package com.example.aichat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aichat.R;
import com.example.aichat.model.Message;
import com.example.aichat.util.MarkdownRenderer;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;

    private List<Message> messages;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void updateMessage(String messageId, String content) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) {
                messages.get(i).setContent(content);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateMessageStatus(String messageId, int status) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) {
                messages.get(i).setStatus(status);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUserMessage() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_USER) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_user, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_ai, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageTextView;
        private TextView statusTextView;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.tv_user_message);
            if (messageTextView == null) {
                messageTextView = itemView.findViewById(R.id.tv_ai_message);
            }
            statusTextView = itemView.findViewById(R.id.tv_status);
        }

        public void bind(Message message) {
            if (message.isAIMessage()) {
                messageTextView.setText(MarkdownRenderer.render(message.getContent()));
                if (statusTextView != null) {
                    switch (message.getStatus()) {
                        case Message.STATUS_RECEIVING:
                            statusTextView.setText(R.string.loading);
                            statusTextView.setVisibility(View.VISIBLE);
                            break;
                        case Message.STATUS_RECEIVED:
                            statusTextView.setVisibility(View.GONE);
                            break;
                        case Message.STATUS_ERROR:
                            statusTextView.setText("Error");
                            statusTextView.setVisibility(View.VISIBLE);
                            break;
                        case Message.STATUS_CANCELLED:
                            statusTextView.setText("Cancelled");
                            statusTextView.setVisibility(View.VISIBLE);
                            break;
                        default:
                            statusTextView.setVisibility(View.GONE);
                    }
                }
            } else {
                messageTextView.setText(message.getContent());
            }
        }
    }
}
