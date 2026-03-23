package com.example.loginanimatedapp.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loginanimatedapp.model.Notification;
import com.example.loginanimatedapp.utils.AppConstants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<Map<String, Object>> deviceData = new MutableLiveData<>();
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    
    // Sử dụng DATABASE_URL từ AppConstants để dễ dàng thay đổi link sau này
    private final FirebaseDatabase mDatabaseInstance = FirebaseDatabase.getInstance(AppConstants.DATABASE_URL);
    private final DatabaseReference mDatabase = mDatabaseInstance.getReference();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    
    private ValueEventListener deviceDataListener;
    private DatabaseReference deviceDataRef;
    private ValueEventListener notificationListener;
    private DatabaseReference notificationRef;

    public LiveData<Map<String, Object>> getDeviceData() {
        return deviceData;
    }

    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    public void startListeningForDeviceData(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;

        if (deviceDataRef != null && deviceDataListener != null) {
            deviceDataRef.removeEventListener(deviceDataListener);
        }

        // Đường dẫn: Devices/[ID_THIẾT_BỊ]
        deviceDataRef = mDatabase.child("Devices").child(deviceId);
        deviceDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Object value = dataSnapshot.getValue();
                    if (value instanceof Map) {
                        deviceData.postValue((Map<String, Object>) value);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        };
        deviceDataRef.addValueEventListener(deviceDataListener);
    }

    public void startListeningForNotifications() {
        if (mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        notificationRef = mDatabase.child("notifications").child(uid);
        
        notificationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    Notification notification = postSnapshot.getValue(Notification.class);
                    if (notification != null && !notification.isRead()) {
                        count++;
                    }
                }
                unreadCount.postValue(count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        notificationRef.addValueEventListener(notificationListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (deviceDataRef != null && deviceDataListener != null) {
            deviceDataRef.removeEventListener(deviceDataListener);
        }
        if (notificationRef != null && notificationListener != null) {
            notificationRef.removeEventListener(notificationListener);
        }
    }
}
