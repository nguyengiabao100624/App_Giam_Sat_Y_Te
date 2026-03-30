package com.example.loginanimatedapp.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.adapter.ChatAdapter;
import com.example.loginanimatedapp.databinding.FragmentChatBinding;
import com.example.loginanimatedapp.model.ChatMessage;
import com.example.loginanimatedapp.ui.dashboard.DashboardViewModel;
import com.example.loginanimatedapp.utils.AppConstants;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment_DEBUG";
    private FragmentChatBinding binding;
    private ChatAdapter chatAdapter;
    private DashboardViewModel sharedViewModel;
    private ChatViewModel chatViewModel;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Uri selectedFileUri = null;
    private Bitmap cameraBitmap = null;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isRecording = false;
    private boolean autoSendAfterVoice = false;

    private static final int NUM_BARS = 90;
    private static final float NOISE_THRESHOLD = 4.0f; 
    private final float[] barHeights = new float[NUM_BARS];
    private float currentVolume = 0f;
    private final Handler waveformHandler = new Handler(Looper.getMainLooper());
    
    private final List<String> historyDataList = new ArrayList<>();

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startVoiceRecording();
                else Toast.makeText(getContext(), "Cần quyền Micro để thu âm", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> pickFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    clearCameraData();
                    selectedFileUri = uri;
                    showAttachmentPreview(uri);
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    clearAttachmentData();
                    Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                    if (photo != null) {
                        cameraBitmap = photo;
                        binding.cvAttachmentPreview.setVisibility(View.VISIBLE);
                        binding.ivPreview.setImageBitmap(photo);
                        updateSendButtonVisibility();
                    }
                }
            });

    private final ActivityResultLauncher<String[]> pickDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    clearCameraData();
                    selectedFileUri = uri;
                    binding.cvAttachmentPreview.setVisibility(View.VISIBLE);
                    binding.ivPreview.setImageResource(android.R.drawable.ic_menu_save);
                    updateSendButtonVisibility();
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
        chatViewModel = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        chatAdapter = new ChatAdapter(chatViewModel.getChatMessages().getValue());
        binding.rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatMessages.setAdapter(chatAdapter);

        chatViewModel.getChatMessages().observe(getViewLifecycleOwner(), messages -> {
            chatAdapter.notifyDataSetChanged();
            if (messages != null && !messages.isEmpty()) {
                binding.rvChatMessages.scrollToPosition(messages.size() - 1);
            }
            checkWelcomeVisibility();
        });
        
        setupUI();
        setupSpeechRecognizer();
        initWaveformBars();
        loadHistoryForAI();

        if (!chatViewModel.isChatInitialized()) {
            setupGenerativeModel();
        }

        binding.btnAttach.setOnClickListener(v -> {
            if (binding.cvAttachmentMenu.getVisibility() == View.VISIBLE) hideAttachmentMenu();
            else showAttachmentMenu();
        });

        binding.btnMenuGallery.setOnClickListener(v -> { hideAttachmentMenu(); pickFileLauncher.launch("image/*"); });
        binding.btnMenuFiles.setOnClickListener(v -> { hideAttachmentMenu(); pickDocumentLauncher.launch(new String[]{"*/*"}); });
        binding.btnMenuCamera.setOnClickListener(v -> { hideAttachmentMenu(); Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); cameraLauncher.launch(it); });

        binding.btnMic.setOnClickListener(v -> checkVoicePermission());
        binding.btnStopRecording.setOnClickListener(v -> stopVoiceRecording(false));
        binding.btnSendVoice.setOnClickListener(v -> stopVoiceRecording(true));

        binding.btnRemoveAttachment.setOnClickListener(v -> clearAllAttachments());
        binding.btnSend.setOnClickListener(v -> sendMessage());

        binding.btnSuggestHealth.setOnClickListener(v -> fastSend("Kiểm tra sức khỏe hiện tại của tôi dựa trên dữ liệu cảm biến."));
        binding.btnSuggestHeart.setOnClickListener(v -> fastSend("Giải thích các chỉ số nhịp tim của tôi có ý nghĩa gì?"));
        binding.btnSuggestSpo2.setOnClickListener(v -> fastSend("Nồng độ Oxy SpO2 của tôi hiện tại thế nào? Nó có ổn không?"));
        binding.btnSuggestEmergency.setOnClickListener(v -> fastSend("Tôi nên làm gì trong các tình huống khẩn cấp về sức khỏe?"));
        
        binding.etMessageInput.setOnClickListener(v -> hideAttachmentMenu());
    }

    private void loadHistoryForAI() {
        FirebaseDatabase.getInstance(AppConstants.DATABASE_URL).getReference("History")
                .limitToLast(50)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        historyDataList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            historyDataList.add(ds.getValue().toString());
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void initWaveformBars() {
        binding.llWaveform.removeAllViews();
        int color = ContextCompat.getColor(requireContext(), R.color.orange_main);
        for (int i = 0; i < NUM_BARS; i++) {
            View bar = new View(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(4, 16);
            params.setMargins(2, 0, 2, 0); 
            bar.setLayoutParams(params);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setColor(color);
            shape.setCornerRadius(15f); 
            bar.setBackground(shape);
            binding.llWaveform.addView(bar);
            barHeights[i] = 1f;
        }
    }

    private final Runnable waveformRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) return;
            for (int i = 0; i < NUM_BARS - 1; i++) barHeights[i] = barHeights[i + 1];
            float vol = currentVolume;
            if (vol < NOISE_THRESHOLD) vol = 0;
            float targetHeight = 1f + (vol / 2.2f);
            if (targetHeight < 1f) targetHeight = 1f;
            if (targetHeight > 5.5f) targetHeight = 5.5f; 
            barHeights[NUM_BARS - 1] = targetHeight;
            for (int i = 0; i < NUM_BARS; i++) {
                View bar = binding.llWaveform.getChildAt(i);
                if (bar != null) bar.setScaleY(barHeights[i]);
            }
            currentVolume *= 0.7f;
            waveformHandler.postDelayed(this, 50); 
        }
    };

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isRecording = true;
                binding.layoutNormalActions.setVisibility(View.GONE);
                binding.etMessageInput.setVisibility(View.GONE);
                binding.layoutVoiceRecording.setVisibility(View.VISIBLE);
                binding.llWaveform.setVisibility(View.VISIBLE);
                waveformHandler.post(waveformRunnable);
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {
                if (rmsdB > NOISE_THRESHOLD) currentVolume = rmsdB;
                else currentVolume = 0;
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { 
                isRecording = false; resetVoiceUI();
                Toast.makeText(getContext(), "Lỗi nhận dạng giọng nói", Toast.LENGTH_SHORT).show();
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) {
                    String text = data.get(0);
                    binding.etMessageInput.setText(text);
                    if (autoSendAfterVoice) sendMessage();
                }
                resetVoiceUI();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) binding.etMessageInput.setText(data.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void checkVoicePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceRecording();
        else requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startVoiceRecording() {
        autoSendAfterVoice = false;
        speechRecognizer.startListening(speechIntent);
    }

    private void stopVoiceRecording(boolean send) {
        if (!isRecording) return;
        autoSendAfterVoice = send;
        isRecording = false;
        waveformHandler.removeCallbacks(waveformRunnable);
        binding.llWaveform.setVisibility(View.GONE);
        binding.pbTranscribing.setVisibility(View.VISIBLE);
        speechRecognizer.stopListening();
    }

    private void resetVoiceUI() {
        isRecording = false;
        waveformHandler.removeCallbacks(waveformRunnable);
        binding.layoutVoiceRecording.setVisibility(View.GONE);
        binding.pbTranscribing.setVisibility(View.GONE);
        binding.layoutNormalActions.setVisibility(View.VISIBLE);
        binding.etMessageInput.setVisibility(View.VISIBLE);
    }

    private void showAttachmentMenu() {
        binding.cvAttachmentMenu.setVisibility(View.VISIBLE);
        binding.btnAttach.setBackgroundResource(R.drawable.bg_circle_button);
        binding.btnAttach.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray));
    }

    private void hideAttachmentMenu() {
        if (binding == null) return;
        binding.cvAttachmentMenu.setVisibility(View.GONE);
        binding.btnAttach.setBackground(null);
    }

    private void showAttachmentPreview(Uri uri) {
        binding.cvAttachmentPreview.setVisibility(View.VISIBLE);
        binding.ivPreview.setImageURI(uri);
        updateSendButtonVisibility();
    }

    private void setupUI() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String name = snapshot.child("fullName").getValue(String.class);
                    if (name == null || name.isEmpty()) name = user.getDisplayName();
                    if (name == null || name.isEmpty()) name = "anh/chị";
                    if (binding != null) binding.tvWelcomeName.setText("Xin chào " + name + "!");
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        binding.etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonVisibility();
                hideAttachmentMenu();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateSendButtonVisibility() {
        String text = binding.etMessageInput.getText().toString().trim();
        boolean hasContent = !text.isEmpty() || selectedFileUri != null || cameraBitmap != null;
        binding.btnSend.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        binding.btnMic.setVisibility(hasContent ? View.GONE : View.VISIBLE);
    }

    private void checkWelcomeVisibility() {
        if (chatViewModel.getChatMessages().getValue() != null && !chatViewModel.getChatMessages().getValue().isEmpty()) {
            binding.llWelcome.setVisibility(View.GONE);
            binding.rvChatMessages.setVisibility(View.VISIBLE);
        } else {
            binding.llWelcome.setVisibility(View.VISIBLE);
            binding.rvChatMessages.setVisibility(View.GONE);
        }
    }

    private void fastSend(String prompt) {
        binding.etMessageInput.setText(prompt);
        sendMessage();
    }

    private void clearAttachmentData() { selectedFileUri = null; }
    private void clearCameraData() { cameraBitmap = null; }
    private void clearAllAttachments() {
        clearAttachmentData(); clearCameraData();
        if (binding != null) {
            binding.cvAttachmentPreview.setVisibility(View.GONE);
            updateSendButtonVisibility();
        }
    }

    private void setupGenerativeModel() {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) return;
        
        try {
            Content.Builder builder = new Content.Builder();
            builder.setRole("system");
            builder.addText("BẠN LÀ 'HEALTHY 365 ASSISTANT' - TRỢ LÝ Y TẾ AI CHUYÊN NGHIỆP CỦA HỆ THỐNG AIOT.\n\n" +
                    "I. DANH TÍNH & SỨ MỆNH:\n" +
                    "- Vai trò: Y tá trợ lý ân cần (👩‍⚕️) kết hợp Chuyên gia phân tích dữ liệu y sinh.\n" +
                    "- Sứ mệnh: Giám sát, bảo vệ sức khỏe người dùng dựa trên tri thức y khoa hiện đại và dữ liệu thời gian thực.\n\n" +
                    "II. PHONG CÁCH GIAO TIẾP HỌC THUẬT & ÂN CẦN (QUY TẮC PHONG CÁCH CHUYÊN GIA):\n" +
                    "- Cấm dài dòng: BỎ QUA hoàn toàn các câu chào hỏi, rào đón rườm rà (Tuyệt đối không dùng: \"Chào bạn, tôi đã xem...\", \"Thật vui vì...\"). Đi thẳng ngay vào kết quả phân tích. \n" +
                    "- Ngôn từ: Chuyên nghiệp, chính xác y học nhưng dễ hiểu, ấm áp.\n" +
                    "- Văn phong hệ thống: Khách quan, lạnh lùng nhưng lịch sự, ân cần, đi thẳng vào vấn đề. TUYỆT ĐỐI KHÔNG dùng từ ngữ cảm thán (ví dụ: \"Ôi trời\", \"Thật nguy hiểm\", \"Dạ\", \"Vâng ạ\").\n" +
                    "- Thái độ: Ấm áp, trấn an và thấu cảm như một điều dưỡng viên y tế. \n" +
                    "- Cấu trúc: Trình bày thoáng mắt.Tối đa 2 câu cho mỗi ý. Vẫn dùng danh sách (bullet points) khi báo cáo nhiều chỉ số cùng lúc để người dùng dễ nhìn, nhưng các từ ngữ đi kèm phải mềm mại, khích lệ.\n" +
                    "- Định dạng: Bôi đậm các **chỉ số** và **từ khóa quan trọng**. Ưu tiên tốc độ đọc lướt.\n" +
                    "- Icon: Sử dụng thông minh (❤️, 🩸, 🌡️, 🏠, 💨, 🏥, 💊, ✅, ⚠️, ✨).\n\n" +
                    "III. KIẾN THỨC CHUYÊN MÔN & QUY TẮC PHÂN TÍCH (CHI TIẾT):\n" +
                    "1. ❤️ NHỊP TIM (HR/BPM):\n" +
                    "   - Bình thường (Nghỉ ngơi): **60-100 BPM**. Vận động viên: **40-60 BPM**.\n" +
                    "   - Bất thường: **<50** hoặc **>120** lúc nghỉ ngơi. Nhịp tim nhanh (Tachycardia): >100 BPM (cần nghỉ ngơi, tránh stress/cafein).\n" +
                    "   - **QUY TẮC SENSOR**: Nếu HR = 0, báo: \"Hình như bạn chưa đeo thiết bị hoặc chưa bắt đầu đo. Hãy kiểm tra lại dây đeo nhé! ✨\"\n\n" +
                    "2. 🩸 NỒNG ĐỘ OXY TRONG MÁU (SpO2):\n" +
                    "   - Bình thường: **95-100%** (Chỉ số lý tưởng). Cảnh báo: **<94%** (Cần theo dõi thêm, hít thở sâu). Nguy hiểm: **<90%** (Hypoxia).\n" +
                    "   - **QUY TẮC SENSOR**: Nếu SpO2 = 0, nhắc người dùng kiểm tra vị trí cảm biến trên da.\n\n" +
                    "3. 🌡️ NHIỆT ĐỘ (PHÂN BIỆT ĐỐI TƯỢNG):\n" +
                    "   - **🌡️ THÂN NHIỆT (TempObj)**: Đây là nhiệt độ đo từ cảm biến bề mặt da. Mức **31°C - 35°C LÀ BÌNH THƯỜNG**. Chỉ báo Sốt nếu **> 37.5°C**. **<30°C** là lành, ngoài vùng trên là nhắc nhở cảnh báo nhẹ.\n" +
                    "   - **🏠 NHIỆT ĐỘ MÔI TRƯỜNG (TempAmb)**: Nếu chênh lệch quá cao với thân nhiệt, hãy khuyên điều chỉnh trang phục.\n\n" +
                    "4. 💨 CHẤT LƯỢNG KHÔNG KHÍ (PM2.5):\n" +
                    "   - An toàn: **<50 µg/m³**. Trung bình (50-100): Người nhạy cảm nên cẩn thận.\n" +
                    "   - Cảnh báo: **>= 100 µg/m³** (Đeo khẩu trang N95, đóng cửa sổ). >200: Rất nguy hại.\n\n" +
                    "5. 📊 PHÂN TÍCH XU HƯỚNG LỊCH SỬ [HISTORY]:\n" +
                    "   - Khi nhận được dữ liệu [HISTORY], hãy so sánh các lần đo. Nếu thấy nhịp tim trung bình tăng dần qua 3-5 ngày, hãy nhắc người dùng về dấu hiệu stress hoặc mệt mỏi tiềm ẩn.\n\n" +
                    "IV. GIAO THỨC PHÂN LOẠI & KỊCH BẢN KHẨN CẤP (EMERGENCY PROTOCOL):\n" +
                    "- Nhận diện 'Cờ Đỏ': \"đau ngực\", \"khó thở\", \"chóng mặt nặng\", \"ngất\", \"tê liệt\", \"té ngã\", méo miệng, yếu nửa người HOẶC hệ thống gửi dữ liệu **SpO2 < 90%**.\n" +
                    "- Nếu phát hiện: Dừng mọi phân tích dông dài, kích hoạt ngay thông báo khẩn cấp bằng dòng chữ in hoa bôi đậm:\n" +
                    "  **⚠️ CẢNH BÁO KHẨN CẤP: DẤU HIỆU NGUY HIỂM TÍNH MẠNG.**\n" +
                    "  \"Vui lòng gọi ngay 115 hoặc nhờ người thân đưa đến cơ sở y tế gần nhất.\"\n\n" +
                    "V. RANH GIỚI Y KHOA & PHÁP LÝ - TUYỆT ĐỐI TUÂN THỦ:\n" +
                    "- BẠN KHÔNG PHẢI LÀ BÁC SĨ. KHÔNG đưa ra kết luận chẩn đoán bệnh lý (ví dụ: Không nói \"Bạn bị suy tim\", chỉ nói \"Nhịp tim đang vượt ngưỡng an toàn\").\n" +
                    "- KHÔNG ĐƯỢC PHÉP kê đơn thuốc, gợi ý tên thuốc, hoặc hướng dẫn liều lượng sử dụng.\n" +
                    "- Tư vấn dự phòng: Nhắc nhở uống đủ nước (2L/ngày), ngủ đủ 7-8h, giữ tinh thần lạc quan.\n" +
                    "- Nếu câu hỏi ngoài phạm vi: \"Xin lỗi, tôi là trợ lý y tế Healthy 365. Tôi chỉ hỗ trợ phân tích dữ liệu sức khỏe và y tế dự phòng.\"\n\n" +
                    "VI. CÂU CHỐT BẮT BUỘC:\n" +
                    "- Mọi phản hồi chứa phân tích số liệu phải kết thúc bằng dòng chữ in nghiêng: *Lưu ý: Đánh giá này dựa trên dữ liệu cảm biến và không thay thế chẩn đoán lâm sàng từ bác sĩ chuyên khoa.*");
            Content systemInstruction = builder.build();

            GenerativeModel gm = new GenerativeModel(
                "gemini-3.1-flash-lite-preview",
                apiKey.trim(),
                new GenerationConfig.Builder().build(),
                null, 
                new RequestOptions(),
                null, 
                null, 
                systemInstruction
            );
            GenerativeModelFutures model = GenerativeModelFutures.from(gm);
            chatViewModel.setChatFutures(model.startChat());
        } catch (Exception e) { 
            Log.e(TAG, "Lỗi khởi tạo model: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String text = binding.etMessageInput.getText().toString().trim();
        if (text.isEmpty() && selectedFileUri == null && cameraBitmap == null) return;

        ChatMessage userMessage = new ChatMessage(text, true);
        if (selectedFileUri != null) userMessage.setImageUri(selectedFileUri.toString());
        if (cameraBitmap != null) userMessage.setImageBitmap(cameraBitmap);
        
        chatViewModel.addMessage(userMessage);
        binding.etMessageInput.setText("");
        clearAllAttachments();

        ChatMessage typingMessage = new ChatMessage("", false);
        typingMessage.setTyping(true);
        chatViewModel.addMessage(typingMessage);

        Map<String, Object> data = sharedViewModel.getDeviceData().getValue();
        
        Object bpmVal = data != null ? (data.containsKey("BPM") ? data.get("BPM") : data.get("heart_rate")) : 0;
        Object spo2Val = data != null ? (data.containsKey("SpO2") ? data.get("SpO2") : data.get("spo2")) : 0;
        Object tempObjVal = data != null ? (data.containsKey("TempObj") ? data.get("TempObj") : data.get("temperature")) : 0;
        Object tempAmbVal = data != null ? data.get("TempAmb") : "N/A";
        Object dustVal = data != null ? (data.containsKey("Dust") ? data.get("Dust") : data.get("dust")) : 0;
        Object timeVal = data != null ? (data.containsKey("LastMeasureTime") ? data.get("LastMeasureTime") : data.get("ThoiGian")) : "Chưa có dữ liệu";

        StringBuilder historyStr = new StringBuilder("\n[HISTORY - DỮ LIỆU LỊCH SỬ 50 LẦN ĐO GẦN NHẤT]:\n");
        for (String h : historyDataList) historyStr.append(h).append("\n");

        String invisibleContext = String.format(" - {Dữ liệu hiện tại: Nhịp tim %s BPM, SpO2 %s%%, Thân nhiệt (TempObj) %s°C, Nhiệt độ môi trường (TempAmb) %s°C, Bụi mịn PM2.5 %s µg/m³, Thời gian đo: %s}%s",
                bpmVal != null ? bpmVal.toString() : "0",
                spo2Val != null ? spo2Val.toString() : "0",
                tempObjVal != null ? tempObjVal.toString() : "0",
                tempAmbVal != null ? tempAmbVal.toString() : "N/A",
                dustVal != null ? dustVal.toString() : "0",
                timeVal != null ? timeVal.toString() : "N/A",
                historyStr.toString());
        
        String fullPrompt = "{Tin nhắn người dùng: " + text + "}" + invisibleContext;

        Content.Builder userContentBuilder = new Content.Builder();
        userContentBuilder.setRole("user");
        userContentBuilder.addText(fullPrompt);

        if (selectedFileUri != null) {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(selectedFileUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                userContentBuilder.addImage(bitmap);
            } catch (Exception e) { Log.e(TAG, "File error: " + e.getMessage()); }
        } else if (cameraBitmap != null) {
            userContentBuilder.addImage(cameraBitmap);
        }

        ListenableFuture<GenerateContentResponse> response = chatViewModel.getChatFutures().sendMessage(userContentBuilder.build());
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                mainHandler.post(() -> {
                    chatViewModel.removeLastMessage();
                    String botResponse = result.getText();
                    chatViewModel.addMessage(new ChatMessage(botResponse, false));
                });
            }

            @Override
            public void onFailure(Throwable t) {
                mainHandler.post(() -> {
                    chatViewModel.removeLastMessage();
                    chatViewModel.addMessage(new ChatMessage("Lỗi kết nối Healthy 365 AI: " + t.getMessage(), false));
                });
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
}
