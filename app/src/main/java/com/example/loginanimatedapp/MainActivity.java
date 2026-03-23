package com.example.loginanimatedapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.loginanimatedapp.databinding.ActivityMainBinding;
import com.example.loginanimatedapp.model.User;
import com.example.loginanimatedapp.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private static final long ANIM_DURATION = 800;

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
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    private void setupInitialView() {
        binding.layoutRegister.getRoot().setVisibility(View.GONE);
        binding.layoutForgotpassword.getRoot().setVisibility(View.GONE);
        binding.layoutSignupSuccess.getRoot().setVisibility(View.GONE);
    }

    private void initAction() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.loginLayout.btnGoToSignUp.setOnClickListener(v -> showRegister(true));
        binding.layoutRegister.btnGoToSignIn.setOnClickListener(v -> showLogin(true));
        binding.loginLayout.btnForgot.setOnClickListener(v -> showForgot(true));

        binding.layoutRegister.btnRegister.setOnClickListener(v -> registerUser());
        binding.loginLayout.btnLogin.setOnClickListener(v -> loginUser());
        binding.layoutForgotpassword.btnContinue.setOnClickListener(v -> sendPasswordReset());
        binding.layoutForgotpassword.btnBackToLogin.setOnClickListener(v -> showLogin(false));

        binding.layoutSignupSuccess.btnBackToLogin.setOnClickListener(v -> showLogin(true));
        binding.layoutSignupSuccess.btnOpenGmail.setOnClickListener(v -> openGmailApp());
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
    }

    private void showLogin(boolean forward) {
        show(binding.loginLayout.getRoot(), forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
    }

    private void showForgot(boolean forward) {
        show(binding.layoutForgotpassword.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutSignupSuccess.getRoot(), !forward);
    }

    private void showSignupSuccess(boolean forward) {
        show(binding.layoutSignupSuccess.getRoot(), forward);
        hide(binding.loginLayout.getRoot(), !forward);
        hide(binding.layoutRegister.getRoot(), !forward);
        hide(binding.layoutForgotpassword.getRoot(), !forward);
    }

    private int getWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private void show(View v, boolean forward) {
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
}
