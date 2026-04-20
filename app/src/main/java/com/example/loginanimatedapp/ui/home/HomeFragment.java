package com.example.loginanimatedapp.ui.home;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.databinding.FragmentHomeBinding;
import com.example.loginanimatedapp.ui.dashboard.DashboardViewModel;
import com.example.loginanimatedapp.ui.dashboard.MetricDetailFragment;
import com.example.loginanimatedapp.utils.NotificationHelper;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Map;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private FragmentHomeBinding binding;
    private DashboardViewModel dashboardViewModel;
    
    private MapView mapView;
    private GoogleMap googleMap;
    private Marker userMarker;
    private LatLng currentLocation;
    private boolean isFirstLocationUpdate = true;
    
    private boolean isSosAlertActive = false;
    private boolean isFallAlertActive = false;
    private boolean isHealthAlertActive = false;



    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) enableMyLocation();
            });

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        mapView = binding.mapView;
        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Theo dõi sức khỏe");
            }
        }

        setupClickListeners();
        
        // KIỂM TRA TRẠNG THÁI KẾT NỐI BAN ĐẦU
        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("connected_device_id", "");
        if (deviceId.isEmpty()) {
            binding.tvHomeOverallStatus.setText("CHƯA KẾT NỐI VỚI THIẾT BỊ NÀO");
            binding.tvHomeOverallStatus.setTextColor(Color.GRAY);
            binding.tvStatusDetail.setText("Vui lòng vào Cài đặt để thêm thiết bị giám sát của bạn.");
            binding.tvStatusDetail.setVisibility(View.VISIBLE);
            binding.ivStatusIcon.setImageResource(R.drawable.ic_info_circle_blue);
        } else {
            binding.tvHomeOverallStatus.setText("ĐANG KẾT NỐI...");
            binding.tvStatusDetail.setText("Hệ thống đang đồng bộ dữ liệu với thiết bị " + deviceId);
            binding.tvStatusDetail.setVisibility(View.VISIBLE);
            binding.ivStatusIcon.setImageResource(R.drawable.ic_info_circle_blue);
        }

        dashboardViewModel.getDeviceData().observe(getViewLifecycleOwner(), this::updateHomeUI);
    }

    private void setupClickListeners() {
        binding.cardHeartRate.setOnClickListener(v -> navigateToDetail("heart_rate", "Nhịp tim"));
        binding.cardSpo2.setOnClickListener(v -> navigateToDetail("spo2", "Nồng độ Oxy"));
        binding.cardTemperature.setOnClickListener(v -> navigateToDetail("temperature", "Thân nhiệt"));
        binding.cardDust.setOnClickListener(v -> navigateToDetail("dust", "Bụi mịn (PM2.5)"));
        binding.btnViewFullMap.setOnClickListener(v -> Navigation.findNavController(requireView()).navigate(R.id.action_home_to_full_map));
        binding.btnCancelAlert.setOnClickListener(v -> cancelAlert());
    }

    private void cancelAlert() {
        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("connected_device_id", "");
        if (!deviceId.isEmpty()) {
            FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL)
                    .getReference("Devices").child(deviceId).child("Cmd_CancelAlert")
                    .setValue(true).addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Đã gửi lệnh hủy báo động!", Toast.LENGTH_SHORT).show();
                        binding.btnCancelAlert.setVisibility(View.GONE);
                    });
        }
    }

    private void navigateToDetail(String metricType, String title) {
        Bundle args = new Bundle();
        args.putString(MetricDetailFragment.ARG_METRIC_TYPE, metricType);
        args.putString("title", title);
        Navigation.findNavController(requireView()).navigate(R.id.action_home_to_detail, args);
    }

    private void updateHomeUI(Map<String, Object> data) {
        if (data == null || binding == null) return;

        Object hr = data.get("BPM");
        Object spo2 = data.get("SpO2");
        Object tempObj = data.get("TempObj");
        Object tempAmb = data.get("TempAmb");
        Object dust = data.get("Dust");
        
        String rawTrangThai = String.valueOf(data.get("TrangThai"));
        String rawTrangThaiDo = String.valueOf(data.get("TrangThaiDo"));
        String thoiGian = String.valueOf(data.get("ThoiGian"));
        String lastMeasure = String.valueOf(data.get("LastMeasureTime"));

        String lastMeasureVn = (lastMeasure != null && !"null".equals(lastMeasure)) ? lastMeasure : "--";
        if ("Chua do".equalsIgnoreCase(lastMeasureVn)) lastMeasureVn = "Chưa đo";

        boolean isFall = Boolean.TRUE.equals(data.get("Alert_Fall"));
        boolean isSOS = Boolean.TRUE.equals(data.get("Alert_SOS"));
        boolean isHealthAlert = Boolean.TRUE.equals(data.get("Alert_Health"));
        String alertReason = String.valueOf(data.get("Alert_Reason"));

        handleNotifications(isSOS, isFall, isHealthAlert, alertReason);

        if (isSOS || isFall || isHealthAlert) {
            binding.btnCancelAlert.setVisibility(View.VISIBLE);
        } else {
            binding.btnCancelAlert.setVisibility(View.GONE);
        }

        // NÂNG CẤP: Hiển thị trạng thái với màu sắc và icon đồng bộ chuẩn y tế
        String trangThaiVn = "ĐANG KẾT NỐI...";
        String statusDetail = "Vui lòng chờ trong khi hệ thống đồng bộ dữ liệu...";
        boolean isOffline = false;

        // 1. KIỂM TRA MẤT KẾT NỐI (Nếu ThoiGian quá cũ) - THU HẸP XUỐNG 1 PHÚT ĐỂ NHẠY HƠN
        if (thoiGian != null && !"null".equals(thoiGian) && !thoiGian.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", java.util.Locale.getDefault());
                java.util.Date lastDate = sdf.parse(thoiGian);
                if (lastDate != null) {
                    long diff = System.currentTimeMillis() - lastDate.getTime();
                    if (diff > 60000) { // Nếu quá 1 phút không có cập nhật -> NGOẠI TUYẾN
                        isOffline = true;
                        trangThaiVn = "NGOẠI TUYẾN";
                        statusDetail = "Thiết bị hiện đang ngoại tuyến. Vui lòng kiểm tra Wifi hoặc nguồn của thiết bị.";
                        binding.tvHomeOverallStatus.setTextColor(Color.GRAY);
                        binding.ivStatusIcon.setImageResource(R.drawable.ic_info_circle_blue);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. CHỈ CẬP NHẬT TRẠNG THÁI SỨC KHỎE KHI THIẾT BỊ ĐANG TRỰC TUYẾN (HOẶC NẾU CÓ CẢNH BÁO SOS/NGÃ CỰC KỲ QUAN TRỌNG)
        if (isSOS) {
            trangThaiVn = "SOS KHẨN CẤP!";
            statusDetail = "Người dùng vừa nhấn nút SOS khẩn cấp, cần hỗ trợ ngay!";
            binding.tvHomeOverallStatus.setTextColor(Color.RED);
            binding.ivStatusIcon.setImageResource(R.drawable.ic_error_circle_red);
        } else if (isFall) {
            trangThaiVn = "PHÁT HIỆN TÉ NGÃ!";
            statusDetail = "Cảm biến phát hiện tác động mạnh nghi là té ngã!";
            binding.tvHomeOverallStatus.setTextColor(Color.RED);
            binding.ivStatusIcon.setImageResource(R.drawable.ic_error_circle_red);
        } else if (!isOffline) {
            // Chỉ hiện các trạng thái thường khi đang trực tuyến - Tránh việc thiết bị tắt mà App vẫn báo "Bình thường"
            if (isHealthAlert || rawTrangThai.contains("CANH BAO")) {
                trangThaiVn = "CẢNH BÁO SỨC KHỎE!";
                String alertReasonText = String.valueOf(data.get("Alert_Reason"));
                
                if (alertReasonText != null && !alertReasonText.isEmpty() && !"null".equalsIgnoreCase(alertReasonText)) {
                    statusDetail = "Phát hiện bất thường: " + alertReasonText;
                } else {
                    StringBuilder sb = new StringBuilder("Phát hiện bất thường: ");
                    boolean added = false;
                    try {
                        if (hr != null) {
                            float v = Float.parseFloat(String.valueOf(hr));
                            if (v > 100) { sb.append("Nhịp tim cao (").append((int)v).append(")"); added = true; }
                            else if (v < 60 && v > 0) { sb.append("Nhịp tim thấp (").append((int)v).append(")"); added = true; }
                        }
                        if (spo2 != null) {
                            float v = Float.parseFloat(String.valueOf(spo2));
                            if (v < 94 && v > 0) {
                                if (added) sb.append(", ");
                                sb.append("SpO2 thấp (").append((int)v).append("%)"); added = true;
                            }
                        }
                        if (tempObj != null) {
                            float v = Float.parseFloat(String.valueOf(tempObj));
                            if (v > 37.8f) {
                                if (added) sb.append(", ");
                                sb.append("Thân nhiệt cao (").append(String.format("%.1f", v)).append("°C)"); added = true;
                            } else if (v < 35.0f && v > 0) {
                                if (added) sb.append(", ");
                                sb.append("Thân nhiệt thấp (").append(String.format("%.1f", v)).append("°C)"); added = true;
                            }
                        }
                    } catch (Exception ignored) {}
                    
                    if (!added) sb.append("Chỉ số vượt ngưỡng an toàn");
                    statusDetail = sb.toString();
                }
                
                binding.tvHomeOverallStatus.setTextColor(Color.parseColor("#FB8C00"));
                binding.ivStatusIcon.setImageResource(R.drawable.ic_info_circle_blue);
            } else if (rawTrangThai.contains("BINH THUONG")) {
                trangThaiVn = "ỔN ĐỊNH";
                statusDetail = "Mọi chỉ số sức khỏe của bạn hiện đang ở mức an toàn.";
                binding.tvHomeOverallStatus.setTextColor(Color.parseColor("#4CAF50"));
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle_green);
            }
        }
        
        binding.tvHomeOverallStatus.setText(trangThaiVn);
        binding.tvStatusDetail.setText(statusDetail);
        binding.tvStatusDetail.setVisibility(View.VISIBLE);

        String trangThaiDoVn = "--";
        if (rawTrangThaiDo != null && !"null".equalsIgnoreCase(String.valueOf(rawTrangThaiDo))) {
            String s = String.valueOf(rawTrangThaiDo).trim().toLowerCase();
            if (s.contains("cho do")) trangThaiDoVn = "Chờ đo";
            else if (s.contains("dang do")) trangThaiDoVn = "Đang đo...";
            else if (s.contains("hoan tat")) trangThaiDoVn = "Hoàn tất";
            else if (s.contains("do lien tuc")) trangThaiDoVn = "Đo liên tục";
            else trangThaiDoVn = String.valueOf(rawTrangThaiDo);
        }
        


        binding.tvHomeHeartRate.setText((hr != null ? hr : "--") + " bpm");
        binding.tvTimeHr.setText("Đo lúc: " + lastMeasureVn);
        binding.tvStatusMeasureHr.setText("Trạng thái: " + trangThaiDoVn);
        
        binding.tvHomeSpo2.setText((spo2 != null ? spo2 : "--") + " %");
        binding.tvTimeSpo2.setText("Đo lúc: " + lastMeasureVn);
        binding.tvStatusMeasureSpo2.setText("Trạng thái: " + trangThaiDoVn);
        


        binding.tvHomeTemperature.setText((tempObj != null ? formatTemp(tempObj) : "--") + " °C");
        binding.tvHomeTempAmb.setText("Môi trường: " + (tempAmb != null ? formatTemp(tempAmb) : "--") + " °C");
        binding.tvTimeTemp.setText("Cập nhật: " + (thoiGian != null && !"null".equals(thoiGian) ? thoiGian : "--"));
        
        binding.tvHomeDust.setText((dust != null ? formatDust(dust) : "--") + " µg/m³");
        binding.tvTimeDust.setText("Cập nhật: " + (thoiGian != null && !"null".equals(thoiGian) ? thoiGian : "--"));

        double lat = parseDouble(data.get("GPS_Lat"));
        double lon = parseDouble(data.get("GPS_Lng"));
        if (lat != 0 && lon != 0) {
            currentLocation = new LatLng(lat, lon);
            if (googleMap != null) {
                if (userMarker == null) {
                    userMarker = googleMap.addMarker(new MarkerOptions().position(currentLocation).title("Vị trí thiết bị"));
                } else {
                    userMarker.setPosition(currentLocation);
                }
                if (isFirstLocationUpdate) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                    isFirstLocationUpdate = false;
                }
            }
        }
    }

    private void handleNotifications(boolean isSOS, boolean isFall, boolean isHealthAlert, String alertReason) {
        if (isSOS) {
            if (!isSosAlertActive) {
                NotificationHelper.showError(getView(), "CẢNH BÁO: Thiết bị vừa nhấn nút SOS khẩn cấp!");
                isSosAlertActive = true;
            }
        } else {
            isSosAlertActive = false;
        }

        if (isFall) {
            if (!isFallAlertActive) {
                NotificationHelper.showError(getView(), "CẢNH BÁO: Phát hiện người thân vừa bị té ngã!");
                isFallAlertActive = true;
            }
        } else {
            isFallAlertActive = false;
        }

        if (isHealthAlert) {
            if (!isHealthAlertActive) {
                String msg = (alertReason != null && !alertReason.isEmpty() && !"null".equalsIgnoreCase(alertReason)) ? alertReason : "Phát hiện chỉ số bất thường!";
                NotificationHelper.showError(getView(), "BÁO ĐỘNG SỨC KHỎE: " + msg);
                isHealthAlertActive = true;
            }
        } else {
            isHealthAlertActive = false;
        }
    }



    private String formatTemp(Object temp) {
        try {
            float t = Float.parseFloat(String.valueOf(temp));
            return String.format("%.1f", t);
        } catch (Exception e) { return String.valueOf(temp); }
    }

    private String formatDust(Object dust) {
        try {
            float d = Float.parseFloat(String.valueOf(dust));
            return String.valueOf((int) d);
        } catch (Exception e) { return String.valueOf(dust); }
    }

    private double parseDouble(Object obj) {
        if (obj == null) return 0;
        try { return Double.parseDouble(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        try {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_medical));
        } catch (Exception ignored) {}

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }

        if (currentLocation != null) {
             userMarker = googleMap.addMarker(new MarkerOptions().position(currentLocation).title("Vị trí thiết bị"));
             googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
             isFirstLocationUpdate = false;
        }
    }

    private void enableMyLocation() {
        if (googleMap != null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapView != null) mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); if (mapView != null) mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}
