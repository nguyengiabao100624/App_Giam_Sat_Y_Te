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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.DashboardActivity;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "HEALTH_365_ALERTS_V4"; 
    private static final int SERVICE_ID = 1001;
    private DatabaseReference deviceRef;
    private ValueEventListener deviceListener;
    
    private long lastSosSent = 0;
    private long lastFallSent = 0;
    private long lastHealthSent = 0;
    private String lastHealthReason = "";
    private static final long GLOBAL_SPAM_DELAY = 10000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        setupFirebaseListener();
        return START_STICKY;
    }

    private void startForegroundService() {
        String channelId = "SERVICE_CHANNEL_V4";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Giám sát sức khỏe", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Healthy 365 đang bảo vệ bạn")
                .setContentText("Hệ thống giám sát đang chạy...")
                .setSmallIcon(R.drawable.ic_health_notification)
                .setColor(ContextCompat.getColor(this, R.color.orange_main))
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
        deviceRef = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL).getReference("Devices").child(deviceId);
        deviceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        Object value = snapshot.getValue();
                        if (value instanceof Map) handleAlerts((Map<String, Object>) value);
                    } catch (Exception e) { Log.e("NotificationService", "Error", e); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        deviceRef.addValueEventListener(deviceListener);
    }

    private void handleAlerts(Map<String, Object> data) {
        if (data == null || isAppOnForeground()) return;
        boolean isSOS = Boolean.TRUE.equals(data.get("Alert_SOS"));
        boolean isFall = Boolean.TRUE.equals(data.get("Alert_Fall"));
        boolean isHealth = Boolean.TRUE.equals(data.get("Alert_Health"));
        String thoiGian = String.valueOf(data.getOrDefault("ThoiGian", ""));

        if (isSOS) {
            sendAlert("SOS KHẨN CẤP!", "Người thân vừa nhấn nút hỗ trợ!", "sos", "SOS", thoiGian, R.drawable.ic_notif_sos);
        } else if (isFall) {
            sendAlert("PHÁT HIỆN TÉ NGÃ!", "Cảnh báo: Có va chạm mạnh/té ngã!", "fall", "Té ngã", thoiGian, R.drawable.ic_notif_fall);
        } else if (isHealth) {
            String rawReason = String.valueOf(data.getOrDefault("Alert_Reason", ""));
            String lowerReason = rawReason.toLowerCase();
            String valueToShow = "--";
            int iconRes = R.drawable.ic_health_notification;
            
            if (lowerReason.contains("nhiet do") || lowerReason.contains("sot")) {
                valueToShow = String.valueOf(data.getOrDefault("TempObj", "--")) + " °C";
                iconRes = R.drawable.ic_notif_temperature;
            } else if (lowerReason.contains("nhip tim")) {
                valueToShow = String.valueOf(data.getOrDefault("BPM", "--")) + " bpm";
                iconRes = R.drawable.ic_notif_heart_rate;
            } else if (lowerReason.contains("oxy") || lowerReason.contains("spo2")) {
                valueToShow = String.valueOf(data.getOrDefault("SpO2", "--")) + " %";
                iconRes = R.drawable.ic_notif_spo2;
            }

            sendAlert("CẢNH BÁO SỨC KHỎE!", mapReason(rawReason), "health", valueToShow, thoiGian, iconRes);
        }
    }

    private void sendAlert(String title, String content, String type, String value, String timeKey, int iconRes) {
        long now = System.currentTimeMillis();
        if ("sos".equals(type) && now - lastSosSent < GLOBAL_SPAM_DELAY) return;
        if ("fall".equals(type) && now - lastFallSent < GLOBAL_SPAM_DELAY) return;
        if ("health".equals(type) && content.equals(lastHealthReason) && (now - lastHealthSent < GLOBAL_SPAM_DELAY)) return;

        if ("sos".equals(type)) lastSosSent = now;
        else if ("fall".equals(type)) lastFallSent = now;
        else if ("health".equals(type)) { lastHealthSent = now; lastHealthReason = content; }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference notifRef = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL).getReference("notifications").child(uid);
        String id = notifRef.push().getKey();
        Notification n = new Notification(id, title, content, System.currentTimeMillis(), iconRes, value);
        if (id != null) {
            notifRef.child(id).setValue(n);
            showSystemNotification(n);
        }
    }

    private boolean isAppOnForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && process.processName.equals(getPackageName())) return true;
        }
        return false;
    }

    private void showSystemNotification(Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Cảnh báo khẩn cấp", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("notification_obj", n);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pi = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(n.getIconResId()) // Sử dụng icon tùy biến theo loại cảnh báo
                .setContentTitle(n.getTitle())
                .setContentText(n.getMessage())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(ContextCompat.getColor(this, R.color.orange_main))
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(n.getId().hashCode(), builder.build());
    }

    private String mapReason(String raw) {
        if (raw == null) return "Phát hiện chỉ số sức khỏe bất thường.";
        String lower = raw.toLowerCase();
        
        if (lower.contains("nhip tim") && lower.contains("cao")) return "Nhịp tim đang ở mức cao bất thường.";
        if (lower.contains("nhip tim") && lower.contains("thap")) return "Nhịp tim đang ở mức thấp bất thường.";
        if (lower.contains("oxy") || lower.contains("spo2")) return "Nồng độ Oxy trong máu (SpO2) đang ở mức thấp.";
        if (lower.contains("sot") || (lower.contains("nhiet do") && lower.contains("cao"))) return "Nhiệt độ cơ thể cao, phát hiện dấu hiệu sốt.";
        if (lower.contains("nhiet do") && lower.contains("thap")) return "Nhiệt độ cơ thể đang xuống mức thấp.";
        if (lower.contains("khong tim thay mach")) return "Báo động: Không tìm thấy mạch đập!";
        if (lower.contains("mat mach khi do lien tuc")) return "Báo động: Mất mạch khi đang đo liên tục!";
        if (lower.contains("ngat xiu")) return "Nguy kịch: Ngất xỉu sau ngã!";
        if (lower.contains("suy ho hap")) return "Báo động: Suy hô hấp do ô nhiễm!";
        
        return raw.replace("Nhiet do", "Nhiệt độ")
                  .replace("nhiet do", "nhiệt độ")
                  .replace("Nhiet Do", "Nhiệt độ")
                  .replace("Nhip tim", "Nhịp tim")
                  .replace("nhip tim", "nhịp tim")
                  .replace("Nhip Tim", "Nhịp tim")
                  .replace("qua cao", "quá cao")
                  .replace("qua thap", "quá thấp")
                  .replace("Canh bao", "Cảnh báo")
                  .replace("canh bao", "cảnh báo");
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { if (deviceRef != null && deviceListener != null) deviceRef.removeEventListener(deviceListener); super.onDestroy(); }
}
