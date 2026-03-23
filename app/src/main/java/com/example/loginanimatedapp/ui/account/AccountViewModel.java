package com.example.loginanimatedapp.ui.account;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loginanimatedapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class AccountViewModel extends ViewModel {

    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> updateSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPhoneAvailable = new MutableLiveData<>();

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    
    // Sử dụng getInstance() mặc định để Firebase tự lấy thông tin từ file google-services.json
    private final FirebaseStorage mStorage = FirebaseStorage.getInstance();

    public LiveData<User> getUserLiveData() {
        return userLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    public LiveData<Boolean> getUpdateSuccessLiveData() {
        return updateSuccessLiveData;
    }
    
    public LiveData<Boolean> getIsPhoneAvailable() {
        return isPhoneAvailable;
    }

    public void resetUpdateSuccess() {
        updateSuccessLiveData.setValue(false);
    }

    public void resetPhoneAvailability() {
        isPhoneAvailable.setValue(null);
    }

    public void fetchUserData() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            DatabaseReference userRef = mDatabase.child("users").child(firebaseUser.getUid());
            userRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    User user = dataSnapshot.getValue(User.class);
                    if (user != null) {
                        userLiveData.postValue(user);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    errorLiveData.postValue(databaseError.getMessage());
                }
            });
        }
    }
    
    public void checkPhoneAvailability(String phone) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        Query query = mDatabase.child("users").orderByChild("phone").equalTo(phone);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean available = true;
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    if (!userSnap.getKey().equals(currentUser.getUid())) {
                        available = false;
                        break;
                    }
                }
                isPhoneAvailable.postValue(available);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                errorLiveData.postValue("Lỗi kiểm tra số điện thoại: " + error.getMessage());
            }
        });
    }

    public void updateUserProfile(User user, Uri newAvatarUri) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            errorLiveData.postValue("Lỗi: Bạn cần đăng nhập lại.");
            return;
        }

        if (newAvatarUri != null) {
            uploadAvatarAndThenUpdateUser(firebaseUser.getUid(), user, newAvatarUri);
        } else {
            updateUserData(firebaseUser.getUid(), user);
        }
    }

    private void uploadAvatarAndThenUpdateUser(String userId, User user, Uri avatarUri) {
        // Lưu ảnh trực tiếp vào root để test quyền
        StorageReference avatarRef = mStorage.getReference().child(userId + ".jpg");
        
        Log.d("FirebaseStorage", "Đang thử upload lên bucket: " + mStorage.getReference().getBucket());
        
        avatarRef.putFile(avatarUri)
            .addOnSuccessListener(taskSnapshot -> {
                avatarRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    user.avatarUrl = downloadUri.toString();
                    updateUserData(userId, user);
                });
            })
            .addOnFailureListener(e -> {
                Log.e("FirebaseStorage", "Chi tiết lỗi: ", e);
                // Hiển thị thông báo lỗi ngắn gọn nhưng đủ ý
                errorLiveData.postValue("Lỗi: " + e.getLocalizedMessage());
                updateSuccessLiveData.postValue(false);
            });
    }

    private void updateUserData(String userId, User user) {
        mDatabase.child("users").child(userId).setValue(user)
            .addOnSuccessListener(aVoid -> updateSuccessLiveData.postValue(true))
            .addOnFailureListener(e -> {
                errorLiveData.postValue("Lỗi Database: " + e.getMessage());
                updateSuccessLiveData.postValue(false);
            });
    }
}
