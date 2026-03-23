package com.example.loginanimatedapp.ui.notifications;

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
    private List<Notification> allNotifications = new ArrayList<>();
    private List<Notification> displayedNotifications = new ArrayList<>();
    
    private DatabaseReference notificationRef;
    private FirebaseAuth mAuth;

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
            FirebaseDatabase db = FirebaseDatabase.getInstance("https://cssuckhoe-default-rtdb.asia-southeast1.firebasedatabase.app/");
            notificationRef = db.getReference().child("notifications").child(mAuth.getCurrentUser().getUid());
            loadNotificationsFromFirebase();
        } else {
            binding.tvNoNotifications.setVisibility(View.VISIBLE);
            binding.tvNoNotifications.setText("Vui lòng đăng nhập để xem thông báo");
        }
    }

    private void setupRecyclerView() {
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationsAdapter(displayedNotifications, this);
        binding.rvNotifications.setAdapter(adapter);
    }

    private void loadNotificationsFromFirebase() {
        notificationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                allNotifications.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    try {
                        Notification notification = postSnapshot.getValue(Notification.class);
                        if (notification != null) {
                            if (notification.getId() == null) notification.setId(postSnapshot.getKey());
                            allNotifications.add(notification);
                        }
                    } catch (Exception e) {
                        Log.e("Firebase", "Bỏ qua bản ghi lỗi: " + postSnapshot.getKey());
                    }
                }
                Collections.sort(allNotifications, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
                filterNotifications(binding.chipGroupFilters.getCheckedChipId());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Lỗi: " + error.getMessage());
            }
        });
    }

    private void setupListeners() {
        binding.tvMarkReadAll.setOnClickListener(v -> {
            if (notificationRef != null) {
                for (Notification n : allNotifications) {
                    if (!n.isRead()) notificationRef.child(n.getId()).child("read").setValue(true);
                }
            }
        });

        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) filterNotifications(checkedIds.get(0));
        });
    }

    private void filterNotifications(int chipId) {
        displayedNotifications.clear();
        if (chipId == R.id.chip_all || chipId == View.NO_ID) {
            displayedNotifications.addAll(allNotifications);
        } else if (chipId == R.id.chip_heart_rate) {
            for (Notification n : allNotifications) {
                String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                if (t.contains("nhịp tim") || t.contains("heart rate")) displayedNotifications.add(n);
            }
        } else if (chipId == R.id.chip_spo2) {
            for (Notification n : allNotifications) {
                String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                if (t.contains("oxy") || t.contains("spo2")) displayedNotifications.add(n);
            }
        } else if (chipId == R.id.chip_temperature) {
            for (Notification n : allNotifications) {
                String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                String m = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
                if (t.contains("nhiệt độ") || t.contains("thân nhiệt") || t.contains("nhiệt") || m.contains("nhiệt độ") || m.contains("sốt")) {
                    displayedNotifications.add(n);
                }
            }
        } else if (chipId == R.id.chip_dust) {
            for (Notification n : allNotifications) {
                String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                String m = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
                if (t.contains("bụi") || t.contains("không khí") || t.contains("dust") || m.contains("bụi") || m.contains("ô nhiễm")) {
                    displayedNotifications.add(n);
                }
            }
        } else if (chipId == R.id.chip_fall) {
            for (Notification n : allNotifications) {
                String t = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                if (t.contains("ngã") || t.contains("té") || t.contains("fall") || t.contains("sos")) displayedNotifications.add(n);
            }
        }
        
        adapter.notifyDataSetChanged();
        binding.rvNotifications.setVisibility(displayedNotifications.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvNoNotifications.setVisibility(displayedNotifications.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onNotificationClick(Notification notification, int position) {
        if (notificationRef != null && !notification.isRead()) {
            notificationRef.child(notification.getId()).child("read").setValue(true);
        }
        if (notification != null) {
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
