package com.example.loginanimatedapp.ui.dashboard;

import android.content.Context;
import android.widget.TextView;
import com.example.loginanimatedapp.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;

public class CustomMarkerView extends MarkerView {
    private final TextView tvContent;
    private final String unit;
    private List<String> timeLabels;

    public CustomMarkerView(Context context, int layoutResource, String unit) {
        super(context, layoutResource);
        this.unit = unit;
        tvContent = findViewById(R.id.tvContent);
    }
    
    public void setTimeLabels(List<String> timeLabels) {
        this.timeLabels = timeLabels;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String dataVal;
        if (e.getY() == (int) e.getY()) {
            dataVal = ((int) e.getY()) + " " + unit;
        } else {
            dataVal = String.format("%.1f", e.getY()) + " " + unit;
        }
        
        int index = (int) e.getX();
        String timeStr = "";
        if (timeLabels != null && index >= 0 && index < timeLabels.size()) {
            timeStr = "\n" + timeLabels.get(index);
        }
        tvContent.setText(dataVal + timeStr);
        
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}
