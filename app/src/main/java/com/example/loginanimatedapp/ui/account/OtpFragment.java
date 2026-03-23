package com.example.loginanimatedapp.ui.account;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.loginanimatedapp.databinding.LayoutOtpBinding;
import com.example.loginanimatedapp.utils.NotificationHelper;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OtpFragment extends Fragment {

    private LayoutOtpBinding binding;
    private String phoneNumber;
    private CountDownTimer countDownTimer;
    private static final long START_TIME_IN_MILLIS = 60000;
    private long mTimeLeftInMillis = START_TIME_IN_MILLIS;

    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = LayoutOtpBinding.inflate(inflater, container, false);
        binding.getRoot().setVisibility(View.VISIBLE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth.setLanguageCode("vi");

        if (getArguments() != null) {
            phoneNumber = getArguments().getString("phoneNumber");
        }

        setupUI();
        setupOtpInputs();
        
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            sendVerificationCode(phoneNumber);
        } else {
            safeToast("Số điện thoại không hợp lệ");
        }
    }

    private void setupUI() {
        binding.tvOtpInstruction.setText("Nhập mã OTP vừa được gửi qua SMS đến số điện thoại " + phoneNumber);
        
        binding.toolbarOtp.setNavigationOnClickListener(v -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(v).navigateUp();
            }
        });
        
        binding.btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        
        // CẬP NHẬT: Xử lý gửi lại OTP
        binding.tvResendOtp.setOnClickListener(v -> resendOtp());
    }

    private void setupOtpInputs() {
        EditText[] editTexts = {
                binding.edtOtp1, binding.edtOtp2, binding.edtOtp3,
                binding.edtOtp4, binding.edtOtp5, binding.edtOtp6
        };

        for (int i = 0; i < editTexts.length; i++) {
            final int index = i;
            editTexts[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1) {
                        if (index < editTexts.length - 1) {
                            editTexts[index + 1].requestFocus();
                        }
                    } else if (s.length() == 0) {
                        if (index > 0) {
                            editTexts[index - 1].requestFocus();
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
            
            editTexts[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (editTexts[index].getText().toString().isEmpty() && index > 0) {
                        editTexts[index - 1].requestFocus();
                        editTexts[index - 1].setText(""); 
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void startTimer() {
        binding.tvResendOtp.setVisibility(View.GONE);
        binding.tvCountdown.setVisibility(View.VISIBLE);
        
        mTimeLeftInMillis = START_TIME_IN_MILLIS; // Reset thời gian mỗi khi bắt đầu

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(mTimeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mTimeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                mTimeLeftInMillis = 0;
                if (binding != null) {
                    binding.tvCountdown.setVisibility(View.GONE);
                    binding.tvResendOtp.setVisibility(View.VISIBLE);
                    binding.tvResendOtp.setEnabled(true);
                }
            }
        }.start();
    }

    private void updateCountDownText() {
        if (binding == null) return;
        int minutes = (int) (mTimeLeftInMillis / 1000) / 60;
        int seconds = (int) (mTimeLeftInMillis / 1000) % 60;
        String timeLeftFormatted = String.format("Gửi lại mã sau %02d:%02d", minutes, seconds);
        binding.tvCountdown.setText(timeLeftFormatted);
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";
        if (phone.startsWith("0")) {
            return "+84" + phone.substring(1);
        } else if (!phone.startsWith("+")) {
            return "+84" + phone;
        }
        return phone;
    }

    private void sendVerificationCode(String phone) {
        startTimer();
        String formattedPhone = formatPhoneNumber(phone);
        Log.d("OtpFragment", "Sending verification code to: " + formattedPhone);

        if (getActivity() == null) return;

        PhoneAuthOptions.Builder optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(getActivity()) 
                .setCallbacks(mCallbacks);

        if (mResendToken != null) {
            optionsBuilder.setForceResendingToken(mResendToken);
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
    }

    private void resendOtp() {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            sendVerificationCode(phoneNumber);
            NotificationHelper.showSuccess(requireView(), "Đang gửi lại mã OTP...");
        } else {
             safeToast("Số điện thoại không hợp lệ để gửi lại.");
        }
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            String code = credential.getSmsCode();
            if (code != null) {
                if (binding != null) fillOtpCode(code);
                verifyOtp(code);
            } else {
                signInWithPhoneAuthCredential(credential);
            }
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            Log.e("OtpFragment", "onVerificationFailed", e);
            String errorMsg = "Xác thực thất bại: " + e.getLocalizedMessage();
            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                errorMsg = "Số điện thoại không hợp lệ hoặc định dạng sai.";
            } else if (e instanceof FirebaseTooManyRequestsException) {
                errorMsg = "Hạn ngạch SMS đã hết hoặc gửi quá nhiều lần. Vui lòng thử lại sau.";
            }
            safeToast(errorMsg);
            
            // Nếu gửi mã thất bại ngay lần đầu, hiện lại nút gửi lại
            if (binding != null) {
                binding.tvCountdown.setVisibility(View.GONE);
                binding.tvResendOtp.setVisibility(View.VISIBLE);
                binding.tvResendOtp.setEnabled(true);
            }
        }

        @Override
        public void onCodeSent(@NonNull String verificationId,
                @NonNull PhoneAuthProvider.ForceResendingToken token) {
            Log.d("OtpFragment", "onCodeSent:" + verificationId);
            mVerificationId = verificationId;
            mResendToken = token;
            NotificationHelper.showSuccess(requireView(), "Mã OTP đã được gửi!");
        }
    };
    
    private void fillOtpCode(String code) {
        if (code.length() >= 6 && binding != null) {
             binding.edtOtp1.setText(String.valueOf(code.charAt(0)));
             binding.edtOtp2.setText(String.valueOf(code.charAt(1)));
             binding.edtOtp3.setText(String.valueOf(code.charAt(2)));
             binding.edtOtp4.setText(String.valueOf(code.charAt(3)));
             binding.edtOtp5.setText(String.valueOf(code.charAt(4)));
             binding.edtOtp6.setText(String.valueOf(code.charAt(5)));
        }
    }

    private void verifyOtp() {
        String code = binding.edtOtp1.getText().toString() +
                binding.edtOtp2.getText().toString() +
                binding.edtOtp3.getText().toString() +
                binding.edtOtp4.getText().toString() +
                binding.edtOtp5.getText().toString() +
                binding.edtOtp6.getText().toString();

        if (code.length() < 6) {
            safeToast("Vui lòng nhập đủ 6 số");
            return;
        }
        
        binding.btnVerifyOtp.setEnabled(false);
        binding.btnVerifyOtp.setText("Đang xác thực...");
        verifyOtp(code);
    }

    private void verifyOtp(String code) {
        if (mVerificationId == null) {
            safeToast("Lỗi xác thực, vui lòng bấm Gửi lại mã");
            resetButtonState();
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void resetButtonState() {
        if (binding != null) {
            binding.btnVerifyOtp.setEnabled(true);
            binding.btnVerifyOtp.setText("Xác thực");
        }
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        if (mAuth.getCurrentUser() != null) {
             mAuth.getCurrentUser().updatePhoneNumber(credential)
                 .addOnCompleteListener(task -> {
                     if (!isAdded()) return;
                     
                     if (task.isSuccessful()) {
                         updatePhoneNumberInDatabase(phoneNumber);
                     } else {
                         resetButtonState();
                         String msg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định";
                         if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                             msg = "Mã OTP không đúng.";
                         } else if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                             msg = "Số điện thoại này đã được liên kết với một tài khoản khác.";
                         }
                         safeToast("Lỗi: " + msg);
                     }
                 });
        } else {
             mAuth.signInWithCredential(credential)
                 .addOnCompleteListener(task -> {
                     if (!isAdded()) return;
                     
                     if (task.isSuccessful()) {
                         if (mAuth.getCurrentUser() != null) {
                             updatePhoneNumberInDatabase(phoneNumber);
                         } else {
                             onSuccess();
                         }
                     } else {
                         resetButtonState();
                         String msg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định";
                         if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                             msg = "Mã OTP không đúng.";
                         }
                         safeToast("Lỗi: " + msg);
                     }
                 });
        }
    }

    private void updatePhoneNumberInDatabase(String phone) {
        if (mAuth.getCurrentUser() == null) {
            safeToast("Người dùng chưa đăng nhập, không thể lưu số điện thoại.");
            resetButtonState();
            return;
        }
        
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("phone", phone);

        mDatabase.child("users").child(uid).updateChildren(updates)
            .addOnCompleteListener(task -> {
                if (!isAdded()) return;
                
                if (task.isSuccessful()) {
                    onSuccess();
                } else {
                    resetButtonState();
                    safeToast("Lỗi lưu số điện thoại: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                }
            });
    }

    private void onSuccess() {
        if (getView() != null) {
            NotificationHelper.showSuccess(requireView(), "Xác thực thành công!");
        }
        if (isAdded() && getView() != null) {
             Navigation.findNavController(getView()).navigateUp();
        }
    }
    
    private void safeToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        binding = null;
    }
}
