package com.example.loginanimatedapp.ui.dashboard;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.loginanimatedapp.BuildConfig;
import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.databinding.FragmentDetailMetricBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MetricDetailFragment extends Fragment {

    public static final String ARG_METRIC_TYPE = "metric_type";
    private String metricType;
    private String displayTitle;
    private FragmentDetailMetricBinding binding;
    private DashboardViewModel dashboardViewModel;

    private float highestValue = -1f;
    private float lowestValue = -1f;
    private float totalSum = 0f;
    private int validDataCount = 0;
    private int graphXIndex = 0;
    private float currentValue = -1f; 
    private GenerateAdviceTask currentAiTask = null; 

    private String highestTime = "";
    private String lowestTime = "";
    private final List<String> timeLabels = new ArrayList<>();

    private DatabaseReference historyRef;
    private final List<Entry> historyEntries = new ArrayList<>();
    private final List<Entry> realtimeWaveformEntries = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            metricType = getArguments().getString(ARG_METRIC_TYPE);
            displayTitle = getArguments().getString("title", "Chi tiết");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDetailMetricBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        setupUI();
        setupWaveformChart(binding.chartRealtimeWaveform);
        
        loadHistoryData();
        
        List<Entry> cachedEntries = dashboardViewModel.getHistoryCache(metricType + "_wave");
        if (!cachedEntries.isEmpty()) {
            realtimeWaveformEntries.addAll(cachedEntries);
            graphXIndex = (int) cachedEntries.get(cachedEntries.size() - 1).getX() + 1;
            updateWaveformUI();
        }

        Map<String, Object> currentData = dashboardViewModel.getDeviceData().getValue();
        if (currentData != null) {
            updateRealtimeData(currentData);
        }

        dashboardViewModel.getDeviceData().observe(getViewLifecycleOwner(), this::updateRealtimeData);
        
        binding.btnExportPdf.setOnClickListener(v -> exportPDF());
    }

    @Override
    public void onDestroyView() {
        if (currentAiTask != null) {
            currentAiTask.cancel(true);
            currentAiTask = null;
        }
        super.onDestroyView();
        binding = null;
    }

    private void exportPDF() {
        Context context = getContext();
        if (context == null || validDataCount == 0 || binding == null) {
            Toast.makeText(context != null ? context : getActivity(), "Không có dữ liệu để xuất PDF!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PdfDocument pdfDocument = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            TextPaint textPaint = new TextPaint();
            textPaint.setAntiAlias(true);

            int margin = 50;
            int pageWidth = pageInfo.getPageWidth();
            int currentY = 0;

            paint.setColor(Color.parseColor("#1976D2")); 
            canvas.drawRect(0, 0, pageWidth, 100, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(20f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("HEALTHY 365", margin, 45, paint);
            
            paint.setTextSize(12f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText("Hệ Thống Giám Sát Sức Khỏe Thông Minh", margin, 70, paint);

            paint.setTextSize(16f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String reportTitle = "BÁO CÁO: " + displayTitle.toUpperCase();
            canvas.drawText(reportTitle, pageWidth - margin - paint.measureText(reportTitle), 60, paint);

            currentY = 140;

            paint.setColor(Color.BLACK);
            paint.setTextSize(13f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("THÔNG TIN TỔNG QUAN", margin, currentY, paint);
            
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(11f);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
            canvas.drawText("Thời gian xuất: " + sdf.format(new Date()), margin, currentY + 20, paint);
            
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String deviceId = prefs.getString("connected_device_id", "Chưa xác định");
            canvas.drawText("Mã thiết bị: " + deviceId, margin, currentY + 35, paint);

            int statusLevel = getStatusLevel(currentValue);
            String statusText = "BÌNH THƯỜNG";
            int statusColor = Color.parseColor("#4CAF50");
            if (statusLevel == 1) { statusText = "CẢNH BÁO"; statusColor = Color.parseColor("#FB8C00"); }
            else if (statusLevel == 2) { statusText = "NGUY HIỂM"; statusColor = Color.RED; }
            
            paint.setColor(statusColor);
            Rect statusRect = new Rect(pageWidth - margin - 120, currentY - 5, pageWidth - margin, currentY + 25);
            canvas.drawRoundRect(statusRect.left, statusRect.top, statusRect.right, statusRect.bottom, 8, 8, paint);
            
            paint.setColor(Color.WHITE);
            paint.setTextSize(11f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float statusWidth = paint.measureText(statusText);
            canvas.drawText(statusText, statusRect.centerX() - (statusWidth / 2), statusRect.centerY() + 4, paint);

            currentY += 70;

            paint.setColor(Color.parseColor("#F5F5F5"));
            canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + 90, 10, 10, paint);
            
            paint.setColor(Color.BLACK);
            paint.setTextSize(12f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("TÓM TẮT CHỈ SỐ LÂM SÀNG (7 NGÀY)", margin + 15, currentY + 25, paint);
            
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            String unit = getUnit();
            String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
            
            canvas.drawText("Trung bình: " + String.format(format, totalSum / validDataCount) + " " + unit, margin + 20, currentY + 55, paint);
            canvas.drawText("Cao nhất: " + String.format(format, highestValue) + " " + unit, margin + 180, currentY + 55, paint);
            canvas.drawText("Thấp nhất: " + String.format(format, lowestValue) + " " + unit, margin + 340, currentY + 55, paint);

            currentY += 120;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("BIỂU ĐỒ XU HƯỚNG LÂM SÀNG", margin, currentY, paint);
            currentY += 15;
            if (binding.chartHistory.getWidth() > 0) {
                Bitmap chartBitmap = binding.chartHistory.getChartBitmap();
                Rect destRect = new Rect(margin, currentY, pageWidth - margin, currentY + 180);
                canvas.drawBitmap(chartBitmap, null, destRect, null);
                currentY += 200;
            }

            CharSequence adviceText = binding.tvAdvice.getText();
            if (adviceText == null || adviceText.length() == 0 || adviceText.toString().contains("AI đang phân tích")) {
                adviceText = "Hệ thống AI đang tổng hợp dữ liệu, vui lòng tham khảo ý kiến bác sĩ trực tiếp.";
            }

            textPaint.setTextSize(12f);
            textPaint.setColor(Color.parseColor("#333333"));
            int textWidth = pageWidth - (margin * 2) - 30;
            
            StaticLayout sl;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sl = StaticLayout.Builder.obtain(adviceText, 0, adviceText.length(), textPaint, textWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0.0f, 1.2f)
                        .build();
            } else {
                sl = new StaticLayout(adviceText, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0.0f, false);
            }

            int aiBoxHeight = sl.getHeight() + 65;
            paint.setColor(Color.parseColor("#FFF8E1")); 
            canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + aiBoxHeight, 10, 10, paint);
            
            paint.setColor(Color.parseColor("#FF8F00")); 
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("PHÂN TÍCH CHUYÊN SÂU TỪ AI", margin + 15, currentY + 25, paint);
            
            canvas.save();
            canvas.translate(margin + 15, currentY + 45);
            sl.draw(canvas);
            canvas.restore();
            
            currentY += aiBoxHeight + 35;

            if (currentY > 730) currentY = 730; 
            
            paint.setColor(Color.LTGRAY);
            paint.setStrokeWidth(1f);
            canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint);
            
            currentY += 25;
            paint.setColor(Color.GRAY);
            paint.setTextSize(9f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            canvas.drawText("* Báo cáo tự động từ hệ thống Healthy 365. Kết quả chỉ mang tính chất tham khảo lâm sàng.", margin, currentY, paint);
            
            paint.setColor(Color.BLACK);
            paint.setTextSize(12f);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Xác nhận chuyên môn", pageWidth - margin - 155, currentY + 40, paint);
            canvas.drawText("___________________", pageWidth - margin - 155, currentY + 75, paint);

            pdfDocument.finishPage(page);

            File pdfDir = new File(context.getCacheDir(), "pdfs");
            if (!pdfDir.exists()) pdfDir.mkdirs();
            File pdfFile = new File(pdfDir, "MedicalReport_" + metricType + "_" + System.currentTimeMillis() + ".pdf");
            pdfDocument.writeTo(new FileOutputStream(pdfFile));
            pdfDocument.close();

            Uri pdfUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", pdfFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Gửi báo cáo y khoa PDF"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Lỗi khi xuất PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupUI() {
        String unit = getUnit();
        binding.tvMetricTitle.setText(displayTitle);
        
        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(displayTitle);
            }
        }

        setupHistoryChart(binding.chartHistory);
        setupDistributionChart(binding.chartDistribution);
        
        binding.tvStatHighest.setText("-- " + unit);
        binding.tvStatLowest.setText("-- " + unit);
        binding.tvStatAverage.setText("-- " + unit);
        
        binding.tvCurrentValue.setText("Chưa đo");
        binding.tvStatCurrent.setText("Chưa đo");
        binding.tvMetricStatusDetail.setText("Hệ thống sẵn sàng");
        binding.tvLastUpdated.setText("Cập nhật lúc: --");
        
        updateAboutDescription();
    }

    private void updateAboutDescription() {
        if (binding == null) return;
        String desc = "";
        if ("heart_rate".equals(metricType)) desc = "Nhịp tim bình thường dao động 60-100 nhịp/phút. Chỉ số này phản ánh tốc độ tim co bóp để bơm máu.";
        if ("spo2".equals(metricType)) desc = "SpO2 đo nồng độ Oxy trong máu. Mức bình thường trên 95%. Nếu dưới 94%, bạn có thể gặp vấn đề về hô hấp cấp thiết.";
        if ("temperature".equals(metricType)) desc = "Thân nhiệt bình thường: 36.5°C tới 37.5°C. Khi vượt mức này có thể bị Sốt hoặc cảnh báo viêm nhiễm.";
        if ("dust".equals(metricType)) desc = "Nồng độ bụi siêu vi PM2.5 (µg/m³). Giá trị dưới 50 là ranh giới an toàn. Bụi mịn PM2.5 có thể tiến sâu vào phổi và máu.";
        binding.tvAboutDesc.setText(desc);
    }

    private String getUnit() {
        if ("heart_rate".equals(metricType)) return "bpm";
        if ("spo2".equals(metricType)) return "%";
        if ("temperature".equals(metricType)) return "°C";
        if ("dust".equals(metricType)) return "µg/m³";
        return "";
    }

    private String[] getFirebaseNodes() {
        if ("heart_rate".equals(metricType)) return new String[]{"BPM"};
        if ("spo2".equals(metricType)) return new String[]{"SpO2"};
        if ("temperature".equals(metricType)) return new String[]{"TempObj"};
        if ("dust".equals(metricType)) return new String[]{"Dust"};
        return new String[]{};
    }

    private void loadHistoryData() {
        String[] possibleNodes = getFirebaseNodes();
        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("connected_device_id", "");

        FirebaseDatabase db = FirebaseDatabase.getInstance(BuildConfig.DATABASE_URL);
        historyRef = deviceId.isEmpty() ? db.getReference("Histories") : db.getReference("Histories").child(deviceId);
        
        historyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                historyEntries.clear();
                timeLabels.clear();
                validDataCount = 0; totalSum = 0;
                highestValue = -1f; lowestValue = -1f;
                highestTime = ""; lowestTime = "";
                int normalCount = 0; int warningCount = 0; int criticalCount = 0;

                long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
                List<DataSnapshot> validNodes = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Object tObj = child.child("ThoiGian").getValue();
                    if (tObj == null) continue;
                    try {
                        Date date = sdf.parse(String.valueOf(tObj));
                        if (date != null && date.getTime() >= sevenDaysAgo) {
                            validNodes.add(child);
                        }
                    } catch (Exception ignored) {}
                }

                int index = 0;
                for (DataSnapshot child : validNodes) {
                    Float val = findValueRecursive(child, possibleNodes, 0);
                    String timeStr = String.valueOf(child.child("ThoiGian").getValue());
                    if (val != null && val >= 0) {
                        historyEntries.add(new Entry(index++, val));
                        timeLabels.add(timeStr);
                        updateStats(val, timeStr);
                        
                        int status = getStatusLevel(val);
                        if (status == 0) normalCount++;
                        else if (status == 1) warningCount++;
                        else criticalCount++;
                    }
                }
                updateHistoryChart();
                updateDistributionChartData(normalCount, warningCount, criticalCount);
                updateStatisticsUI();
                checkAndGenerateAIAdvice();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int getStatusLevel(float val) {
        if ("heart_rate".equals(metricType)) {
            if (val < 60 || val > 100) return 2;
            return 0;
        }
        if ("spo2".equals(metricType)) {
            if (val < 94) return 2;
            return 0;
        }
        if ("temperature".equals(metricType)) {
            if (val > 37.0f || (val < 30.0f && val > 0)) return 2; 
            if ((val >= 30.0f && val < 31.0f) || (val > 35.0f && val <= 37.0f)) return 1; 
            return 0; 
        }
        if ("dust".equals(metricType)) {
            if (val > 50) return 1;
            return 0;
        }
        return 0;
    }

    private Float findValueRecursive(DataSnapshot ds, String[] nodes, int depth) {
        if (depth > 2) return null;
        for (String node : nodes) {
            DataSnapshot n = ds.child(node);
            if (n.exists() && n.getValue() != null) {
                try { return Float.parseFloat(String.valueOf(n.getValue())); } catch (Exception e) {}
            }
        }
        for (DataSnapshot c : ds.getChildren()) {
            Float f = findValueRecursive(c, nodes, depth + 1);
            if (f != null) return f;
        }
        return null;
    }

    private void updateStats(float val, String timeStr) {
        if (highestValue == -1 || val > highestValue) {
            highestValue = val;
            highestTime = timeStr;
        }
        if (lowestValue == -1 || val < lowestValue) {
            lowestValue = val;
            lowestTime = timeStr;
        }
        totalSum += val;
        validDataCount++;
    }

    private void updateStatisticsUI() {
        if (validDataCount == 0 || binding == null) return;
        String unit = getUnit();
        String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
        
        binding.tvStatHighest.setText(String.format(format, highestValue) + " " + unit + "\n(" + highestTime + ")");
        binding.tvStatHighest.setTextSize(14f);
        
        binding.tvStatLowest.setText(String.format(format, lowestValue) + " " + unit + "\n(" + lowestTime + ")");
        binding.tvStatLowest.setTextSize(14f);
        
        binding.tvStatAverage.setText(String.format(format, totalSum / validDataCount) + " " + unit);
    }

    private void setupWaveformChart(LineChart chart) {
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        
        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(false);
        
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#F0F0F0"));
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setTextSize(9f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setLabelCount(5, true);

        if ("temperature".equals(metricType)) {
            leftAxis.setAxisMinimum(25f);
            leftAxis.setAxisMaximum(42f);
        } else if ("spo2".equals(metricType)) {
            leftAxis.setAxisMinimum(88f);
            leftAxis.setAxisMaximum(101f);
        } else if ("heart_rate".equals(metricType)) {
            leftAxis.setAxisMinimum(50f);
            leftAxis.setAxisMaximum(130f);
        } else {
            leftAxis.setAxisMinimum(0f);
        }

        LineDataSet set = createWaveformDataSet();
        chart.setData(new LineData(set));
    }

    private LineDataSet createWaveformDataSet() {
        LineDataSet set = new LineDataSet(new ArrayList<>(realtimeWaveformEntries), "");
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawCircles(false);
        set.setLineWidth(2.5f);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillAlpha(35);

        int color;
        if ("heart_rate".equals(metricType)) color = Color.parseColor("#FF5252");
        else if ("spo2".equals(metricType)) color = Color.parseColor("#2196F3");
        else if ("temperature".equals(metricType)) color = Color.parseColor("#FF9800");
        else color = Color.parseColor("#607D8B");
        
        set.setColor(color);
        set.setFillColor(color);
        return set;
    }

    private void setupHistoryChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText("Đang tải dữ liệu lịch sử...");
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        
        CustomMarkerView mv = new CustomMarkerView(requireContext(), R.layout.custom_marker_view, getUnit());
        mv.setChartView(chart);
        chart.setMarker(mv);
    }

    private void updateHistoryChart() {
        if (binding == null || historyEntries.isEmpty()) return;
        
        if (binding.chartHistory.getMarker() instanceof CustomMarkerView) {
            ((CustomMarkerView) binding.chartHistory.getMarker()).setTimeLabels(timeLabels);
        }
        
        LineDataSet dataSet = new LineDataSet(historyEntries, displayTitle);
        dataSet.setColor(Color.parseColor("#607D8B"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#CFD8DC"));
        binding.chartHistory.setData(new LineData(dataSet));
        binding.chartHistory.invalidate();
    }

    private void setupDistributionChart(PieChart chart) {
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.WHITE);
        chart.setTransparentCircleRadius(61f);
        chart.setHoleRadius(58f);
        chart.setDrawCenterText(true);
        chart.setCenterText("Tình trạng");
        chart.setCenterTextSize(14f);
        
        Legend l = chart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
    }

    private void updateDistributionChartData(int normal, int warning, int critical) {
        if (binding == null || (normal == 0 && warning == 0 && critical == 0)) return;
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        
        if (normal > 0) {
            entries.add(new PieEntry(normal, "Bình thường"));
            colors.add(Color.parseColor("#66BB6A"));
        }
        if (warning > 0) {
            entries.add(new PieEntry(warning, "Cảnh báo"));
            colors.add(Color.parseColor("#FFD600"));
        }
        if (critical > 0) {
            entries.add(new PieEntry(critical, "Nguy hiểm"));
            colors.add(Color.parseColor("#EF5350"));
        }
        
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(binding.chartDistribution));
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.WHITE);
        
        binding.chartDistribution.setData(data);
        binding.chartDistribution.invalidate();
    }

    private void updateRealtimeData(Map<String, Object> data) {
        if (data == null || binding == null) return;
        
        String unit = getUnit();
        String rawTrangThai = String.valueOf(data.getOrDefault("TrangThai", ""));
        
        // KIỂM TRA TRẠNG THÁI NGOẠI TUYẾN ĐẦU TIÊN
        if (rawTrangThai.contains("NGOAI TUYEN") || rawTrangThai.contains("OFFLINE")) {
            binding.tvMetricStatusDetail.setText("Trạng thái: Thiết bị ngoại tuyến");
            binding.tvMetricStatusDetail.setTextColor(Color.GRAY);
            binding.tvCurrentValue.setText("-- " + unit);
            binding.tvCurrentValue.setTextColor(Color.GRAY);
            binding.tvLastUpdated.setText("Thiết bị mất kết nối");
            this.currentValue = -1f;
            return;
        }

        String[] nodes = getFirebaseNodes();
        float value = -1f;
        for (String node : nodes) {
            Object v = data.get(node);
            if (v != null && !String.valueOf(v).equals("null")) {
                try { value = Float.parseFloat(String.valueOf(v)); break; } catch (Exception e) {}
            }
        }

        if (value >= 0) {
            try {
                String valDisplay = ("temperature".equals(metricType)) ? String.format("%.1f", value) : String.valueOf((int)value);
                binding.tvCurrentValue.setText(valDisplay + " " + unit);
                binding.tvStatCurrent.setText(valDisplay + " " + unit);

                updateDetailStatusMessage(value);

                Object timeObj = ("dust".equals(metricType) || "temperature".equals(metricType)) ? data.get("ThoiGian") : data.get("LastMeasureTime");
                String currentTime = String.valueOf(timeObj);
                binding.tvLastUpdated.setText("Cập nhật lúc: " + currentTime);

                // Dust và Temperature: giá trị 0 là hợp lệ. BPM/SpO2: giá trị 0 nghĩa là chưa đo.
                boolean shouldAddToChart = ("dust".equals(metricType) || "temperature".equals(metricType)) ? value >= 0 : value > 0;
                if (shouldAddToChart) {
                    realtimeWaveformEntries.add(new Entry(graphXIndex++, value));
                    if (realtimeWaveformEntries.size() > 30) realtimeWaveformEntries.remove(0);
                    dashboardViewModel.updateHistoryCache(metricType + "_wave", realtimeWaveformEntries);
                    updateWaveformUI();
                }
            } catch (Exception e) {}
        }
        
        this.currentValue = value; 
    }

    private void updateDetailStatusMessage(float value) {
        if (value < 0) return;
        String status;
        int color = Color.parseColor("#4CAF50");

        if ("heart_rate".equals(metricType)) {
            if (value > 100) { status = "Cảnh báo: Nhịp tim đang cao"; color = Color.RED; }
            else if (value < 60 && value > 0) { status = "Cảnh báo: Nhịp tim đang thấp"; color = Color.RED; }
            else { status = "Trạng thái: Nhịp tim ổn định"; }
        } else if ("spo2".equals(metricType)) {
            if (value < 94 && value > 0) { status = "Cảnh báo: Nồng độ Oxy thấp"; color = Color.RED; }
            else { status = "Trạng thái: Nồng độ Oxy tốt"; }
        } else if ("temperature".equals(metricType)) {
            if (value > 37.0f) { status = "Cảnh báo: Thân nhiệt cao (Sốt)"; color = Color.RED; }
            else if (value < 30.0f && value > 0) { status = "Cảnh báo: Thân nhiệt thấp (Nguy hiểm)"; color = Color.RED; }
            else if ((value >= 30.0f && value < 31.0f) || (value > 35.0f && value <= 37.0f)) { status = "Cảnh báo: Thân nhiệt không ổn định"; color = Color.parseColor("#FFD600"); }
            else if (value >= 31.0f && value <= 35.0f) { status = "Trạng thái: Thân nhiệt bình thường"; color = Color.parseColor("#4CAF50"); }
            else { status = "Trạng thái: Bình thường"; }
        } else if ("dust".equals(metricType)) {
            if (value > 50) { status = "Cảnh báo: Chất lượng không khí kém"; color = Color.parseColor("#FB8C00"); }
            else { status = "Trạng thái: Không khí trong lành"; }
        } else {
            status = "Trạng thái: Bình thường";
        }

        binding.tvMetricStatusDetail.setText(status);
        binding.tvMetricStatusDetail.setTextColor(color);
        binding.tvCurrentValue.setTextColor(color);
    }

    private void updateWaveformUI() {
        if (binding == null) return;
        LineData ld = binding.chartRealtimeWaveform.getData();
        if (ld != null) {
            ld.clearValues();
            LineDataSet set = createWaveformDataSet();
            ld.addDataSet(set);
            ld.notifyDataChanged();
            binding.chartRealtimeWaveform.notifyDataSetChanged();
            binding.chartRealtimeWaveform.setVisibleXRangeMaximum(30);
            binding.chartRealtimeWaveform.moveViewToX(graphXIndex);
        }
    }

    private void checkAndGenerateAIAdvice() {
        if (validDataCount == 0 || binding == null) return;
        
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_API_KEY")) {
            binding.tvAdvice.setText("Để xem lời khuyên AI, vui lòng cập nhật GEMINI_API_KEY trong file local.properties.");
            return;
        }

        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        String session = "";
        if (hour >= 0 && hour < 6) session = "Buổi Sáng Sớm";
        else if (hour >= 6 && hour < 12) session = "Buổi Sáng";
        else if (hour >= 12 && hour < 18) session = "Buổi Chiều";
        else session = "Buổi Tối";

        String cacheKey = "ai_advice_" + metricType;
        String cacheSessionKey = "ai_session_" + metricType;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = sdf.format(new Date());

        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String savedSession = prefs.getString(cacheSessionKey, "");
        String savedDate = prefs.getString(cacheKey + "_date", "");
        String cachedResult = prefs.getString(cacheKey, "");

        if (savedSession.equals(session) && savedDate.equals(today) && !cachedResult.isEmpty() && !cachedResult.startsWith("Lỗi")) {
            displayFormattedAdvice(cachedResult);
            binding.tvAdviceTime.setText("Phân tích " + session + " (" + today + ")");
            return;
        }

        float avg = totalSum / validDataCount;
        String unit = getUnit();
        String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
        
        String prompt = "Dữ liệu " + displayTitle + " của tôi:\n" +
                "- Giá trị hiện tại (vừa đo): " + (currentValue > 0 ? String.format(format, currentValue) : "Chưa có dữ liệu mới") + unit + "\n" + 
                "- Trung bình 7 ngày qua: " + String.format(format, avg) + unit + "\n" + 
                "- Cao nhất (7 ngày): " + String.format(format, highestValue) + unit + "\n" +
                "- Thấp nhất (7 ngày): " + String.format(format, lowestValue) + unit + "\n" +
                "Hãy so sánh giá trị Hiện Tại với Trung Bình và đưa ra lời khuyên ngắn gọn.";

        binding.tvAdvice.setText("AI đang phân tích...");
        binding.tvAdviceTime.setText("Khung giờ đang lấy: " + session);

        if (currentAiTask != null) currentAiTask.cancel(true);
        currentAiTask = new GenerateAdviceTask(session, today, cacheKey, cacheSessionKey, apiKey);
        currentAiTask.execute(prompt);
    }

    private void displayFormattedAdvice(String rawResult) {
        if (binding == null) return;
        String formattedResult = rawResult != null ? rawResult
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                .replace("\n", "<br/>") : "";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            binding.tvAdvice.setText(Html.fromHtml(formattedResult, Html.FROM_HTML_MODE_COMPACT));
        } else {
            binding.tvAdvice.setText(Html.fromHtml(formattedResult));
        }
    }

    private class GenerateAdviceTask extends android.os.AsyncTask<String, Void, String> {
        private String session;
        private String today;
        private String cKey;
        private String cSession;
        private String apiKey;

        public GenerateAdviceTask(String s, String t, String k, String cs, String key) {
            this.session = s; this.today = t; this.cKey = k; this.cSession = cs; this.apiKey = key;
        }

        @Override
        protected String doInBackground(String... params) {
            String promptText = params[0];
            try {
                java.net.URL url = new java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject jsonBody = new org.json.JSONObject();
                
                org.json.JSONObject systemInstruction = new org.json.JSONObject();
                org.json.JSONArray siParts = new org.json.JSONArray();
                org.json.JSONObject siPart = new org.json.JSONObject();
                siPart.put("text", "BẠN LÀ TRỢ LÝ Y TẾ AI 'HEALTHY 365'.\n" +
                        "QUY TẮC PHÂN TÍCH CHỈ SỐ:\n" +
                        "1. ❤️ NHỊP TIM: 60-100 BPM là bình thường. >100 BPM báo nhịp tim nhanh (Tachycardia) - nhắc nghỉ ngơi/tránh stress. <50 hoặc >120 là bất thường nặng. Nếu chỉ số = 0: chưa đeo thiết bị.\n" +
                        "2. 🩸 SpO2: 95-100% là lý tưởng. <94% là cần theo dõi/hít thở sâu. <90% là NGUY HIỂM (Hypoxia).\n" +
                        "3. 🌡️ NHIỆT ĐỘ: Thân nhiệt đo da 31°C - 35°C LÀ BÌNH THƯỜNG. >37.5°C là Sốt. <30°C là lạnh.\n" +
                        "4. 💨 PM2.5: <50 an toàn. >100 cảnh báo (đeo khẩu trang N95). >200 rất nguy hại.\n" +
                        "QUY TẮC KHẨN CẤP: Nếu SpO2 < 90% hoặc phát hiện té ngã, in hoa bôi đậm: ⚠️ CẢNH BÁO KHẨN CẤP: DẤU HIỆU NGUY HIỂM TÍNH MẠNG. Gọi 115 ngay.\n" +
                        "PHONG CÁCH: Đi thẳng vào phân tích. Ngôn từ chuyên nghiệp, ấm áp. Không chào hỏi. Đi thẳng vào phân tích ý chính. Bôi đậm **chỉ số**. Tối đa 2 câu/ý.\n" +
                        "RANH GIỚI: Không chẩn đoán bệnh, không kê đơn thuốc. Kết thúc bằng câu in nghiêng: *Lưu ý: Đánh giá này dựa trên dữ liệu cảm biến và không thay thế chẩn đoán lâm sàng.*");
                siParts.put(siPart);
                systemInstruction.put("parts", siParts);
                jsonBody.put("system_instruction", systemInstruction);

                org.json.JSONArray contents = new org.json.JSONArray();
                org.json.JSONObject content = new org.json.JSONObject();
                org.json.JSONArray parts = new org.json.JSONArray();
                org.json.JSONObject part = new org.json.JSONObject();
                part.put("text", promptText);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                jsonBody.put("contents", contents);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
                os.close();

                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                is.close();

                org.json.JSONObject resObj = new org.json.JSONObject(response);
                org.json.JSONArray candidates = resObj.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    org.json.JSONObject firstCandidate = candidates.getJSONObject(0);
                    org.json.JSONObject contentObj = firstCandidate.getJSONObject("content");
                    org.json.JSONArray partsArr = contentObj.getJSONArray("parts");
                    if (partsArr.length() > 0) {
                        return partsArr.getJSONObject(0).getString("text");
                    }
                }
            } catch (Exception e) {
                return "Lỗi phân tích AI: " + e.getMessage();
            }
            return "Không thể nhận lời khuyên lúc này.";
        }

        @Override
        protected void onPostExecute(String result) {
            if (binding == null || !isAdded() || getContext() == null) return;

            displayFormattedAdvice(result);
            binding.tvAdviceTime.setText("Phân tích lúc: " + session + " (" + today + ")");
            
            if (!result.startsWith("Lỗi")) {
                SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                prefs.edit()
                     .putString(cKey, result)
                     .putString(cSession, session)
                     .putString(cKey + "_date", today)
                     .apply();
            }
            currentAiTask = null;
        }
    }
}
