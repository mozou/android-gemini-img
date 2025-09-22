package com.cameradetector.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraDetector.OnCameraDetectedListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private CameraDetector cameraDetector;
    private List<CameraInfo> detectedCameras = new ArrayList<>();
    private CameraListAdapter cameraListAdapter;
    
    private Button btnScanCameras;
    private Button btnControlCameras;
    private ProgressBar progressBar;
    private TextView tvCameraCount;
    private TextView tvScanStatus;
    private TextView tvScanProgress;
    private ListView lvCameraList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupListeners();
        
        cameraDetector = new CameraDetector(this);
    }
    
    private void initViews() {
        btnScanCameras = findViewById(R.id.btn_scan_cameras);
        btnControlCameras = findViewById(R.id.btn_control_cameras);
        progressBar = findViewById(R.id.progress_bar);
        tvCameraCount = findViewById(R.id.tv_camera_count);
        tvScanStatus = findViewById(R.id.tv_scan_status);
        tvScanProgress = findViewById(R.id.tv_scan_progress);
        lvCameraList = findViewById(R.id.lv_camera_list);
        
        cameraListAdapter = new CameraListAdapter(this, detectedCameras);
        lvCameraList.setAdapter(cameraListAdapter);
        
        // 初始状态
        btnControlCameras.setEnabled(false);
        progressBar.setVisibility(View.GONE);
        tvScanStatus.setVisibility(View.GONE);
        tvScanProgress.setVisibility(View.GONE);
    }
    
    private void setupListeners() {
        btnScanCameras.setOnClickListener(v -> {
            if (checkPermissions()) {
                startScan();
            } else {
                requestPermissions();
            }
        });
        
        btnControlCameras.setOnClickListener(v -> {
            if (!detectedCameras.isEmpty()) {
                openCameraControl();
            }
        });
        
        lvCameraList.setOnItemClickListener((parent, view, position, id) -> {
            CameraInfo selectedCamera = detectedCameras.get(position);
            openCameraDetail(selectedCamera);
        });
    }
    
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "需要所有权限才能扫描摄像头", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void startScan() {
        detectedCameras.clear();
        cameraListAdapter.notifyDataSetChanged();
        
        btnScanCameras.setEnabled(false);
        btnControlCameras.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setVisibility(View.VISIBLE);
        tvScanProgress.setVisibility(View.VISIBLE);
        tvScanStatus.setText("正在扫描...");
        tvScanProgress.setText("");
        tvCameraCount.setText("检测到 0 个摄像头设备");
        
        cameraDetector.startComprehensiveScan(this);
    }
    
    private void openCameraControl() {
        Intent intent = new Intent(this, CameraControlActivity.class);
        intent.putParcelableArrayListExtra("cameras", new ArrayList<>(detectedCameras));
        startActivity(intent);
    }
    
    private void openCameraDetail(CameraInfo camera) {
        Intent intent = new Intent(this, CameraControlActivity.class);
        intent.putExtra("camera_info", camera);
        startActivity(intent);
    }
    
    @Override
    public void onCameraDetected(CameraInfo cameraInfo) {
        detectedCameras.add(cameraInfo);
        cameraListAdapter.notifyDataSetChanged();
        tvCameraCount.setText("检测到 " + detectedCameras.size() + " 个摄像头设备");
    }
    
    @Override
    public void onScanComplete() {
        btnScanCameras.setEnabled(true);
        btnControlCameras.setEnabled(!detectedCameras.isEmpty());
        progressBar.setVisibility(View.GONE);
        
        if (detectedCameras.isEmpty()) {
            tvScanStatus.setText("未检测到摄像头");
        } else {
            tvScanStatus.setText("扫描完成，发现 " + detectedCameras.size() + " 个摄像头");
        }
    }
    
    @Override
    public void onScanProgress(String status) {
        tvScanStatus.setText(status);
    }
    
    @Override
    public void onProgressUpdate(int current, int total, String currentTask) {
        progressBar.setMax(total);
        progressBar.setProgress(current);
        tvScanProgress.setText(currentTask + " (" + current + "/" + total + ")");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraDetector != null) {
            cameraDetector.destroy();
        }
    }
}