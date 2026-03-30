package com.example.loginanimatedapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
        if (chatMessages != null && position < chatMessages.size()) {
            holder.bind(chatMessages.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return chatMessages != null ? chatMessages.size() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages != null && chatMessages.get(position).isSentByUser()) {
            return R.layout.item_chat_sent;
        } else {
            return R.layout.item_chat_received;
        }
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final ImageView chatImage;
        private final Markwon markwon;
        private final ProgressBar pbThinking;
        private final View layoutBotIcon;

        public ChatViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            this.markwon = markwon;
            messageText = itemView.findViewById(R.id.tv_message);
            chatImage = itemView.findViewById(R.id.iv_chat_image);
            pbThinking = itemView.findViewById(R.id.pb_bot_thinking);
            layoutBotIcon = itemView.findViewById(R.id.layout_bot_icon);
        }

        public void bind(ChatMessage chatMessage) {
            if (chatMessage.isTyping()) {
                if (messageText != null) messageText.setVisibility(View.GONE);
                if (pbThinking != null) pbThinking.setVisibility(View.VISIBLE);
                if (layoutBotIcon != null) layoutBotIcon.setVisibility(View.VISIBLE);
            } else {
                if (pbThinking != null) pbThinking.setVisibility(View.GONE);
                if (chatMessage.getMessage() == null || chatMessage.getMessage().isEmpty()) {
                    if (messageText != null) messageText.setVisibility(View.GONE);
                } else {
                    if (messageText != null) {
                        messageText.setVisibility(View.VISIBLE);
                        markwon.setMarkdown(messageText, chatMessage.getMessage());
                    }
                }
            }

            if (chatImage != null) {
                if (chatMessage.getImage() != null) {
                    chatImage.setVisibility(View.VISIBLE);
                    chatImage.setImageBitmap(chatMessage.getImage());
                } else {
                    chatImage.setVisibility(View.GONE);
                }
            }
        }
    }
}
