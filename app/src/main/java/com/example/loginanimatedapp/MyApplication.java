package com.example.loginanimatedapp;

import android.app.Application;
import com.example.loginanimatedapp.BuildConfig;
import com.google.firebase.database.FirebaseDatabase;

// Cấu hình ứng dụng toàn cục
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL).setPersistenceEnabled(true);
        } catch (Exception ignored) { }
    }
}
