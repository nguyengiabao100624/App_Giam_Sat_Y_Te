package com.example.loginanimatedapp.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loginanimatedapp.model.ChatMessage;
import com.google.ai.client.generativeai.java.ChatFutures;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    private ChatFutures chatFutures;

    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }

    public void addMessage(ChatMessage message) {
        List<ChatMessage> currentMessages = chatMessages.getValue();
        if (currentMessages != null) {
            currentMessages.add(message);
            chatMessages.setValue(currentMessages);
        }
    }

    public void removeLastMessage() {
        List<ChatMessage> currentMessages = chatMessages.getValue();
        if (currentMessages != null && !currentMessages.isEmpty()) {
            currentMessages.remove(currentMessages.size() - 1);
            chatMessages.setValue(currentMessages);
        }
    }

    public ChatFutures getChatFutures() {
        return chatFutures;
    }

    public void setChatFutures(ChatFutures chatFutures) {
        this.chatFutures = chatFutures;
    }

    public boolean isChatInitialized() {
        return chatFutures != null;
    }
}
