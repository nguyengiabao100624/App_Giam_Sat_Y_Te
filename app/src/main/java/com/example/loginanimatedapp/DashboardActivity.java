package com.example.loginanimatedapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.loginanimatedapp.databinding.ActivityDashboardBinding;
import com.example.loginanimatedapp.model.Notification;
import com.example.loginanimatedapp.service.NotificationService;
import com.example.loginanimatedapp.ui.dashboard.DashboardViewModel;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    private NavController navController;
    private DashboardViewModel dashboardViewModel;
    private TextView tvBadgeCount;
    private static final String CHANNEL_ID = "HEALTH_ALERT_CHANNEL";

    private float dX, dY;
    private static final int CLICK_DRAG_TOLERANCE = 10;

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startAlertServiceDelayed();
                } else {
                    Toast.makeText(this, "Vui lòng bật thông báo để nhận cảnh báo khẩn cấp kịp thời!", Toast.LENGTH_LONG).show();
                }
            });

    private final BroadcastReceiver updateDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newDeviceId = intent.getStringExtra("device_id");
            if (newDeviceId != null && !newDeviceId.isEmpty()) {
                if (dashboardViewModel != null) {
                    dashboardViewModel.startListeningForDeviceData(newDeviceId);
                }
                startAlertServiceDelayed();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityDashboardBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
            setSupportActionBar(binding.toolbar);

            createNotificationChannel();
            checkNotificationPermission();
            setupNavigation();
            setupFabActions();

            IntentFilter filter = new IntentFilter("com.example.loginanimatedapp.UPDATE_DEVICE_ID");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(updateDeviceReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(updateDeviceReceiver, filter);
            }

            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            String savedDeviceId = prefs.getString("connected_device_id", "");
            if (!savedDeviceId.isEmpty()) {
                dashboardViewModel.startListeningForDeviceData(savedDeviceId);
            }

            dashboardViewModel.startListeningForNotifications();
            dashboardViewModel.getUnreadCount().observe(this, this::updateNotificationBadge);
            
            startAlertServiceDelayed();

            // Xử lý Intent từ thông báo sau khi NavController đã sẵn sàng
            new Handler(Looper.getMainLooper()).postDelayed(() -> handleNotificationIntent(getIntent()), 600);
            
        } catch (Exception e) {
            Log.e("Dashboard", "Lỗi onCreate: " + e.getMessage());
        }
    }

    private void startAlertServiceDelayed() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent serviceIntent = new Intent(this, NotificationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("Dashboard", "Lỗi khởi động Service: " + e.getMessage());
            }
        }, 2500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); 
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("notification_obj")) {
            try {
                Notification notification = (Notification) intent.getSerializableExtra("notification_obj");
                if (notification != null && navController != null) {
                    Bundle args = new Bundle();
                    args.putSerializable("notification", notification);
                    
                    // Thực hiện điều hướng
                    navController.navigate(R.id.navigation_notification_detail, args);
                    
                    // QUAN TRỌNG: Xóa extra sau khi đã xử lý để tránh re-navigation khi xoay màn hình
                    intent.removeExtra("notification_obj");
                    Log.d("Dashboard", "Đã điều hướng tới chi tiết thông báo: " + notification.getTitle());
                }
            } catch (Exception e) {
                Log.e("Dashboard", "Lỗi điều hướng từ Intent: " + e.getMessage());
            }
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Cảnh báo sức khỏe", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Thông báo khẩn cấp từ hệ thống Healthy 365");
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void setupNavigation() {
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_account)
                .build();
        
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_dashboard);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(binding.navView, navController);

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                boolean isDetail = id == R.id.navigation_chat || 
                                   id == R.id.navigation_notifications || 
                                   id == R.id.navigation_metric_detail ||
                                   id == R.id.navigation_notification_detail;
                
                binding.fabChat.setVisibility(isDetail ? View.GONE : View.VISIBLE);
                binding.bottomAppBar.setVisibility(isDetail ? View.GONE : View.VISIBLE);
                binding.fabAdd.setVisibility(isDetail ? View.GONE : View.VISIBLE);
            });
        }
    }

    private void setupFabActions() {
        binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddDeviceActivity.class)));
        
        binding.fabChat.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        initialX = event.getRawX();
                        initialY = event.getRawY();
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        View parent = (View) view.getParent();
                        if (newX < 0) newX = 0;
                        if (newX > parent.getWidth() - view.getWidth()) newX = parent.getWidth() - view.getWidth();
                        if (newY < 0) newY = 0;
                        if (newY > parent.getHeight() - view.getHeight()) newY = parent.getHeight() - view.getHeight();
                        view.animate().x(newX).y(newY).setDuration(0).start();
                        if (Math.abs(event.getRawX() - initialX) > CLICK_DRAG_TOLERANCE || 
                            Math.abs(event.getRawY() - initialY) > CLICK_DRAG_TOLERANCE) isMoving = true;
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isMoving) if (navController != null) navController.navigate(R.id.navigation_chat);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dashboard_toolbar_menu, menu);
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        View actionView = notificationItem.getActionView();
        if (actionView != null) {
            tvBadgeCount = actionView.findViewById(R.id.tv_badge_count);
            actionView.setOnClickListener(v -> {
                if (navController != null) navController.navigate(R.id.navigation_notifications);
            });
            Integer count = dashboardViewModel.getUnreadCount().getValue();
            updateNotificationBadge(count != null ? count : 0);
        }
        return true;
    }

    public void updateNotificationBadge(int count) {
        if (tvBadgeCount == null) return;
        tvBadgeCount.setText(String.valueOf(count));
        tvBadgeCount.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(updateDeviceReceiver); } catch (Exception ignored) {}
    }

    @Override
    public boolean onSupportNavigateUp() {
        return (navController != null && navController.navigateUp()) || super.onSupportNavigateUp();
    }
}
