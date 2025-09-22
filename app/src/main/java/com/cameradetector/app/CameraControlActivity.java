package com.cameradetector.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class CameraControlActivity extends AppCompatActivity {
    
    private TextView tvCameraInfo;
    private WebView webViewCamera;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnConnect;
    private Button btnPanLeft;
    private Button btnPanRight;
    private Button btnTiltUp;
    private Button btnTiltDown;
    private Button btnZoomIn;
    private Button btnZoomOut;
    private Button btnSnapshot;
    private LinearLayout controlPanel;
    private ProgressBar progressLoading;
    
    private CameraInfo selectedCamera;
    private CameraController cameraController;
    private String streamUrl;
    private boolean controlMode;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_control);
        
        // 获取传递的摄像头信息
        selectedCamera = getIntent().getParcelableExtra("camera_info");
        streamUrl = getIntent().getStringExtra("stream_url");
        controlMode = getIntent().getBooleanExtra("control_mode", false);
        
        initViews();
        
        if (selectedCamera != null) {
            setupCamera(streamUrl, controlMode);
        } else {
            Toast.makeText(this, "未选择摄像头", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initViews() {
        tvCameraInfo = findViewById(R.id.tv_camera_info);
        webViewCamera = findViewById(R.id.webview_camera);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnConnect = findViewById(R.id.btn_connect);
        btnPanLeft = findViewById(R.id.btn_pan_left);
        btnPanRight = findViewById(R.id.btn_pan_right);
        btnTiltUp = findViewById(R.id.btn_tilt_up);
        btnTiltDown = findViewById(R.id.btn_tilt_down);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnSnapshot = findViewById(R.id.btn_snapshot);
        controlPanel = findViewById(R.id.layout_control_panel);
        progressLoading = findViewById(R.id.progress_loading);
        
        // 设置WebView
        WebSettings webSettings = webViewCamera.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webViewCamera.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressLoading.setVisibility(View.GONE);
            }
        });
        
        // 设置按钮点击事件
        btnConnect.setOnClickListener(v -> connectToCamera());
        
        btnPanLeft.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "ptz_left");
            }
        });
        
        btnPanRight.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "ptz_right");
            }
        });
        
        btnTiltUp.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "ptz_up");
            }
        });
        
        btnTiltDown.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "ptz_down");
            }
        });
        
        btnZoomIn.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "zoom_in");
            }
        });
        
        btnZoomOut.setOnClickListener(v -> {
            if (cameraController != null) {
                cameraController.sendCameraCommand(selectedCamera, "zoom_out");
            }
        });
        
        btnSnapshot.setOnClickListener(v -> {
            if (cameraController != null) {
                takeSnapshot();
            }
        });
    }
    
    private void setupCamera(String streamUrl, boolean controlMode) {
        // 显示摄像头信息
        String cameraInfo = selectedCamera.getName() + " (" + selectedCamera.getTypeString() + ")";
        if (selectedCamera.getType() == CameraInfo.CameraType.NETWORK && selectedCamera.getIpAddress() != null) {
            cameraInfo += "\nIP: " + selectedCamera.getIpAddress();
            if (selectedCamera.getPort() > 0) {
                cameraInfo += ":" + selectedCamera.getPort();
            }
        }
        tvCameraInfo.setText(cameraInfo);
        
        // 初始化控制器
        cameraController = new CameraController(this);
        
        // 设置控制面板可见性
        controlPanel.setVisibility(controlMode ? View.VISIBLE : View.GONE);
        
        // 如果提供了流URL，直接尝试连接
        if (streamUrl != null && !streamUrl.isEmpty()) {
            loadCameraStream(streamUrl);
        } 
        // 如果摄像头已经有权限，尝试自动连接
        else if (selectedCamera.hasPermission()) {
            connectToCamera();
        }
    }
    
    private void connectToCamera() {
        final String username = etUsername.getText().toString();
        final String password = etPassword.getText().toString();
        
        // 如果用户名密码为空，使用默认值
        final String finalUsername = username.isEmpty() ? "admin" : username;
        final String finalPassword = password.isEmpty() ? "admin" : password;
        
        progressLoading.setVisibility(View.VISIBLE);
        
        if (selectedCamera.getType() == CameraInfo.CameraType.NETWORK) {
            // 尝试连接网络摄像头
            new Thread(() -> {
                // 设置凭据
                selectedCamera.setUsername(finalUsername);
                selectedCamera.setPassword(finalPassword);
                
                // 尝试获取流地址
                String detectedStreamUrl = detectStreamUrl(selectedCamera);
                
                runOnUiThread(() -> {
                    if (detectedStreamUrl != null) {
                        loadCameraStream(detectedStreamUrl);
                    } else {
                        progressLoading.setVisibility(View.GONE);
                        Toast.makeText(this, "无法获取摄像头流地址，请检查凭据或网络连接", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        } else {
            progressLoading.setVisibility(View.GONE);
            Toast.makeText(this, "不支持的摄像头类型", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String detectStreamUrl(CameraInfo camera) {
        // 如果已经有流URL，直接返回
        if (streamUrl != null && !streamUrl.isEmpty()) {
            return streamUrl;
        }
        
        // 尝试常见的流路径
        String[] commonStreamPaths = {
            "/video.mjpg",
            "/mjpg/video.mjpg",
            "/cgi-bin/mjpg/video.cgi",
            "/videostream.cgi",
            "/video/mjpg.cgi",
            "/cgi-bin/camera",
            "/snapshot.cgi",
            "/image.jpg",
            "/video.cgi",
            "/live.html",
            "/live/index.html",
            "/live/view.html",
            "/view/view.shtml",
            "/index.html"
        };
        
        String ipAddress = camera.getIpAddress();
        int port = camera.getPort();
        
        // 尝试RTSP流
        if (port == 554 || port == 8554 || port == 10554) {
            String[] rtspPaths = {
                "/live/main",
                "/live/ch1/main",
                "/live/ch01/main",
                "/cam/realmonitor?channel=1&subtype=0",
                "/h264/ch1/main/av_stream",
                "/streaming/channels/1",
                "/onvif1",
                "/media/video1",
                "/videoMain"
            };
            
            for (String path : rtspPaths) {
                String url = "rtsp://" + ipAddress + ":" + port + path;
                // 注意：RTSP流无法在WebView中直接播放，需要使用专用播放器
                // 这里只是检测URL是否有效
                return url;  // 返回第一个RTSP URL，实际应用中应该检测有效性
            }
        }
        
        // 尝试HTTP流
        for (String path : commonStreamPaths) {
            String url = "http://" + ipAddress + ":" + port + path;
            try {
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                
                // 如果有凭据，添加认证头
                if (camera.getUsername() != null && !camera.getUsername().isEmpty()) {
                    String auth = camera.getUsername() + ":" + camera.getPassword();
                    String encodedAuth = android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
                    connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                }
                
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                
                int responseCode = connection.getResponseCode();
                String contentType = connection.getContentType();
                
                if (responseCode == 200) {
                    if (contentType != null && (
                        contentType.startsWith("image/") || 
                        contentType.startsWith("video/") || 
                        contentType.startsWith("multipart/") ||
                        contentType.contains("mjpeg") ||
                        contentType.contains("html"))) {
                        return url;
                    }
                }
            } catch (Exception e) {
                // 继续尝试下一个路径
            }
        }
        
        // 如果所有尝试都失败，返回null
        return null;
    }
    
    private void loadCameraStream(String url) {
        if (url.startsWith("rtsp://")) {
            // RTSP流需要特殊处理
            showRtspStreamDialog(url);
        } else {
            // HTTP流可以直接在WebView中加载
            webViewCamera.loadUrl(url);
            progressLoading.setVisibility(View.GONE);
        }
    }
    
    private void showRtspStreamDialog(String rtspUrl) {
        progressLoading.setVisibility(View.GONE);
        
        new AlertDialog.Builder(this)
            .setTitle("RTSP流")
            .setMessage("检测到RTSP流地址：\n" + rtspUrl + "\n\nRTSP流需要使用专用播放器播放。")
            .setPositiveButton("使用外部播放器", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(android.net.Uri.parse(rtspUrl), "video/*");
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "未找到支持RTSP的播放器应用", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void takeSnapshot() {
        if (selectedCamera == null || !selectedCamera.isAccessible()) {
            Toast.makeText(this, "无法访问摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "正在获取快照...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            boolean success = cameraController.sendCameraCommand(selectedCamera, "snapshot");
            
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "快照已保存", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "获取快照失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}