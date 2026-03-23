package com.example.loginanimatedapp.service;

import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.loginanimatedapp.DashboardActivity;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.model.Notification;
import com.example.loginanimatedapp.utils.AppConstants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "HEALTH_ALERT_CHANNEL";
    private static final int SERVICE_ID = 1001;
    private DatabaseReference deviceRef;
    private ValueEventListener deviceListener;
    private String lastAlertTime = "";
    
    // Quản lý thời gian gửi cho từng loại
    private long lastSosSent = 0;
    private long lastFallSent = 0;
    private long lastHealthSent = 0;
    private String lastHealthReason = "";

    // NGƯỠNG THỜI GIAN (COOLDOWN) - Đã cập nhật tất cả là 10 giây theo yêu cầu
    private static final long GLOBAL_SPAM_DELAY = 10000; // 10 giây (Áp dụng cho mọi loại cảnh báo)

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        setupFirebaseListener();
        return START_STICKY;
    }

    private void startForegroundService() {
        String channelId = "SERVICE_CHANNEL";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Giám sát sức khỏe",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Healthy 365 đang bảo vệ bạn")
                .setContentText("Hệ thống giám sát đang chạy...")
                .setSmallIcon(R.drawable.ic_check_circle_green)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SERVICE_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_ID, builder.build());
        }
    }

    private void setupFirebaseListener() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String deviceId = prefs.getString("connected_device_id", "");
        if (deviceId.isEmpty()) return;

        if (deviceRef != null && deviceListener != null) {
            deviceRef.removeEventListener(deviceListener);
        }

        lastAlertTime = getSharedPreferences("AlertPrefs", MODE_PRIVATE).getString("last_sent_time", "");

        deviceRef = FirebaseDatabase.getInstance(AppConstants.DATABASE_URL)
                .getReference("Devices").child(deviceId);

        deviceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        Object value = snapshot.getValue();
                        if (value instanceof Map) {
                            handleAlerts((Map<String, Object>) value);
                        }
                    } catch (Exception e) {
                        Log.e("NotificationService", "Error", e);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        deviceRef.addValueEventListener(deviceListener);
    }

    private void handleAlerts(Map<String, Object> data) {
        if (data == null) return;

        // Nếu đang mở App thì không spam thông báo hệ thống
        if (isAppOnForeground()) return;

        boolean isSOS = Boolean.TRUE.equals(data.get("Alert_SOS"));
        boolean isFall = Boolean.TRUE.equals(data.get("Alert_Fall"));
        boolean isHealth = Boolean.TRUE.equals(data.get("Alert_Health"));
        String thoiGian = String.valueOf(data.getOrDefault("ThoiGian", ""));

        // LƯU Ý: Cho phép spam (bỏ qua thoiGian) nếu thiết bị đang trong trạng thái cảnh báo
        if (isSOS) {
            sendAlert("SOS KHẨN CẤP!", "Người thân vừa nhấn nút hỗ trợ!", "sos", "SOS", thoiGian);
        } else if (isFall) {
            sendAlert("PHÁT HIỆN TÉ NGÃ!", "Cảnh báo: Có va chạm mạnh/té ngã!", "fall", "Té ngã", thoiGian);
        } else if (isHealth) {
            String rawReason = String.valueOf(data.getOrDefault("Alert_Reason", ""));
            String value = "--";
            if (rawReason.contains("Nhip tim")) value = data.getOrDefault("BPM", "0") + " bpm";
            else if (rawReason.contains("SpO2")) value = data.getOrDefault("SpO2", "0") + " %";
            else if (rawReason.contains("Nhiet do")) value = data.getOrDefault("TempObj", "0") + " °C";
            else if (rawReason.contains("o nhiem")) value = data.getOrDefault("Dust", "0") + " µg/m³";

            sendAlert("CẢNH BÁO SỨC KHỎE!", mapReason(rawReason), "health", value, thoiGian);
        }
    }

    private void sendAlert(String title, String content, String type, String value, String timeKey) {
        long now = System.currentTimeMillis();
        
        // KIỂM TRA COOLDOWN ĐỒNG NHẤT 10 GIÂY
        if ("sos".equals(type)) {
            if (now - lastSosSent < GLOBAL_SPAM_DELAY) return;
            lastSosSent = now;
        } else if ("fall".equals(type)) {
            if (now - lastFallSent < GLOBAL_SPAM_DELAY) return;
            lastFallSent = now;
        } else if ("health".equals(type)) {
            // Với cảnh báo sức khỏe, nếu cùng một lý do thì chờ 10s mới gửi tiếp
            if (content.equals(lastHealthReason) && (now - lastHealthSent < GLOBAL_SPAM_DELAY)) return;
            lastHealthSent = now;
            lastHealthReason = content;
        }

        lastAlertTime = timeKey;
        getSharedPreferences("AlertPrefs", MODE_PRIVATE).edit().putString("last_sent_time", timeKey).apply();

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference notifRef = FirebaseDatabase.getInstance(AppConstants.DATABASE_URL)
                .getReference("notifications").child(uid);
        
        String id = notifRef.push().getKey();
        int icon = R.drawable.ic_error_circle_red;
        if ("health".equals(type)) icon = R.drawable.ic_info_circle_blue;
        
        Notification n = new Notification(id, title, content, System.currentTimeMillis(), icon, value);
        if (id != null) {
            notifRef.child(id).setValue(n);
            showSystemNotification(n);
        }
    }

    private boolean isAppOnForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) return false;
        final String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void showSystemNotification(Notification n) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("notification_obj", n);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pi = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_error_circle_red)
                .setContentTitle(n.getTitle())
                .setContentText(n.getMessage())
                .setPriority(NotificationCompat.PRIORITY_MAX) 
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 1000, 500, 1000});

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(n.getId().hashCode(), builder.build());
    }

    private String mapReason(String raw) {
        if (raw == null || raw.isEmpty()) return "Phát hiện chỉ số bất thường";
        String s = raw.toLowerCase();
        if (s.contains("nhip tim")) return "Nhịp tim đang ở mức bất thường.";
        if (s.contains("oxy") || s.contains("spo2")) return "Nồng độ Oxy (SpO2) đang xuống thấp.";
        if (s.contains("nhiet do da qua thap")) return "Nhiệt độ cơ thể đang ở mức báo động thấp.";
        if (s.contains("nhiet do cao") || s.contains("sot")) return "Phát hiện đang bị sốt cao.";
        if (s.contains("o nhiem")) return "Không khí ô nhiễm nặng.";
        return raw;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (deviceRef != null && deviceListener != null) deviceRef.removeEventListener(deviceListener);
    }
}
