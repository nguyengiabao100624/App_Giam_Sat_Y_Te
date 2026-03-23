package com.example.loginanimatedapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.model.ChatMessage;

import java.util.List;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> chatMessages;
    private Markwon markwon;

    public ChatAdapter(List<ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) {
            markwon = Markwon.create(parent.getContext());
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new ChatViewHolder(view, markwon);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages.get(position).isSentByUser()) {
            return R.layout.item_chat_sent;
        } else {
            return R.layout.item_chat_received;
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final Markwon markwon;

        public ChatViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            this.markwon = markwon;
            messageText = itemView.findViewById(R.id.tv_message);
        }

        void bind(ChatMessage chatMessage) {
            markwon.setMarkdown(messageText, chatMessage.getMessage());
        }
    }
}
