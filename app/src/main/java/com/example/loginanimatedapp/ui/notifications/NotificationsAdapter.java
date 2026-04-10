package com.example.loginanimatedapp.ui.notifications;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;
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
        Context context = holder.itemView.getContext();
        
        // GIẢI PHÁP CHỐNG CRASH: Kiểm tra Resource ID an toàn
        int iconRes = notification.getIconResId();
        
        try {
            // Thử nạp icon từ ID được lưu
            if (iconRes != 0) {
                holder.icon.setImageResource(iconRes);
            } else {
                holder.icon.setImageResource(R.drawable.ic_health_notification);
            }
        } catch (Resources.NotFoundException e) {
            // Nếu không tìm thấy ID (do ID cũ từ Firebase không khớp bản build mới)
            Log.w("NotificationsAdapter", "Resource ID not found: " + iconRes + ". Using default icon.");
            
            // TỰ ĐỘNG KHẮC PHỤC DỰA TRÊN TIÊU ĐỀ
            String title = notification.getTitle() != null ? notification.getTitle().toLowerCase() : "";
            if (title.contains("sos")) holder.icon.setImageResource(R.drawable.ic_notif_sos);
            else if (title.contains("ngã") || title.contains("té")) holder.icon.setImageResource(R.drawable.ic_notif_fall);
            else if (title.contains("nhiệt độ")) holder.icon.setImageResource(R.drawable.ic_notif_temperature);
            else if (title.contains("nhịp tim")) holder.icon.setImageResource(R.drawable.ic_notif_heart_rate);
            else if (title.contains("oxy") || title.contains("spo2")) holder.icon.setImageResource(R.drawable.ic_notif_spo2);
            else holder.icon.setImageResource(R.drawable.ic_health_notification);
        }

        holder.title.setText(notification.getTitle());
        holder.message.setText(notification.getMessage());
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault());
            holder.timestamp.setText(sdf.format(new Date(notification.getTimestamp())));
        } catch (Exception e) {
            holder.timestamp.setText("--:--");
        }

        if (!notification.isRead()) {
            holder.cardBackground.setBackgroundColor(Color.parseColor("#FFF3E0")); 
            holder.unreadDot.setVisibility(View.VISIBLE);
        } else {
            holder.cardBackground.setBackgroundColor(Color.WHITE);
            holder.unreadDot.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNotificationClick(notification, position);
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
