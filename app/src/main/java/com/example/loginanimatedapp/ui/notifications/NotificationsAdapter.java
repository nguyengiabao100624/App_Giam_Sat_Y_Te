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
        void onNotificationLongClick(Notification notification, int position);
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
        
        // Sử dụng iconResId từ model (đã được NotificationService gán icon tùy biến)
        int iconRes = notification.getIconResId();
        if (iconRes == 0) iconRes = R.drawable.ic_health_notification;
        holder.icon.setImageResource(iconRes);
        
        holder.title.setText(notification.getTitle());
        holder.message.setText(notification.getMessage());
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(notification.getTimestamp())));

        if (!notification.isRead()) {
            holder.cardBackground.setBackgroundColor(Color.parseColor("#FFF3E0")); // Light orange
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

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onNotificationLongClick(notification, position);
                return true;
            }
            return false;
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
