package com.example.loginanimatedapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.loginanimatedapp.databinding.ActivityAddDeviceBinding;
import com.example.loginanimatedapp.BuildConfig;
import com.google.firebase.database.FirebaseDatabase;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class AddDeviceActivity extends AppCompatActivity {

    private ActivityAddDeviceBinding binding;
    // Sử dụng BuildConfig.DATABASE_URL
    private final FirebaseDatabase mDatabase = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);

    private final com.google.firebase.auth.FirebaseAuth mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddDeviceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Hiển thị ID hiện tại
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String currentId = prefs.getString("connected_device_id", "Chưa có kết nối");
        binding.tvCurrentConnectedDevice.setText(currentId);

        // Nút quay lại trên Toolbar
        binding.toolbarAddDevice.setNavigationOnClickListener(v -> finish());

        // Nút quét QR
        binding.btnScanQr.setOnClickListener(v -> scanQrCode());

        // Nút cập nhật kết nối (Fix ID từ btnConnectManual thành btnConfirmAdd)
        binding.btnConfirmAdd.setOnClickListener(v -> {
            // Fix ID từ etDeviceId thành edtDeviceId
            String deviceId = binding.edtDeviceId.getText().toString().trim();
            if (!deviceId.isEmpty()) {
                saveDeviceAndFinish(deviceId);
            } else {
                Toast.makeText(this, "Vui lòng nhập ID thiết bị", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scanQrCode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Quét mã QR trên thiết bị");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(CustomCaptureActivity.class);
        barcodeLauncher.launch(options);
    }

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    binding.edtDeviceId.setText(result.getContents());
                    saveDeviceAndFinish(result.getContents());
                }
            });

    private void saveDeviceAndFinish(String deviceId) {
        // Lưu vào SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putString("connected_device_id", deviceId).apply();

        // Cập nhật lên Firebase User
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            mDatabase.getReference("Users").child(uid).child("deviceId").setValue(deviceId);
        }

        Toast.makeText(this, "Kết nối thiết bị thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
