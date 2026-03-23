package com.example.loginanimatedapp.ui.chat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.adapter.ChatAdapter;
import com.example.loginanimatedapp.databinding.FragmentChatBinding;
import com.example.loginanimatedapp.model.ChatMessage;
import com.example.loginanimatedapp.ui.dashboard.DashboardViewModel;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment_DEBUG";
    private FragmentChatBinding binding;
    private ChatAdapter chatAdapter;
    private DashboardViewModel sharedViewModel;
    private ChatViewModel chatViewModel;
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    sendCurrentLocation();
                } else {
                    addMessageToUI(new ChatMessage("Bạn vui lòng cấp quyền vị trí để mình có thể hỗ trợ tìm cơ sở y tế gần nhất nhé!", false));
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Sử dụng ViewModelProvider(requireActivity()) để ViewModel tồn tại xuyên suốt vòng đời của Activity
        chatViewModel = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupRecyclerView();
        
        // Nếu chatbot chưa được khởi tạo lần nào thì mới khởi tạo và gửi tin nhắn chào mừng
        if (!chatViewModel.isChatInitialized()) {
            setupGenerativeModel();
            addMessageToUI(new ChatMessage("Chào bạn! Mình là Trợ lý Healthy 365. Mình đã kết nối với thiết bị để bảo vệ bạn. Bạn cần mình kiểm tra chỉ số nào không? 😊", false));
        }

        setupQuickActions();
        binding.btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupQuickActions() {
        binding.btnQuick115.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:115"));
            startActivity(intent);
        });

        binding.btnQuickFamily.setOnClickListener(v -> {
            String familyNumber = "0909000111";
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + familyNumber));
            startActivity(intent);
        });

        binding.btnQuickLocation.setOnClickListener(v -> sendCurrentLocation());
    }

    private void sendCurrentLocation() {
        addMessageToUI(new ChatMessage("Đang chuẩn bị dữ liệu vị trí và sức khỏe...", false));
        String healthContext = getSystemDataContext();

        Map<String, Object> data = sharedViewModel.getDeviceData().getValue();
        if (data != null) {
            double lat = parseDouble(data.get("GPS_Lat"));
            double lng = parseDouble(data.get("GPS_Lng"));

            if (lat != 0 && lng != 0) {
                String address = getAddressFromLatLng(lat, lng);
                String mapsLink = "https://www.google.com/maps?q=" + lat + "," + lng;
                addMessageToUI(new ChatMessage("Vị trí thiết bị: " + address + "\n" + mapsLink, true));
                
                String prompt = "DỮ LIỆU SỨC KHỎE: [" + healthContext + "].\n" +
                        "VỊ TRÍ: [" + address + "].\n" +
                        "YÊU CẦU: Hãy phân tích các chỉ số y tế trên (đặc biệt lưu ý các ngưỡng nhiệt độ ngoài da) và gợi ý bệnh viện gần địa chỉ này nhất.";
                generateGeminiResponse(prompt);
                return;
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    String address = getAddressFromLatLng(location.getLatitude(), location.getLongitude());
                    String mapsLink = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                    addMessageToUI(new ChatMessage("Vị trí điện thoại: " + address + "\n" + mapsLink, true));
                    
                    String prompt = "DỮ LIỆU SỨC KHỎE: [" + healthContext + "].\n" +
                            "VỊ TRÍ: [" + address + "].\n" +
                            "YÊU CẦU: Hãy phân tích sức khỏe dựa trên các chỉ số thực tế và gợi ý cơ sở y tế gần địa chỉ này.";
                    generateGeminiResponse(prompt);
                } else {
                    addMessageToUI(new ChatMessage("Không thể xác định vị trí. Hãy bật GPS và thử lại nhé!", false));
                }
            });
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private String getAddressFromLatLng(double lat, double lng) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            Log.e(TAG, "Lỗi lấy địa chỉ", e);
        }
        return "Tọa độ: " + lat + ", " + lng;
    }

    private void setupRecyclerView() {
        // Lấy danh sách tin nhắn từ ViewModel thay vì tạo mới
        chatAdapter = new ChatAdapter(chatViewModel.getChatMessages().getValue());
        binding.rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatMessages.setAdapter(chatAdapter);
        
        // Cuộn xuống tin nhắn cuối cùng nếu có
        if (chatAdapter.getItemCount() > 0) {
            binding.rvChatMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void setupGenerativeModel() {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY")) return;

        String systemPrompt = "Bạn là trợ lý y tế Healthy 365 chuyên nghiệp.\n" +
                "QUY TẮC Y TẾ ĐẶC BIỆT (DÀNH CHO CẢM BIẾN ĐO NGOÀI DA):\n" +
                "1. THÂN NHIỆT (TempObj): 31°C - 36.9°C là BÌNH THƯỜNG. Trên 37°C là SỐT.\n" +
                "2. CHÍNH XÁC: Đọc đúng TempObj là Thân nhiệt.\n" +
                "3. THÁI ĐỘ: Thân thiện, ấm áp (Mình - Bạn).\n" +
                "4. KHÔNG nhắc tới AI/Gemini.";

        Content systemInstruction = new Content.Builder().addText(systemPrompt).build();
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", apiKey.trim());
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);
        ChatFutures chat = model.startChat(Collections.singletonList(systemInstruction));
        
        chatViewModel.setChatFutures(chat);
    }

    private void sendMessage() {
        String messageText = binding.etMessageInput.getText().toString().trim();
        if (messageText.isEmpty() || !chatViewModel.isChatInitialized()) return;

        addMessageToUI(new ChatMessage(messageText, true));
        binding.etMessageInput.setText("");

        String systemContext = getSystemDataContext();
        String finalPrompt = "DỮ LIỆU CẢM BIẾN HIỆN TẠI: [" + systemContext + "].\nCÂU HỎI: " + messageText;

        addMessageToUI(new ChatMessage("Đang kiểm tra chỉ số...", false));
        generateGeminiResponse(finalPrompt);
    }

    private String getSystemDataContext() {
        if (sharedViewModel == null) return "N/A";
        Map<String, Object> data = sharedViewModel.getDeviceData().getValue();
        if (data == null) return "Chưa có dữ liệu";

        return String.format("Nhịp tim: %s bpm, SpO2: %s%%, THÂN NHIỆT NGOÀI DA (TempObj): %s°C, Bụi mịn: %s, Trạng thái: %s",
                data.getOrDefault("BPM", "0"),
                data.getOrDefault("SpO2", "0"),
                data.getOrDefault("TempObj", "0"),
                data.getOrDefault("Dust", "0"),
                data.getOrDefault("TrangThai", "BÌNH THƯỜNG"));
    }

    private void generateGeminiResponse(String prompt) {
        if (!chatViewModel.isChatInitialized()) return;
        
        Content userMessage = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = chatViewModel.getChatFutures().sendMessage(userMessage);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String botResponse = result.getText();
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    chatViewModel.removeLastMessage();
                    chatAdapter.notifyItemRemoved(chatViewModel.getChatMessages().getValue().size());
                    addMessageToUI(new ChatMessage(botResponse, false));
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    chatViewModel.removeLastMessage();
                    chatAdapter.notifyItemRemoved(chatViewModel.getChatMessages().getValue().size());
                    addMessageToUI(new ChatMessage("Kết nối gián đoạn, bạn thử lại nhé!", false));
                });
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private double parseDouble(Object obj) {
        if (obj == null) return 0;
        try { return Double.parseDouble(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    private void addMessageToUI(ChatMessage chatMessage) {
        chatViewModel.addMessage(chatMessage);
        chatAdapter.notifyItemInserted(chatViewModel.getChatMessages().getValue().size() - 1);
        binding.rvChatMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
