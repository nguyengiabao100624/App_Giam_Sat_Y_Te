package com.example.loginanimatedapp.model;

import java.io.Serializable;

public class Notification implements Serializable {
    private String id;
    private String title;
    private String message;
    private long timestamp;
    private int iconResId;
    private boolean isRead;
    private String metricValue;

    // Constructor mặc định cho Firebase
    public Notification() {
    }

    public Notification(String title, String message, long timestamp, int iconResId) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.iconResId = iconResId;
        this.isRead = false;
    }

    public Notification(String id, String title, String message, long timestamp, int iconResId, String metricValue) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.iconResId = iconResId;
        this.isRead = false;
        this.metricValue = metricValue;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public String getMetricValue() { return metricValue; }
    public void setMetricValue(String metricValue) { this.metricValue = metricValue; }
}
