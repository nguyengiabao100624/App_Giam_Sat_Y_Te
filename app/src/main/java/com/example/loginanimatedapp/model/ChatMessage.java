package com.example.loginanimatedapp.model;

import android.graphics.Bitmap;

public class ChatMessage {
    private String message;
    private boolean isSentByUser;
    private Bitmap image;
    private String imageUri;
    private boolean isTyping;

    public ChatMessage(String message, boolean isSentByUser) {
        this.message = message;
        this.isSentByUser = isSentByUser;
    }

    public ChatMessage(String message, boolean isSentByUser, Bitmap image) {
        this.message = message;
        this.isSentByUser = isSentByUser;
        this.image = image;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSentByUser() {
        return isSentByUser;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImageBitmap(Bitmap image) {
        this.image = image;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }
}
