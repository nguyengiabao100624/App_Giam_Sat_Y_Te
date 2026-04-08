package com.example.loginanimatedapp.ui.account;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.loginanimatedapp.MainActivity;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.databinding.FragmentAccountBinding;
import com.example.loginanimatedapp.model.User;
import com.example.loginanimatedapp.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;

public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;
    private AccountViewModel accountViewModel;

    private User currentUser;
    private Uri newAvatarUri = null;
    private ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMedia;
    private FirebaseAuth mAuth;

    private boolean isPhoneVisible = false;
    private boolean isEmailVisible = false;
    private String pendingPhoneNumber = "";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        mAuth = FirebaseAuth.getInstance();

        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Tài khoản");
        }

        setupImagePicker();
        setupObservers();
        setupClickListeners();
        setupTextWatchers();

        checkAuthState();
    }

    private void checkAuthState() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showGuestView();
        } else {
            showLoggedInView();
            accountViewModel.fetchUserData();
        }
    }

    private void showGuestView() {
        binding.guestView.setVisibility(View.VISIBLE);
        binding.loggedInView.setVisibility(View.GONE);
    }

    private void showLoggedInView() {
        binding.guestView.setVisibility(View.GONE);
        binding.loggedInView.setVisibility(View.VISIBLE);
    }

    private void setupImagePicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                newAvatarUri = uri;
                Glide.with(this).load(newAvatarUri).into(binding.ivAvatar); 
                Glide.with(this).load(newAvatarUri).into(binding.ivEditAvatar); 
                checkForChanges();
            } else {
                Toast.makeText(getContext(), "Chưa chọn ảnh nào", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupObservers() {
        accountViewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentUser = user;
                updateUI(user);
                resetChangeTracking();
            }
        });

        accountViewModel.getErrorLiveData().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), "Lỗi: " + error, Toast.LENGTH_LONG).show();
            }
        });

        accountViewModel.getUpdateSuccessLiveData().observe(getViewLifecycleOwner(), success -> {
            if(success) {
                NotificationHelper.showSuccess(requireActivity().findViewById(android.R.id.content), "Cập nhật hồ sơ thành công!");
                newAvatarUri = null;
                accountViewModel.resetUpdateSuccess(); 
                accountViewModel.fetchUserData();
            }
        });

        accountViewModel.getIsPhoneAvailable().observe(getViewLifecycleOwner(), isAvailable -> {
            if (isAvailable != null) {
                if (isAvailable) {
                    // Phone is available, proceed to OTP
                    Bundle args = new Bundle();
                    args.putString("phoneNumber", pendingPhoneNumber);
                    Navigation.findNavController(requireView()).navigate(R.id.action_account_to_otp, args);
                } else {
                    NotificationHelper.showError(requireView(), "Số điện thoại này đã được liên kết với một tài khoản khác.");
                }
                accountViewModel.resetPhoneAvailability();
                pendingPhoneNumber = ""; // Reset
            }
        });
    }

    private void updateUI(User user) {
        FirebaseUser fUser = mAuth.getCurrentUser();
        if (fUser == null) return;

        binding.tvUserName.setText(user.fullName);
        
        String phoneToDisplay = (user.phone != null && !user.phone.isEmpty()) ? user.phone : "";
        String verifiedPhone = fUser.getPhoneNumber();
        boolean isPhoneVerified = (verifiedPhone != null && !verifiedPhone.isEmpty());

        if (isPhoneVerified) {
             binding.tvPhoneVerifiedHeader.setVisibility(View.VISIBLE);
             binding.btnVerifyOTP.setVisibility(View.GONE); 
             binding.edtEditPhone.setEnabled(false); 
             if (phoneToDisplay.isEmpty()) {
                 phoneToDisplay = verifiedPhone;
             }
        } else {
             binding.tvPhoneVerifiedHeader.setVisibility(View.GONE);
             binding.btnVerifyOTP.setVisibility(View.VISIBLE);
             binding.edtEditPhone.setEnabled(true);
        }
        
        updateMaskedPhone(phoneToDisplay);
        binding.edtEditPhone.setText(phoneToDisplay);

        if (fUser.getEmail() != null && !fUser.getEmail().isEmpty()) {
            updateMaskedEmail(fUser.getEmail());
            binding.btnVerifyEmail.setVisibility(fUser.isEmailVerified() ? View.GONE : View.VISIBLE);
            binding.tvEmailVerified.setVisibility(fUser.isEmailVerified() ? View.VISIBLE : View.GONE);
            binding.edtEditEmail.setEnabled(!fUser.isEmailVerified());
        } else {
            binding.tvUserEmail.setText("Chưa có email");
            binding.btnVerifyEmail.setText("Thêm & Xác thực");
            binding.edtEditEmail.setEnabled(true);
        }

        Glide.with(this).load(user.avatarUrl).placeholder(R.drawable.ic_account_circle_24).into(binding.ivAvatar);
        Glide.with(this).load(user.avatarUrl).placeholder(R.drawable.ic_account_circle_24).into(binding.ivEditAvatar);
        
        binding.edtEditName.setText(user.fullName);
        binding.edtEditEmail.setText(fUser.getEmail() != null ? fUser.getEmail() : "");
        binding.edtEditDob.setText(user.dob != null ? user.dob : "");

        if (user.gender != null) {
            if (user.gender.equalsIgnoreCase("nam") || user.gender.equalsIgnoreCase("male")) {
                binding.rbMale.setChecked(true);
            } else if (user.gender.equalsIgnoreCase("nữ") || user.gender.equalsIgnoreCase("female")) {
                binding.rbFemale.setChecked(true);
            }
        }
    }

    private void updateMaskedPhone(String phone) {
        if (isPhoneVisible) {
            binding.tvUserPhone.setText(phone);
            binding.btnTogglePhoneVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            binding.tvUserPhone.setText(maskPhone(phone));
            binding.btnTogglePhoneVisibility.setImageResource(R.drawable.ic_visibility);
        }
    }

    private void updateMaskedEmail(String email) {
        if (isEmailVisible) {
            binding.tvUserEmail.setText(email);
            binding.btnToggleEmailVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            binding.tvUserEmail.setText(maskEmail(email));
            binding.btnToggleEmailVisibility.setImageResource(R.drawable.ic_visibility);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf("@");
        if (atIndex < 3) return email;
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }

    private void setupClickListeners() {
        binding.tvLogout.setOnClickListener(v -> showLogoutDialog());
        binding.tvDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
        binding.ivEditAvatar.setOnClickListener(v -> openImagePicker());
        binding.btnVerifyEmail.setOnClickListener(v -> handleEmailVerification());
        binding.btnUpdateProfile.setOnClickListener(v -> updateUserProfile());
        binding.btnVerifyOTP.setOnClickListener(v -> navigateToOtp());
        
        binding.edtEditDob.setOnClickListener(v -> showDatePicker());
        
        binding.btnTogglePhoneVisibility.setOnClickListener(v -> {
            isPhoneVisible = !isPhoneVisible;
            if (currentUser != null) {
                String ph = binding.edtEditPhone.getText().toString(); 
                updateMaskedPhone(ph.isEmpty() ? currentUser.phone : ph); 
            }
        });

        binding.btnToggleEmailVisibility.setOnClickListener(v -> {
            isEmailVisible = !isEmailVisible;
            FirebaseUser fUser = mAuth.getCurrentUser();
            if (fUser != null && fUser.getEmail() != null) {
                updateMaskedEmail(fUser.getEmail());
            }
        });

        binding.btnLogin.setOnClickListener(v -> navigateToLogin(true));
        binding.btnRegister.setOnClickListener(v -> navigateToLogin(false));
    }
    
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        String currentDob = binding.edtEditDob.getText().toString();
        if (!currentDob.isEmpty() && currentDob.contains("/")) {
            try {
                String[] parts = currentDob.split("/");
                if (parts.length == 3) {
                    calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[0]));
                    calendar.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                    calendar.set(Calendar.YEAR, Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {}
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String date = dayOfMonth + "/" + (month + 1) + "/" + year;
                    binding.edtEditDob.setText(date);
                    checkForChanges();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void navigateToOtp() {
        String phoneNumber = binding.edtEditPhone.getText().toString().trim();
        if (phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng nhập số điện thoại trước", Toast.LENGTH_SHORT).show();
            return;
        }
        
        NotificationHelper.showInfo(requireView(), "Đang kiểm tra số điện thoại...");
        pendingPhoneNumber = phoneNumber;
        accountViewModel.checkPhoneAvailability(phoneNumber);
    }

    private void openImagePicker() {
        pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Ở lại", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Đăng xuất", (dialog, which) -> {
                    mAuth.signOut();
                    checkAuthState();
                    NotificationHelper.showSuccess(requireActivity().findViewById(android.R.id.content), "Đã đăng xuất");
                })
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xoá tài khoản")
                .setMessage("Bạn có chắc muốn xoá tài khoản vĩnh viễn không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Ở lại", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Xoá tài khoản", (dialog, which) -> {
                    deleteUserAccount();
                })
                .show();
    }

    private void deleteUserAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.delete().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    NotificationHelper.showSuccess(requireActivity().findViewById(android.R.id.content), "Tài khoản đã được xoá.");
                    checkAuthState();
                } else {
                    Exception e = task.getException();
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        new AlertDialog.Builder(requireContext())
                            .setTitle("Yêu cầu xác thực lại")
                            .setMessage("Để đảm bảo an toàn, bạn cần đăng nhập lại trước khi xoá tài khoản.")
                            .setPositiveButton("Đăng nhập lại", (dialog, which) -> {
                                mAuth.signOut();
                                navigateToLogin(true);
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                    } else {
                        Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void navigateToLogin(boolean isLogin) {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        if (!isLogin) {
            intent.putExtra("SHOW_REGISTER", true);
        }
        startActivity(intent);
    }
    
    private void updateUserProfile() {
        binding.btnUpdateProfile.setEnabled(false);
        setUpdateButtonStyle(false); 
        NotificationHelper.showInfo(requireActivity().findViewById(android.R.id.content), "Đang cập nhật...");

        String newName = binding.edtEditName.getText().toString();
        String newEmail = binding.edtEditEmail.getText().toString();
        String newPhone = binding.edtEditPhone.getText().toString();
        String newDob = binding.edtEditDob.getText().toString();
        
        RadioButton selectedGender = binding.getRoot().findViewById(binding.rgGender.getCheckedRadioButtonId());
        String newGender = selectedGender != null ? selectedGender.getText().toString() : "";

        String avatarUrl = (currentUser != null && currentUser.avatarUrl != null) ? currentUser.avatarUrl : null;
        User updatedUser = new User(newName, newEmail, newPhone, newGender.toLowerCase(), avatarUrl, newDob);
        accountViewModel.updateUserProfile(updatedUser, newAvatarUri);
    }

    private void handleEmailVerification() {
         FirebaseUser user = mAuth.getCurrentUser();
         if (user != null) {
             user.sendEmailVerification()
                 .addOnCompleteListener(task -> {
                     if (task.isSuccessful()) {
                         NotificationHelper.showSuccess(requireActivity().findViewById(android.R.id.content), "Email xác thực đã được gửi đến " + user.getEmail());
                     } else {
                         Toast.makeText(getContext(), "Không thể gửi email xác thực.", Toast.LENGTH_SHORT).show();
                     }
                 });
         }
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkForChanges();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.edtEditName.addTextChangedListener(textWatcher);
        binding.edtEditEmail.addTextChangedListener(textWatcher);
        binding.edtEditPhone.addTextChangedListener(textWatcher);
        binding.edtEditDob.addTextChangedListener(textWatcher);
        binding.rgGender.setOnCheckedChangeListener((group, checkedId) -> checkForChanges());
    }

    private void checkForChanges() {
        if (currentUser == null) return;
        
        boolean nameChanged = !binding.edtEditName.getText().toString().equals(currentUser.fullName != null ? currentUser.fullName : "");
        boolean emailChanged = !binding.edtEditEmail.getText().toString().equals(currentUser.email != null ? currentUser.email : "");
        boolean phoneChanged = !binding.edtEditPhone.getText().toString().equals(currentUser.phone != null ? currentUser.phone : "");
        boolean dobChanged = !binding.edtEditDob.getText().toString().equals(currentUser.dob != null ? currentUser.dob : "");
        
        String currentGender = currentUser.gender != null ? currentUser.gender : "";
        RadioButton selectedGender = binding.getRoot().findViewById(binding.rgGender.getCheckedRadioButtonId());
        String newGender = selectedGender != null ? selectedGender.getText().toString().toLowerCase() : "";
        boolean genderChanged = !newGender.equalsIgnoreCase(currentGender);
        
        boolean avatarChanged = newAvatarUri != null;
        
        boolean hasChanges = nameChanged || emailChanged || genderChanged || avatarChanged || phoneChanged || dobChanged;
        
        binding.btnUpdateProfile.setEnabled(hasChanges);
        setUpdateButtonStyle(hasChanges);
    }
    
    private void setUpdateButtonStyle(boolean enabled) {
        if (enabled) {
            binding.btnUpdateProfile.setBackgroundResource(R.drawable.btn_filled_orange);
            binding.btnUpdateProfile.setTextColor(Color.WHITE);
        } else {
            binding.btnUpdateProfile.setBackgroundColor(Color.LTGRAY);
            binding.btnUpdateProfile.setTextColor(Color.DKGRAY);
        }
    }

    private void resetChangeTracking() {
        newAvatarUri = null;
        checkForChanges();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
