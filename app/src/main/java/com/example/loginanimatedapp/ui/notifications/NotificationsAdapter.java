package com.example.loginanimatedapp.ui.notifications;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.model.Notification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    private List<Notification> notifications;
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification, int position);
    }

    public NotificationsAdapter(List<Notification> notifications, OnNotificationClickListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.icon.setImageResource(notification.getIconResId());
        holder.title.setText(notification.getTitle());
        holder.message.setText(notification.getMessage());
        holder.timestamp.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(notification.getTimestamp())));

        if (!notification.isRead()) {
            holder.cardBackground.setBackgroundColor(Color.parseColor("#E3F2FD")); // Light blue
            holder.unreadDot.setVisibility(View.VISIBLE);
        } else {
            holder.cardBackground.setBackgroundColor(Color.WHITE);
            holder.unreadDot.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(notification, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView message;
        TextView timestamp;
        View unreadDot;
        LinearLayout cardBackground;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_notification_icon);
            title = itemView.findViewById(R.id.tv_notification_title);
            message = itemView.findViewById(R.id.tv_notification_message);
            timestamp = itemView.findViewById(R.id.tv_notification_timestamp);
            unreadDot = itemView.findViewById(R.id.view_unread_dot);
            cardBackground = itemView.findViewById(R.id.layout_notification_item);
        }
    }
}
