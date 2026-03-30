package com.example.loginanimatedapp.ui.dashboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.loginanimatedapp.R;
import com.example.loginanimatedapp.databinding.FragmentDashboardBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment implements OnMapReadyCallback {

    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;
    private MapView mapView;
    private GoogleMap googleMap;
    private Marker userMarker;
    private LatLng currentLocation;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) enableMyLocation();
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle("Bản đồ chi tiết");
            }
        }

        setupClickListeners();
        dashboardViewModel.getDeviceData().observe(getViewLifecycleOwner(), this::updateData);
    }

    private void setupClickListeners() {
        binding.btnShareLocation.setOnClickListener(v -> {
            if (currentLocation != null) {
                String uri = "http://maps.google.com/maps?q=" + currentLocation.latitude + "," + currentLocation.longitude;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Vị trí của thiết bị: " + uri);
                startActivity(Intent.createChooser(shareIntent, "Chia sẻ vị trí"));
            }
        });

        binding.btnFindHospital.setOnClickListener(v -> {
            if (currentLocation != null) {
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14f));
                    searchNearbyHospitals(currentLocation.latitude, currentLocation.longitude);
                }
            } else {
                Toast.makeText(getContext(), "Đang xác định vị trí thiết bị...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchNearbyHospitals(double lat, double lon) {
        Toast.makeText(getContext(), "Đang tìm cơ sở y tế xung quanh...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            try {
                String query = "[out:json];" +
                        "(" +
                        "node[\"amenity\"~\"hospital|clinic\"](around:5000," + lat + "," + lon + ");" +
                        "way[\"amenity\"~\"hospital|clinic\"](around:5000," + lat + "," + lon + ");" +
                        "relation[\"amenity\"~\"hospital|clinic\"](around:5000," + lat + "," + lon + ");" +
                        ");" +
                        "out center;";
                
                URL url = new URL("https://overpass-api.de/api/interpreter?data=" + Uri.encode(query));
                
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                StringBuilder result = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                }
                
                JSONObject json = new JSONObject(result.toString());
                JSONArray elements = json.getJSONArray("elements");

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded() || googleMap == null) return;

                    googleMap.clear();
                    userMarker = null;
                    if (currentLocation != null) {
                        addUserMarker(currentLocation);
                    }

                    if (elements.length() == 0) {
                        Toast.makeText(getContext(), "Không tìm thấy bệnh viện nào trong bán kính 5km", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (int i = 0; i < elements.length(); i++) {
                        JSONObject element = elements.optJSONObject(i);
                        if (element != null) {
                            double hLat = element.optDouble("lat", Double.NaN);
                            double hLon = element.optDouble("lon", Double.NaN);
                            
                            if (Double.isNaN(hLat) || Double.isNaN(hLon)) {
                                JSONObject center = element.optJSONObject("center");
                                if (center != null) {
                                    hLat = center.optDouble("lat", 0.0);
                                    hLon = center.optDouble("lon", 0.0);
                                }
                            }
                            
                            JSONObject tags = element.optJSONObject("tags");
                            String name = tags != null ? tags.optString("name", "Bệnh viện/Phòng khám") : "Bệnh viện/Phòng khám";
                            String amenity = tags != null ? tags.optString("amenity", "hospital") : "hospital";
                            
                            if (hLat != 0.0 && hLon != 0.0) {
                                float markerColor = "hospital".equals(amenity) ? 
                                        BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_ORANGE;
                                        
                                googleMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(hLat, hLon))
                                        .title(name)
                                        .snippet("Loại: " + ("hospital".equals(amenity) ? "Bệnh viện" : "Phòng khám"))
                                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
                            }
                        }
                    }
                    Toast.makeText(getContext(), "Đã tìm thấy " + elements.length() + " cơ sở y tế", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e("MapSearch", "Lỗi tải dữ liệu: ", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Không thể tải dữ liệu bệnh viện", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateData(Map<String, Object> data) {
        if (data == null) return;
        double lat = parseDouble(data.get("GPS_Lat"));
        double lon = parseDouble(data.get("GPS_Lng"));
        if (lat != 0 && lon != 0) {
            currentLocation = new LatLng(lat, lon);
            if (googleMap != null) {
                addUserMarker(currentLocation);
            }
        }
    }

    private void addUserMarker(@NonNull LatLng location) {
        if (googleMap == null) return;
        if (userMarker == null) {
            userMarker = googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Vị trí thiết bị")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        } else {
            userMarker.setPosition(location);
        }
    }

    private double parseDouble(Object obj) {
        if (obj == null) return 0;
        try { return Double.parseDouble(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        try {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_medical));
        } catch (Exception ignored) {}

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        googleMap.setOnMyLocationButtonClickListener(() -> {
            if (currentLocation != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
                return true;
            }
            return false;
        });

        if (currentLocation != null) {
             googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
             addUserMarker(currentLocation);
        }
    }

    private void enableMyLocation() {
        if (googleMap != null && isAdded() && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(true);
        }
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapView != null) mapView.onPause(); }
    @Override public void onDestroy() { 
        super.onDestroy(); 
        if (mapView != null) mapView.onDestroy(); 
        executorService.shutdown();
        binding = null; 
    }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}
