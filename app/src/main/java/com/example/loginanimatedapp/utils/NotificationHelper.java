package com.example.loginanimatedapp.utils;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.loginanimatedapp.R;
import com.google.android.material.snackbar.Snackbar;

public class NotificationHelper {

    private static final int CUSTOM_DURATION = 5000;

    public static void showSuccess(View view, String message) {
        showCustomSnackbar(view, message, R.drawable.ic_check_circle_green, Color.parseColor("#4CAF50"));
    }

    public static void showInfo(View view, String message) {
        showCustomSnackbar(view, message, R.drawable.ic_info_circle_blue, Color.parseColor("#2196F3"));
    }

    public static void showError(View view, String message) {
        showCustomSnackbar(view, message, R.drawable.ic_error_circle_red, Color.parseColor("#F44336"));
    }

    private static void showCustomSnackbar(View view, String message, int iconResId, int tintColor) {
        if (view == null) return;
        
        final Snackbar snackbar = Snackbar.make(view, "", CUSTOM_DURATION);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();

        layout.setElevation(0f);
        layout.setBackgroundColor(Color.TRANSPARENT);
        layout.setPadding(0, 0, 0, 0);

        TextView textView = layout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) textView.setVisibility(View.INVISIBLE);

        View customView = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_custom_snackbar, null);
        
        TextView messageTv = customView.findViewById(R.id.tv_message);
        messageTv.setText(message);
        
        ImageView iconIv = customView.findViewById(R.id.iv_icon);
        iconIv.setImageResource(iconResId);
        iconIv.setColorFilter(tintColor);

        customView.findViewById(R.id.btn_close).setOnClickListener(v -> snackbar.dismiss());

        layout.addView(customView, 0);
        snackbar.show();
    }
}
