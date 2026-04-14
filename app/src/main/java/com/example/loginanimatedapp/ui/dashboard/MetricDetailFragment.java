package com.example.loginanimatedapp.ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
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
    private GenerateAdviceTask currentAiTask = null; // Quản lý task AI để tránh văng App

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
        // HỦY TASK AI KHI THOÁT MÀN HÌNH ĐỂ TRÁNH CRASH
        if (currentAiTask != null) {
            currentAiTask.cancel(true);
            currentAiTask = null;
        }
        super.onDestroyView();
        binding = null;
    }

    private void exportPDF() {
        if (validDataCount == 0 || binding == null) {
            Toast.makeText(getContext(), "Không có dữ liệu để xuất PDF!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PdfDocument pdfDocument = new PdfDocument();
            Paint paint = new Paint();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            paint.setTextSize(24f);
            paint.setFakeBoldText(true);
            canvas.drawText("BÁO CÁO Y TẾ - " + displayTitle.toUpperCase(), 50, 80, paint);

            paint.setTextSize(16f);
            paint.setFakeBoldText(false);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            canvas.drawText("Ngày xuất: " + sdf.format(new Date()), 50, 120, paint);

            String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
            String unit = getUnit();

            canvas.drawText("Chi tiết thống kê:", 50, 170, paint);
            canvas.drawText("- Giá trị cao nhất: " + String.format(format, highestValue) + " " + unit, 70, 210, paint);
            canvas.drawText("- Giá trị thấp nhất: " + String.format(format, lowestValue) + " " + unit, 70, 250, paint);
            canvas.drawText("- Giá trị trung bình: " + String.format(format, totalSum / validDataCount) + " " + unit, 70, 290, paint);

            pdfDocument.finishPage(page);

            File pdfDir = new File(requireContext().getCacheDir(), "pdfs");
            if (!pdfDir.exists()) pdfDir.mkdirs();
            
            File pdfFile = new File(pdfDir, "BaoCao_" + metricType + "_" + System.currentTimeMillis() + ".pdf");
            pdfDocument.writeTo(new FileOutputStream(pdfFile));
            pdfDocument.close();

            Uri pdfUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", pdfFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ báo cáo PDF"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi khi tạo PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                // Sửa format ngày để khớp với dữ liệu từ thiết bị (HH:mm:ss dd/MM/yyyy)
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
                List<DataSnapshot> validNodes = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    // XÓA BỎ LỌC 'Do lien tuc' để lấy toàn bộ lịch sử đo (bao gồm cả đo snapshot)
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
            if (val > 37.0f || (val < 30.0f && val > 0)) return 2; // Critical (Đỏ)
            if ((val >= 30.0f && val < 31.0f) || (val > 35.0f && val <= 37.0f)) return 1; // Warning (Vàng)
            return 0; // Normal (Xanh)
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
        boolean isMeasurementBased = "heart_rate".equals(metricType) || "spo2".equals(metricType);
        
        if (isMeasurementBased) {
            Object lastTime = data.get("LastMeasureTime");
            String timeStr = String.valueOf(lastTime);
            if (lastTime == null || timeStr.equalsIgnoreCase("Chua do") || timeStr.trim().isEmpty() || timeStr.equals("null")) {
                binding.tvCurrentValue.setText("Chưa đo");
                binding.tvStatCurrent.setText("Chưa đo");
                binding.tvMetricStatusDetail.setText("Chưa có dữ liệu đo mới nhất");
                binding.tvLastUpdated.setText("Cập nhật lúc: Chưa đo");
                return;
            }
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

                if (value > 0) {
                    realtimeWaveformEntries.add(new Entry(graphXIndex++, value));
                    if (realtimeWaveformEntries.size() > 30) realtimeWaveformEntries.remove(0);
                    dashboardViewModel.updateHistoryCache(metricType + "_wave", realtimeWaveformEntries);
                    updateWaveformUI();
                }
            } catch (Exception e) {}
        }
    }

    private void updateDetailStatusMessage(float value) {
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

    // ============================================
    // LOGIC TÍCH HỢP GEMINI API VÀO 4 BUỔI
    // ============================================
    private void checkAndGenerateAIAdvice() {
        if (validDataCount == 0 || binding == null) return;
        
        // SỬ DỤNG API KEY TỪ BUILDCONFIG (BẢO MẬT)
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

        // KIỂM TRA THÔNG MINH: Nếu đúng Ngày + đúng Khung giờ + đã có dữ liệu (không phải lỗi) -> Dùng lại cache
        if (savedSession.equals(session) && savedDate.equals(today) && !cachedResult.isEmpty() && !cachedResult.startsWith("Lỗi")) {
            binding.tvAdvice.setText(cachedResult);
            binding.tvAdviceTime.setText("Phân tích " + session + " (" + today + ")");
            return;
        }

        // Tạo Prompt
        float avg = totalSum / validDataCount;
        String unit = getUnit();
        String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
        
        String prompt = "Dữ liệu " + displayTitle + " của tôi:\n" +
                "- Hiện tại: " + String.format(format, avg) + unit + "\n" + 
                "- Cao nhất (7 ngày): " + String.format(format, highestValue) + unit + "\n" +
                "- Thấp nhất (7 ngày): " + String.format(format, lowestValue) + unit + "\n" +
                "Dựa trên các quy tắc chuyên môn, hãy đưa ra phân tích và lời khuyên ngắn gọn.";

        binding.tvAdvice.setText("AI đang phân tích...");
        binding.tvAdviceTime.setText("Khung giờ đang lấy: " + session);

        // Hủy task cũ nếu có trước khi chạy task mới
        if (currentAiTask != null) currentAiTask.cancel(true);
        currentAiTask = new GenerateAdviceTask(session, today, cacheKey, cacheSessionKey, apiKey);
        currentAiTask.execute(prompt);
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
                
                // THÊM CHỈ DẪN HỆ THỐNG (SYSTEM INSTRUCTION) THEO YÊU CẦU NGƯỜI DÙNG
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
            // KIỂM TRA AN TOÀN: Nếu Fragment đã bị đóng hoặc Context không còn, thoát ngay để tránh Crash
            if (binding == null || !isAdded() || getContext() == null) return;

            binding.tvAdvice.setText(result);
            binding.tvAdviceTime.setText("Phân tích lúc: " + session + " (" + today + ")");
            
            // CHỈ LƯU VÀO CACHE NẾU KHÔNG PHẢI LÀ LỖI
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
