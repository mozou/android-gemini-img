package com.cameradetector.app;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraDetector {
    private static final String TAG = "CameraDetector";
    private Context context;
    private OnCameraDetectedListener listener;
    private ExecutorService executorService;
    private boolean isScanning = false;
    private Handler mainHandler;
    private AtomicInteger scannedIps = new AtomicInteger(0);
    private int totalIpsToScan = 0;
    
    // 常见的摄像头端口
    private static final int[] COMMON_CAMERA_PORTS = {
            80, 81, 82, 83, 88, 
            554, 555, 8000, 8080, 8081, 
            8082, 8083, 8084, 8085, 8086, 
            8554, 8555, 9000, 9001, 9002, 
            10554, 37777, 37778, 49152
    };
    
    // 常见的摄像头URL路径
    private static final String[] COMMON_CAMERA_PATHS = {
            "/", "/index.html", "/view.html", "/viewer/live.html", "/live.html", 
            "/live/index.html", "/video.cgi", "/mjpg/video.mjpg", 
            "/cgi-bin/viewer/video.jpg", "/snapshot.cgi", "/axis-cgi/mjpg/video.cgi", 
            "/control/faststream.jpg", "/videostream.cgi", "/GetData.cgi", 
            "/live/av0", "/cam/realmonitor", "/webcam.jpg", "/camera.cgi",
            "/video/mjpg.cgi", "/cgi-bin/camera", "/image.jpg", "/video.mjpg",
            "/cgi-bin/mjpg/video.cgi", "/live/main", "/live/ch1/main"
    };
    
    // 常见的摄像头制造商特征
    private static final Map<String, String> CAMERA_MANUFACTURERS = new HashMap<String, String>() {{
        put("hikvision", "海康威视");
        put("dahua", "大华");
        put("axis", "安讯士");
        put("sony", "索尼");
        put("panasonic", "松下");
        put("samsung", "三星");
        put("bosch", "博世");
        put("vivotek", "威联通");
        put("tplink", "TP-Link");
        put("dlink", "D-Link");
        put("foscam", "福斯康姆");
        put("wanscam", "万视达");
        put("uniview", "宇视");
        put("tiandy", "天地伟业");
        put("kedacom", "科达");
        put("yushi", "宇视");
        put("infinova", "英飞拓");
    }};

    public interface OnCameraDetectedListener {
        void onCameraDetected(CameraInfo cameraInfo);
        void onScanComplete();
        void onScanProgress(String status);
        void onProgressUpdate(int current, int total, String currentTask);
    }

    public CameraDetector(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(20);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void startComprehensiveScan(OnCameraDetectedListener listener) {
        if (isScanning) {
            return;
        }
        
        this.listener = listener;
        isScanning = true;
        scannedIps.set(0);
        
        // 开始扫描网络摄像头
        detectNetworkCameras();
    }
    
    public void stopScan() {
        isScanning = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = Executors.newFixedThreadPool(20);
        }
    }
    
    public void destroy() {
        stopScan();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void detectNetworkCameras() {
        updateProgress("开始扫描局域网摄像头...", 0, 100);
        
        // 获取当前WiFi网络的IP地址
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        
        // 转换IP地址格式
        String myIp = String.format(
                "%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
        
        // 提取网络前缀
        String prefix = myIp.substring(0, myIp.lastIndexOf(".") + 1);
        
        // 计算总扫描IP数量
        totalIpsToScan = 254;
        
        // 扫描局域网中的所有IP
        for (int i = 1; i <= 254; i++) {
            if (!isScanning) {
                break;
            }
            
            final String targetIp = prefix + i;
            final int ipIndex = i;
            
            executorService.submit(() -> {
                if (isReachable(targetIp)) {
                    scanPorts(targetIp);
                }
                
                int scanned = scannedIps.incrementAndGet();
                updateProgress("扫描IP: " + targetIp, scanned, totalIpsToScan);
                
                if (scanned >= totalIpsToScan) {
                    scanComplete();
                }
            });
        }
    }
    
    private boolean isReachable(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            return address.isReachable(500);  // 500ms超时
        } catch (UnknownHostException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }
    
    private void scanPorts(String ipAddress) {
        for (int port : COMMON_CAMERA_PORTS) {
            if (!isScanning) {
                return;
            }
            
            if (isPortOpen(ipAddress, port)) {
                for (String path : COMMON_CAMERA_PATHS) {
                    if (!isScanning) {
                        return;
                    }
                    
                    if (isCameraUrl(ipAddress, port, path)) {
                        CameraInfo camera = createCameraInfo(ipAddress, port, path);
                        if (listener != null) {
                            mainHandler.post(() -> listener.onCameraDetected(camera));
                        }
                        break;  // 找到一个有效路径后就不再继续检查其他路径
                    }
                }
            }
        }
    }
    
    private boolean isPortOpen(String ipAddress, int port) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(ipAddress, port), 300);  // 300ms超时
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private boolean isCameraUrl(String ipAddress, int port, String path) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + ipAddress + ":" + port + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(500);
            connection.setReadTimeout(500);
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                String contentType = connection.getContentType();
                if (contentType != null && 
                    (contentType.startsWith("image/") || 
                     contentType.startsWith("video/") || 
                     contentType.startsWith("multipart/x-mixed-replace") ||
                     contentType.contains("mjpeg"))) {
                    return true;
                }
                
                // 检查HTML内容是否包含视频相关关键词
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                int linesRead = 0;
                while ((line = reader.readLine()) != null && linesRead < 20) {  // 只读取前20行
                    response.append(line.toLowerCase());
                    linesRead++;
                }
                reader.close();
                
                String html = response.toString();
                return html.contains("camera") || html.contains("video") || 
                       html.contains("stream") || html.contains("mjpeg") ||
                       html.contains("rtsp") || html.contains("surveillance") ||
                       html.contains("摄像") || html.contains("监控") ||
                       html.contains("视频") || html.contains("直播");
            }
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private CameraInfo createCameraInfo(String ipAddress, int port, String path) {
        CameraInfo camera = new CameraInfo();
        camera.setType(CameraInfo.CameraType.NETWORK);
        camera.setIpAddress(ipAddress);
        camera.setPort(port);
        camera.setStreamPath(path);
        camera.setAccessible(true);
        camera.setId(ipAddress + ":" + port);
        
        // 设置名称
        String name = "网络摄像头 (" + ipAddress + ":" + port + ")";
        camera.setName(name);
        
        // 尝试检测摄像头制造商
        detectCameraManufacturer(camera);
        
        return camera;
    }
    
    private void detectCameraManufacturer(CameraInfo camera) {
        String ipAddress = camera.getIpAddress();
        int port = camera.getPort();
        
        try {
            URL url = new URL("http://" + ipAddress + ":" + port + "/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(500);
            connection.setReadTimeout(500);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                
                // 检查服务器头信息
                String server = connection.getHeaderField("Server");
                if (server != null) {
                    server = server.toLowerCase();
                    for (Map.Entry<String, String> entry : CAMERA_MANUFACTURERS.entrySet()) {
                        if (server.contains(entry.getKey())) {
                            camera.setManufacturer(entry.getValue());
                            camera.setName(entry.getValue() + " 摄像头");
                            break;
                        }
                    }
                }
                
                // 如果没有从服务器头信息中检测到制造商，尝试从HTML内容中检测
                if (camera.getManufacturer() == null && responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    int linesRead = 0;
                    while ((line = reader.readLine()) != null && linesRead < 50) {  // 只读取前50行
                        response.append(line.toLowerCase());
                        linesRead++;
                    }
                    reader.close();
                    
                    String html = response.toString();
                    for (Map.Entry<String, String> entry : CAMERA_MANUFACTURERS.entrySet()) {
                        if (html.contains(entry.getKey())) {
                            camera.setManufacturer(entry.getValue());
                            camera.setName(entry.getValue() + " 摄像头");
                            break;
                        }
                    }
                }
            }
            
            connection.disconnect();
            
        } catch (IOException e) {
            // 忽略异常
        }
    }
    
    private void updateProgress(String status, int current, int total) {
        if (listener != null) {
            mainHandler.post(() -> {
                listener.onScanProgress(status);
                listener.onProgressUpdate(current, total, status);
            });
        }
    }
    
    private void scanComplete() {
        if (isScanning) {
            isScanning = false;
            if (listener != null) {
                mainHandler.post(() -> listener.onScanComplete());
            }
        }
    }
}