package com.example.loginanimatedapp.ui.notifications;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.loginanimatedapp.databinding.FragmentNotificationDetailBinding;
import com.example.loginanimatedapp.model.Notification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationDetailFragment extends Fragment {

    private FragmentNotificationDetailBinding binding;
    private Notification notification;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Sửa lỗi văng app: Kiểm tra xem object có thực sự là Notification không
            Object navNotif = getArguments().getSerializable("notification");
            if (navNotif instanceof Notification) {
                notification = (Notification) navNotif;
            } else {
                // Fallback nếu click từ thông báo hệ thống (Service gửi)
                notification = (Notification) getArguments().getSerializable("notification_obj");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (notification != null) {
            String title = notification.getTitle() != null ? notification.getTitle() : "Thông báo";
            String message = notification.getMessage() != null ? notification.getMessage() : "";
            
            binding.tvDetailTitle.setText(title);
            binding.tvDetailMessage.setText(message);
            
            try {
                binding.ivDetailIcon.setImageResource(notification.getIconResId());
            } catch (Exception e) {
                // Fallback icon nếu lỗi
            }
            
            // HIỂN THỊ GIÁ TRỊ GHI NHẬN
            String val = notification.getMetricValue();
            Log.d("NotifDetail", "MetricValue: " + val); // Debug xem giá trị là gì
            
            if (val == null || val.trim().isEmpty() || val.equals("--") || val.equalsIgnoreCase("null")) {
                binding.tvDetailValue.setText("N/A");
            } else {
                binding.tvDetailValue.setText(val);
            }
            
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
                binding.tvDetailTime.setText(sdf.format(new Date(notification.getTimestamp())));
            } catch (Exception e) {
                binding.tvDetailTime.setText("--:--");
            }
            
            if (getActivity() instanceof AppCompatActivity) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Chi tiết cảnh báo");
            }

            updateAdvice(title, message);
        } else {
            binding.tvDetailTitle.setText("Không tìm thấy dữ liệu");
            binding.tvDetailMessage.setText("Thông báo này có thể đã bị xóa hoặc không hợp lệ.");
        }

        if (notification == null || (!notification.getTitle().contains("SOS") && !notification.getTitle().contains("TÉ NGÃ"))) {
            binding.btnActionPrimary.setText("ĐÓNG");
            binding.btnActionPrimary.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        }
    }

    private void updateAdvice(String title, String message) {
        StringBuilder suggestion = new StringBuilder();
        String actionText = "ĐÓNG";

        if (title.contains("SOS")) {
            suggestion.append("• Gọi ngay cho dịch vụ cấp cứu 115.\n")
                      .append("• Giữ liên lạc liên tục với người thân.\n")
                      .append("• Chuẩn bị sẵn hồ sơ y tế nếu cần.");
            actionText = "GỌI CẤP CỨU 115";
            binding.btnActionPrimary.setOnClickListener(v -> startCall("115"));
        } else if (title.contains("TÉ NGÃ")) {
            suggestion.append("• Đến ngay vị trí của người thân.\n")
                      .append("• Kiểm tra ý thức và vết thương hở.\n")
                      .append("• Không di chuyển người thân nếu nghi ngờ chấn thương cột sống.");
            actionText = "GỌI NGƯỜI THÂN";
            binding.btnActionPrimary.setOnClickListener(v -> startCall("0909000111"));
        } else if (message.contains("Nhiệt độ") || message.contains("sốt")) {
            suggestion.append("• Cho người thân uống nước ấm hoặc sữa ấm.\n")
                      .append("• Đắp chăn giữ ấm hoặc nới lỏng quần áo tùy tình trạng.\n")
                      .append("• Theo dõi nhiệt độ liên tục mỗi 15 phút.");
        } else if (message.contains("Oxy") || message.contains("Nhịp tim") || message.contains("SpO2")) {
            suggestion.append("• Nhắc người thân hít thở sâu và đều.\n")
                      .append("• Ngồi tư thế thẳng lưng hoặc nằm nghiêng.\n")
                      .append("• Kiểm tra lại dây đeo thiết bị.");
        }

        binding.tvDetailSuggestion.setText(suggestion.toString().isEmpty() ? "Vui lòng theo dõi tình trạng sức khỏe thường xuyên." : suggestion.toString());
        binding.btnActionPrimary.setText(actionText);
    }

    private void startCall(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
