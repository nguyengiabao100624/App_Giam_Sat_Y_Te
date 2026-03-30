package com.example.loginanimatedapp.utils;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        View snackbarView = snackbar.getView();

        if (snackbarView instanceof ViewGroup) {
            ViewGroup layout = (ViewGroup) snackbarView;
            layout.setElevation(0f);
            layout.setBackgroundColor(Color.TRANSPARENT);
            layout.setPadding(0, 0, 0, 0);

            int snackbarTextId = view.getResources().getIdentifier("snackbar_text", "id", "com.google.android.material");
            View defaultText = layout.findViewById(snackbarTextId);
            if (defaultText != null) defaultText.setVisibility(View.INVISIBLE);

            // Inflate custom view
            View customView = LayoutInflater.from(view.getContext()).inflate(R.layout.layout_custom_snackbar, layout, false);

            TextView messageTv = customView.findViewById(R.id.tv_message);
            if (messageTv != null) messageTv.setText(message);

            ImageView iconIv = customView.findViewById(R.id.iv_icon);
            if (iconIv != null) {
                iconIv.setImageResource(iconResId);
                iconIv.setColorFilter(tintColor);
            }

            View closeBtn = customView.findViewById(R.id.btn_close);
            if (closeBtn != null) closeBtn.setOnClickListener(v -> snackbar.dismiss());

            layout.addView(customView, 0);
        }

        snackbar.show();
    }
}
