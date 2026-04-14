package com.example.loginanimatedapp.ui.notifications;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.databinding.FragmentNotificationsBinding;
import com.example.loginanimatedapp.model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsFragment extends Fragment implements NotificationsAdapter.OnNotificationClickListener {

    private FragmentNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private final List<Notification> allNotifications = new ArrayList<>();
    private final List<Notification> displayedNotifications = new ArrayList<>();
    
    private DatabaseReference notificationRef;
    private FirebaseAuth mAuth;
    private ValueEventListener notificationListener;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Thông báo");
            }
        }

        mAuth = FirebaseAuth.getInstance();
        setupRecyclerView();
        setupListeners();
        
        if (mAuth.getCurrentUser() != null) {
            // Đảm bảo URL Database đồng bộ
            FirebaseDatabase db = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);
            notificationRef = db.getReference().child("notifications").child(mAuth.getCurrentUser().getUid());
            loadNotificationsFromFirebase();
        } else {
            if (binding != null) {
                binding.tvNoNotifications.setVisibility(View.VISIBLE);
                binding.tvNoNotifications.setText("Vui lòng đăng nhập để xem thông báo");
            }
        }
    }

    private void setupRecyclerView() {
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        // Sử dụng danh sách displayedNotifications để Adapter tham chiếu
        adapter = new NotificationsAdapter(displayedNotifications, this);
        binding.rvNotifications.setAdapter(adapter);
    }

    private void loadNotificationsFromFirebase() {
        if (notificationRef == null) return;
        
        notificationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                List<Notification> tempAll = new ArrayList<>();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    try {
                        Notification notification = postSnapshot.getValue(Notification.class);
                        if (notification != null) {
                            if (notification.getId() == null) notification.setId(postSnapshot.getKey());
                            tempAll.add(notification);
                        }
                    } catch (Exception e) {
                        Log.e("Firebase", "Bỏ qua bản ghi lỗi: " + postSnapshot.getKey());
                    }
                }
                
                // Sắp xếp trên danh sách tạm
                Collections.sort(tempAll, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
                
                // Cập nhật allNotifications an toàn
                synchronized (allNotifications) {
                    allNotifications.clear();
                    allNotifications.addAll(tempAll);
                }
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> filterNotifications(binding.chipGroupFilters.getCheckedChipId()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Lỗi: " + error.getMessage());
            }
        };
        notificationRef.addValueEventListener(notificationListener);
    }

    private void setupListeners() {
        binding.tvMarkReadAll.setOnClickListener(v -> {
            if (notificationRef != null && !allNotifications.isEmpty()) {
                synchronized (allNotifications) {
                    for (Notification n : allNotifications) {
                        if (!n.isRead()) notificationRef.child(n.getId()).child("read").setValue(true);
                    }
                }
                Toast.makeText(getContext(), "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
            }
        });

        binding.tvClearAll.setOnClickListener(v -> {
            if (notificationRef != null && !allNotifications.isEmpty()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Xóa tất cả thông báo")
                        .setMessage("Bạn có chắc chắn muốn xóa tất cả thông báo không?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            notificationRef.removeValue().addOnSuccessListener(aVoid -> {
                                if (getContext() != null) Toast.makeText(getContext(), "Đã xóa tất cả thông báo", Toast.LENGTH_SHORT).show();
                            });
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });

        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) filterNotifications(checkedIds.get(0));
        });
    }

    private void filterNotifications(int chipId) {
        if (binding == null) return;
        
        List<Notification> filtered = new ArrayList<>();
        synchronized (allNotifications) {
            if (chipId == R.id.chip_all || chipId == View.NO_ID) {
                filtered.addAll(allNotifications);
            } else {
                for (Notification n : allNotifications) {
                    String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                    String m = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
                    
                    if (chipId == R.id.chip_heart_rate && (t.contains("nhịp tim") || t.contains("heart rate"))) {
                        filtered.add(n);
                    } else if (chipId == R.id.chip_spo2 && (t.contains("oxy") || t.contains("spo2"))) {
                        filtered.add(n);
                    } else if (chipId == R.id.chip_temperature && (t.contains("nhiệt độ") || t.contains("thân nhiệt") || t.contains("nhiệt") || m.contains("sốt"))) {
                        filtered.add(n);
                    } else if (chipId == R.id.chip_dust && (t.contains("bụi") || t.contains("không khí") || t.contains("dust") || m.contains("ô nhiễm"))) {
                        filtered.add(n);
                    } else if (chipId == R.id.chip_fall && (t.contains("ngã") || t.contains("té") || t.contains("fall") || t.contains("sos"))) {
                        filtered.add(n);
                    }
                }
            }
        }
        
        // Cập nhật danh sách hiển thị an toàn cho Adapter
        displayedNotifications.clear();
        displayedNotifications.addAll(filtered);
        adapter.notifyDataSetChanged();
        
        binding.rvNotifications.setVisibility(displayedNotifications.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvNoNotifications.setVisibility(displayedNotifications.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onNotificationClick(Notification notification, int position) {
        if (notification == null) return;
        
        if (notificationRef != null && !notification.isRead()) {
            notificationRef.child(notification.getId()).child("read").setValue(true);
        }
        
        try {
            NavController navController = Navigation.findNavController(requireView());
            if (navController.getCurrentDestination() != null && 
                navController.getCurrentDestination().getId() == R.id.navigation_notifications) {
                
                Bundle args = new Bundle();
                args.putSerializable("notification", notification);
                navController.navigate(R.id.action_notifications_to_detail, args);
            }
        } catch (Exception e) {
            Log.e("Notif", "Lỗi điều hướng: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationLongClick(Notification notification, int position) {
        if (notification == null || notification.getId() == null) return;
        
        new AlertDialog.Builder(getContext())
                .setTitle("Xóa thông báo")
                .setMessage("Bạn có muốn xóa thông báo này không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    if (notificationRef != null) {
                        notificationRef.child(notification.getId()).removeValue();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (notificationRef != null && notificationListener != null) {
            notificationRef.removeEventListener(notificationListener);
        }
        super.onDestroyView();
        binding = null;
    }
}
