package com.example.loginanimatedapp.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loginanimatedapp.model.Notification;
import com.example.loginanimatedapp.utils.AppConstants;
import com.github.mikephil.charting.data.Entry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<Map<String, Object>> deviceData = new MutableLiveData<>();
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    
    // Cache dữ liệu lịch sử để không bị reset khi chuyển màn hình
    private final Map<String, List<Entry>> historyCache = new HashMap<>();
    
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

    public List<Entry> getHistoryCache(String type) {
        return historyCache.getOrDefault(type, new ArrayList<>());
    }

    public void updateHistoryCache(String type, List<Entry> entries) {
        historyCache.put(type, new ArrayList<>(entries));
    }

    public void startListeningForDeviceData(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        if (deviceDataRef != null && deviceDataListener != null) {
            deviceDataRef.removeEventListener(deviceDataListener);
        }
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
            @Override public void onCancelled(@NonNull DatabaseError databaseError) {}
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
                    if (notification != null && !notification.isRead()) count++;
                }
                unreadCount.postValue(count);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        notificationRef.addValueEventListener(notificationListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (deviceDataRef != null && deviceDataListener != null) deviceDataRef.removeEventListener(deviceDataListener);
        if (notificationRef != null && notificationListener != null) notificationRef.removeEventListener(notificationListener);
    }
}
