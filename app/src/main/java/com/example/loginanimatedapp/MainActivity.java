package com.example.loginanimatedapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.loginanimatedapp.databinding.ActivityMainBinding;
import com.example.loginanimatedapp.model.User;
import com.example.loginanimatedapp.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.FirebaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.concurrent.TimeUnit;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private static final long ANIM_DURATION = 800;
    
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private String pendingPhoneNumber;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try { getSupportActionBar().hide(); } catch (Exception ignored) {}

        initFirebase();

        setupInitialView();
        
        if (getIntent().getBooleanExtra("SHOW_REGISTER", false)) {
            showRegister(false);
        }
        
        initAction();
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mAuth.getFirebaseAuthSettings().setAppVerificationDisabledForTesting(true);
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    private void setupInitialView() {
        binding.layoutRegister.getRoot().setVisibility(View.GONE);
        binding.layoutForgotpassword.getRoot().setVisibility(View.GONE);
        binding.layoutSignupSuccess.getRoot().setVisibility(View.GONE);
        binding.layoutOtp.getRoot().setVisibility(View.GONE);
    }

    private void initAction() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.loginLayout.btnGoToSignUp.setOnClickListener(v -> showRegister(true));
        binding.layoutRegister.btnGoToSignIn.setOnClickListener(v -> showLogin(true));
        binding.loginLayout.btnForgot.setOnClickListener(v -> showForgot(true));

        binding.layoutRegister.btnRegister.setOnClickListener(v -> registerUser());
        binding.loginLayout.btnLogin.setOnClickListener(v -> loginUser());
        binding.loginLayout.btnLoginWithPhone.setOnClickListener(v -> showPhoneInputDialog());

        binding.layoutForgotpassword.btnContinue.setOnClickListener(v -> sendPasswordReset());
        binding.layoutForgotpassword.btnBackToLogin.setOnClickListener(v -> showLogin(false));

        binding.layoutSignupSuccess.btnBackToLogin.setOnClickListener(v -> showLogin(true));
        binding.layoutSignupSuccess.btnOpenGmail.setOnClickListener(v -> openGmailApp());
        
        setupOtpInputs();
        binding.layoutOtp.btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        binding.layoutOtp.tvResendOtp.setOnClickListener(v -> resendOtp());
    }

    private void openGmailApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        Intent gmailIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
        if (gmailIntent != null) {
            startActivity(gmailIntent);
        } else {
             try {
                 startActivity(Intent.createChooser(intent, "Mở ứng dụng Email"));
             } catch (Exception e) {
                 Toast.makeText(this, "Không tìm thấy ứng dụng Email nào.", Toast.LENGTH_SHORT).show();
             }
        }
    }

    private void registerUser() {
        if (!validateRegistration()) {
            return;
        }
        showLoading(true);
        String email = binding.layoutRegister.edtEmail.getText().toString().trim();
        String password = binding.layoutRegister.edtPassword.getText().toString().trim();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification().addOnCompleteListener(emailTask -> {
                                if (!emailTask.isSuccessful()) {
                                    Toast.makeText(MainActivity.this, "Không thể gửi email xác thực.", Toast.LENGTH_SHORT).show();
                                }
                            });
                            saveUserData(user);
                        }
                    } else {
                        showLoading(false);
                        Toast.makeText(MainActivity.this, "Đăng ký thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserData(FirebaseUser user) {
        String fullName = binding.layoutRegister.edtFullName.getText().toString().trim();
        String phone = binding.layoutRegister.edtPhone.getText().toString().trim();
        
        User newUser = new User(fullName, user.getEmail(), phone, null, null);

        mDatabase.child("users").child(user.getUid()).setValue(newUser)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        mAuth.signOut();
                        showSignupSuccess(true);
                    } else {
                        Toast.makeText(MainActivity.this, "Không thể lưu dữ liệu người dùng.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginUser() {
        if (!validateLogin()) {
            return;
        }
        showLoading(true);
        String email = binding.loginLayout.edtLoginEmail.getText().toString().trim();
        String password = binding.loginLayout.edtLoginPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            navigateToDashboard();
                        } else {
                            mAuth.signOut();
                            Toast.makeText(MainActivity.this, "Vui lòng xác thực email của bạn trước khi đăng nhập.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Đăng nhập thất bại. Vui lòng kiểm tra lại email hoặc mật khẩu.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendPasswordReset() {
        String email = binding.layoutForgotpassword.edtForgotPasswordEmail.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutForgotpassword.tilForgotPasswordEmail.setError("Vui lòng nhập email hợp lệ");
            return;
        }
        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        NotificationHelper.showSuccess(binding.getRoot(), "Đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra email.");
                        showLogin(true);
                    } else {
                        Toast.makeText(MainActivity.this, "Lỗi: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private boolean validateLogin() {
        boolean isValid = true;
        if (binding.loginLayout.edtLoginEmail.getText().toString().isEmpty()) {
            binding.loginLayout.tilLoginEmail.setError("Không được để trống");
            isValid = false;
        } else {
            binding.loginLayout.tilLoginEmail.setError(null);
        }
        if (binding.loginLayout.edtLoginPassword.getText().toString().isEmpty()) {
            binding.loginLayout.tilLoginPassword.setError("Không được để trống");
            isValid = false;
        } else {
            binding.loginLayout.tilLoginPassword.setError(null);
        }
        return isValid;
    }

    private boolean validateRegistration() {
        boolean isValid = true;
        if (binding.layoutRegister.edtFullName.getText().toString().isEmpty()) {
            binding.layoutRegister.tilFullName.setError("Không được để trống");
            isValid = false;
        } else {
            binding.layoutRegister.tilFullName.setError(null);
        }
        if (binding.layoutRegister.edtEmail.getText().toString().isEmpty()) {
            binding.layoutRegister.tilEmail.setError("Email là bắt buộc");
            isValid = false;
        } else {
            binding.layoutRegister.tilEmail.setError(null);
        }
        if (binding.layoutRegister.edtPassword.getText().toString().length() < 6) {
            binding.layoutRegister.tilPassword.setError("Mật khẩu phải có ít nhất 6 ký tự");
            isValid = false;
        } else {
            binding.layoutRegister.tilPassword.setError(null);
        }
        if (!binding.layoutRegister.edtConfirmPassword.getText().toString().equals(binding.layoutRegister.edtPassword.getText().toString())) {
            binding.layoutRegister.tilConfirmPassword.setError("Mật khẩu không khớp");
            isValid = false;
        } else {
            binding.layoutRegister.tilConfirmPassword.setError(null);
        }
        return isValid;
    }

    private void navigateToDashboard() {
        NotificationHelper.showSuccess(binding.getRoot(), "Đăng nhập thành công!");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1000);
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showRegister(boolean forward) {
        show(binding.layoutRegister.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
        hide(binding.layoutOtp.getRoot(), !forward);
    }

    private void showLogin(boolean forward) {
        show(binding.loginLayout.getRoot(), forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
        hide(binding.layoutOtp.getRoot(), !forward);
    }

    private void showForgot(boolean forward) {
        show(binding.layoutForgotpassword.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
        hide(binding.layoutOtp.getRoot(), !forward);
    }

    private void showSignupSuccess(boolean forward) {
        show(binding.layoutSignupSuccess.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
        hide(binding.layoutOtp.getRoot(), !forward);
    }
    
    private void showOtpLayout(boolean forward) {
        show(binding.layoutOtp.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
    }

    private int getWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private void show(View v, boolean forward) {
        if (v == null || v.getVisibility() == View.VISIBLE) return;
        v.bringToFront();
        v.setVisibility(View.VISIBLE);
        v.setTranslationX(forward ? getWidth() : -getWidth());
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void hide(View v, boolean forward) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate().translationX(forward ? getWidth() : -getWidth()).alpha(0f).setDuration(ANIM_DURATION).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }
    
    // ================== LOGIC OTP ==================
    
    private void showPhoneInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đăng nhập bằng Số điện thoại");
        builder.setMessage("Nhập số điện thoại (ví dụ: 0912345678)");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setPadding(50, 40, 50, 40);
        builder.setView(input);

        builder.setPositiveButton("Gửi mã OTP", (dialog, which) -> {
            String phone = input.getText().toString().trim();
            if (phone.isEmpty() || phone.length() < 9) {
                Toast.makeText(this, "Số điện thoại không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingPhoneNumber = phone;
            binding.layoutOtp.tvOtpInstruction.setText("Nhập mã OTP đã được gửi tới số điện thoại: " + phone);
            showOtpLayout(true);
            sendVerificationCode(phone);
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void sendVerificationCode(String phone) {
        startTimer();
        String formattedPhone = formatPhoneNumber(phone);
        
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    
    private String formatPhoneNumber(String phone) {
        if (phone.startsWith("0")) return "+84" + phone.substring(1);
        if (!phone.startsWith("+")) return "+84" + phone;
        return phone;
    }
    
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            String code = credential.getSmsCode();
            if (code != null) {
                fillOtpCode(code);
                verifyOtp(code);
            } else {
                signInWithPhoneAuthCredential(credential);
            }
        }
        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            Toast.makeText(MainActivity.this, "Gửi mã thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
            binding.layoutOtp.tvCountdown.setVisibility(View.GONE);
            binding.layoutOtp.tvResendOtp.setVisibility(View.VISIBLE);
        }
        @Override
        public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
            mVerificationId = verificationId;
            mResendToken = token;
            NotificationHelper.showSuccess(binding.getRoot(), "Mã OTP đã được gửi!");
        }
    };
    
    private void verifyOtp() {
        String code = binding.layoutOtp.edtOtp1.getText().toString() +
                binding.layoutOtp.edtOtp2.getText().toString() +
                binding.layoutOtp.edtOtp3.getText().toString() +
                binding.layoutOtp.edtOtp4.getText().toString() +
                binding.layoutOtp.edtOtp5.getText().toString() +
                binding.layoutOtp.edtOtp6.getText().toString();

        if (code.length() < 6) {
            Toast.makeText(this, "Vui lòng nhập đủ 6 số", Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.layoutOtp.btnVerifyOtp.setEnabled(false);
        binding.layoutOtp.btnVerifyOtp.setText("Đang xác thực...");
        verifyOtp(code);
    }

    private void verifyOtp(String code) {
        if (mVerificationId == null) {
            Toast.makeText(this, "Lỗi, vui lòng gửi lại mã", Toast.LENGTH_SHORT).show();
            resetOtpButton();
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    checkAndCreateUserIfPhoneNew();
                } else {
                    resetOtpButton();
                    Toast.makeText(this, "Mã OTP không đúng", Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private void checkAndCreateUserIfPhoneNew() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        
        mDatabase.child("users").child(user.getUid()).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                navigateToDashboard(); 
            } else {
                User newUser = new User("Người dùng Hệ thống", user.getPhoneNumber(), user.getPhoneNumber(), null, null);
                mDatabase.child("users").child(user.getUid()).setValue(newUser)
                    .addOnCompleteListener(t -> navigateToDashboard());
            }
        });
    }

    private void resetOtpButton() {
        binding.layoutOtp.btnVerifyOtp.setEnabled(true);
        binding.layoutOtp.btnVerifyOtp.setText("Xác thực");
    }

    private void resendOtp() {
        if (pendingPhoneNumber != null) {
            sendVerificationCode(pendingPhoneNumber);
        }
    }

    private void startTimer() {
        binding.layoutOtp.tvResendOtp.setVisibility(View.GONE);
        binding.layoutOtp.tvCountdown.setVisibility(View.VISIBLE);
        if (countDownTimer != null) countDownTimer.cancel();
        
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                binding.layoutOtp.tvCountdown.setText(String.format("Gửi lại mã sau %02ds", sec));
            }
            @Override
            public void onFinish() {
                binding.layoutOtp.tvCountdown.setVisibility(View.GONE);
                binding.layoutOtp.tvResendOtp.setVisibility(View.VISIBLE);
            }
        }.start();
    }
    
    private void fillOtpCode(String code) {
        if (code.length() >= 6) {
             binding.layoutOtp.edtOtp1.setText(String.valueOf(code.charAt(0)));
             binding.layoutOtp.edtOtp2.setText(String.valueOf(code.charAt(1)));
             binding.layoutOtp.edtOtp3.setText(String.valueOf(code.charAt(2)));
             binding.layoutOtp.edtOtp4.setText(String.valueOf(code.charAt(3)));
             binding.layoutOtp.edtOtp5.setText(String.valueOf(code.charAt(4)));
             binding.layoutOtp.edtOtp6.setText(String.valueOf(code.charAt(5)));
        }
    }
    
    private void setupOtpInputs() {
        EditText[] editTexts = {
                binding.layoutOtp.edtOtp1, binding.layoutOtp.edtOtp2, binding.layoutOtp.edtOtp3,
                binding.layoutOtp.edtOtp4, binding.layoutOtp.edtOtp5, binding.layoutOtp.edtOtp6
        };

        for (int i = 0; i < editTexts.length; i++) {
            final int index = i;
            editTexts[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < editTexts.length - 1) {
                        editTexts[index + 1].requestFocus();
                    } else if (s.length() == 0 && index > 0) {
                        editTexts[index - 1].requestFocus();
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
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
}
