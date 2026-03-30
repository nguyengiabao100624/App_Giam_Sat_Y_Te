package com.example.loginanimatedapp.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.loginanimatedapp.databinding.FragmentDetailMetricBinding;
import com.example.loginanimatedapp.utils.AppConstants;
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

import java.util.ArrayList;
import java.util.List;
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

        FirebaseDatabase db = FirebaseDatabase.getInstance(AppConstants.DATABASE_URL);
        historyRef = deviceId.isEmpty() ? db.getReference("Histories") : db.getReference("Histories").child(deviceId);
        
        historyRef.limitToLast(100).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                historyEntries.clear();
                validDataCount = 0; totalSum = 0;
                highestValue = -1f; lowestValue = -1f;
                int normalCount = 0; int warningCount = 0; int criticalCount = 0;

                int index = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    // LỌC DỮ LIỆU: Chỉ lấy khi thiết bị báo "Do lien tuc" cho Nhịp tim và SpO2
                    if ("heart_rate".equals(metricType) || "spo2".equals(metricType)) {
                        Object statusObj = child.child("TrangThaiDo").getValue();
                        String status = String.valueOf(statusObj);
                        if (!"Do lien tuc".equalsIgnoreCase(status)) {
                            continue; // Bỏ qua nếu đang chờ đo hoặc đang trong tiến trình đo chưa xong
                        }
                    }

                    Float val = findValueRecursive(child, possibleNodes, 0);
                    if (val != null && val >= 0) {
                        historyEntries.add(new Entry(index++, val));
                        updateStats(val);
                        
                        int status = getStatusLevel(val);
                        if (status == 0) normalCount++;
                        else if (status == 1) warningCount++;
                        else criticalCount++;
                    }
                }
                updateHistoryChart();
                updateDistributionChartData(normalCount, warningCount, criticalCount);
                updateStatisticsUI();
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

    private void updateStats(float val) {
        if (highestValue == -1 || val > highestValue) highestValue = val;
        if (lowestValue == -1 || val < lowestValue) lowestValue = val;
        totalSum += val;
        validDataCount++;
    }

    private void updateStatisticsUI() {
        if (validDataCount == 0 || binding == null) return;
        String unit = getUnit();
        String format = "temperature".equals(metricType) ? "%.1f" : "%.0f";
        binding.tvStatHighest.setText(String.format(format, highestValue) + " " + unit);
        binding.tvStatLowest.setText(String.format(format, lowestValue) + " " + unit);
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
    }

    private void updateHistoryChart() {
        if (binding == null || historyEntries.isEmpty()) return;
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

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
