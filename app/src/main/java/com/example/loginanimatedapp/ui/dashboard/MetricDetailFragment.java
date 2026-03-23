package com.example.loginanimatedapp.ui.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
        dashboardViewModel.getDeviceData().observe(getViewLifecycleOwner(), this::updateRealtimeData);
    }

    private void setupUI() {
        String desc = "";
        String unit = getUnit();

        if ("heart_rate".equals(metricType)) {
            desc = "Nhịp tim nghỉ ngơi trung bình của bạn là số nhịp tim mỗi phút khi bạn không hoạt động.";
        } else if ("spo2".equals(metricType)) {
            desc = "Độ bão hòa oxy trong máu (SpO2) đo tỷ lệ phần trăm oxy trong máu.";
        } else if ("temperature".equals(metricType)) {
            desc = "Thân nhiệt là thước đo khả năng tạo ra và loại bỏ nhiệt của cơ thể.";
        } else if ("dust".equals(metricType)) {
            desc = "Chất lượng không khí (PM2.5) đo lường nồng độ bụi mịn.";
        }

        binding.tvMetricTitle.setText(displayTitle);
        binding.tvAboutDesc.setText(desc);
        
        if (getActivity() instanceof AppCompatActivity) {
            if (((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(displayTitle);
            }
        }

        setupHistoryChart(binding.chartHistory);
        setupDistributionChart(binding.chartDistribution);
        
        // Reset thống kê về trạng thái ban đầu
        binding.tvStatHighest.setText("-- " + unit);
        binding.tvStatLowest.setText("-- " + unit);
        binding.tvStatAverage.setText("-- " + unit);
    }

    private String getUnit() {
        if ("heart_rate".equals(metricType)) return "bpm";
        if ("spo2".equals(metricType)) return "%";
        if ("temperature".equals(metricType)) return "°C";
        if ("dust".equals(metricType)) return "µg/m³";
        return "";
    }

    private String[] getFirebaseNodes() {
        if ("heart_rate".equals(metricType)) return new String[]{"BPM", "heart_rate", "nhip_tim"};
        if ("spo2".equals(metricType)) return new String[]{"SpO2", "oxy", "spo2"};
        if ("temperature".equals(metricType)) return new String[]{"TempObj", "temperature", "than_nhiet"};
        if ("dust".equals(metricType)) return new String[]{"Dust", "dust", "bui_min"};
        return new String[]{};
    }

    private void loadHistoryData() {
        String[] possibleNodes = getFirebaseNodes();
        if (possibleNodes.length == 0) return;

        FirebaseDatabase db = FirebaseDatabase.getInstance(AppConstants.DATABASE_URL);
        historyRef = db.getReference().child("History");
        
        // Lắng nghe dữ liệu lịch sử (50 bản ghi mới nhất)
        historyRef.limitToLast(50).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                historyEntries.clear();
                int index = 0;
                int normalCount = 0;
                int warningCount = 0;
                
                highestValue = -1f;
                lowestValue = -1f;
                totalSum = 0f;
                validDataCount = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    float val = -1f;
                    // Quét qua các key có thể có để lấy dữ liệu
                    for (String node : possibleNodes) {
                        Object valObj = data.child(node).getValue();
                        if (valObj != null) {
                            try {
                                val = Float.parseFloat(String.valueOf(valObj));
                                break; 
                            } catch (Exception e) {
                                Log.e("History", "Parse error for " + node);
                            }
                        }
                    }

                    if (val > 0) {
                        historyEntries.add(new Entry(index++, val));
                        
                        if (highestValue == -1 || val > highestValue) highestValue = val;
                        if (lowestValue == -1 || val < lowestValue) lowestValue = val;
                        totalSum += val;
                        validDataCount++;

                        if (isWarning(val)) warningCount++;
                        else normalCount++;
                    }
                }

                updateHistoryChart();
                updateDistributionChartData(normalCount, warningCount);
                updateStatisticsUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "History error: " + error.getMessage());
            }
        });
    }

    private boolean isWarning(float val) {
        if ("heart_rate".equals(metricType)) return val < 60 || val > 100;
        if ("spo2".equals(metricType)) return val < 94;
        if ("temperature".equals(metricType)) return val > 37.5f || val < 36.0f;
        if ("dust".equals(metricType)) return val > 50;
        return false;
    }

    private void updateStatisticsUI() {
        if (validDataCount == 0 || binding == null) return;
        String unit = getUnit();
        boolean isTemp = "temperature".equals(metricType);
        String format = isTemp ? "%.1f" : "%.0f";
        
        binding.tvStatHighest.setText(String.format(format, highestValue) + " " + unit);
        binding.tvStatLowest.setText(String.format(format, lowestValue) + " " + unit);
        binding.tvStatAverage.setText(String.format(format, totalSum / validDataCount) + " " + unit);
    }

    private void setupWaveformChart(LineChart chart) {
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setViewPortOffsets(0, 0, 0, 0);
        chart.getXAxis().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setEnabled(false);
        
        LineDataSet set = new LineDataSet(new ArrayList<>(), "");
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawCircles(false);
        set.setLineWidth(3f);
        set.setColor(Color.parseColor("#4CAF50"));
        set.setDrawValues(false);
        chart.setData(new LineData(set));
    }

    private void setupHistoryChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText("Đang tải dữ liệu lịch sử...");
        chart.setNoDataTextColor(Color.GRAY);
        
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        
        chart.getAxisRight().setEnabled(false);
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
    }

    private void updateHistoryChart() {
        if (historyEntries.isEmpty() || binding == null) return;
        
        LineDataSet dataSet = new LineDataSet(historyEntries, displayTitle);
        dataSet.setColor(Color.parseColor("#2196F3"));
        dataSet.setCircleColor(Color.parseColor("#1976D2"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(1.5f);
        dataSet.setValueTextSize(0f); // Tắt text trên điểm để đỡ rối
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#BBDEFB"));
        dataSet.setFillAlpha(100);
        
        LineData lineData = new LineData(dataSet);
        binding.chartHistory.setData(lineData);
        binding.chartHistory.animateX(1000);
        binding.chartHistory.invalidate();
    }

    private void setupDistributionChart(PieChart chart) {
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.WHITE);
        chart.setTransparentCircleRadius(61f);
        chart.setCenterText("Tình trạng");
        chart.setCenterTextSize(15f);
        chart.setNoDataText("Đang phân tích...");
    }

    private void updateDistributionChartData(int normal, int warning) {
        if (binding == null) return;
        ArrayList<PieEntry> entries = new ArrayList<>();
        if (normal > 0) entries.add(new PieEntry(normal, "Bình thường"));
        if (warning > 0) entries.add(new PieEntry(warning, "Cảnh báo"));

        if (entries.isEmpty()) return;

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(4f);
        dataSet.setSelectionShift(7f);

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#4CAF50")); // Green
        colors.add(Color.parseColor("#F44336")); // Red
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(binding.chartDistribution));
        data.setValueTextSize(12f);
        data.setValueTextColor(Color.WHITE);
        
        binding.chartDistribution.setData(data);
        binding.chartDistribution.invalidate();
    }

    private void updateRealtimeData(Map<String, Object> data) {
        if (data == null || binding == null) return;
        
        String[] possibleNodes = getFirebaseNodes();
        float value = -1f;
        for (String node : possibleNodes) {
            Object rawVal = data.get(node);
            if (rawVal != null) {
                try {
                    value = Float.parseFloat(String.valueOf(rawVal));
                    break;
                } catch (Exception e) {}
            }
        }

        if (value >= 0) {
            try {
                String unit = getUnit();
                boolean isTemp = "temperature".equals(metricType);
                String valDisplay = isTemp ? String.format("%.1f", value) : String.valueOf((int)value);
                
                binding.tvCurrentValue.setText(valDisplay + " " + unit);
                binding.tvStatCurrent.setText(valDisplay + " " + unit);

                Object measureTime = ("dust".equals(metricType) || isTemp) ? data.get("ThoiGian") : data.get("LastMeasureTime");
                binding.tvLastUpdated.setText("Cập nhật lúc: " + (measureTime != null ? String.valueOf(measureTime) : "--"));

                if (value > 0) {
                    LineData lineData = binding.chartRealtimeWaveform.getData();
                    if (lineData != null) {
                        LineDataSet set = (LineDataSet) lineData.getDataSetByIndex(0);
                        lineData.addEntry(new Entry(graphXIndex++, value), 0);
                        if (set.getEntryCount() > 30) set.removeEntry(0);
                        lineData.notifyDataChanged();
                        binding.chartRealtimeWaveform.notifyDataSetChanged();
                        binding.chartRealtimeWaveform.setVisibleXRangeMaximum(30);
                        binding.chartRealtimeWaveform.moveViewToX(graphXIndex);
                    }
                }
            } catch (Exception e) {}
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
