package com.cameradetector.app;

import android.os.Parcel;
import android.os.Parcelable;

public class CameraInfo implements Parcelable {
    
    private String username;
    private String password;
    private String streamPath;
    
    public enum CameraType {
        LOCAL,      // 本地摄像头
        NETWORK,    // 网络摄像头
        BLUETOOTH   // 蓝牙摄像头
    }
    
    private String id;
    private String name;
    private CameraType type;
    private String ipAddress;
    private int port;
    private boolean accessible;
    private boolean hasPermission;
    private String manufacturer;
    private String model;
    private String description;
    
    public CameraInfo() {
        this.accessible = false;
        this.hasPermission = false;
    }
    
    protected CameraInfo(Parcel in) {
        id = in.readString();
        name = in.readString();
        type = CameraType.valueOf(in.readString());
        ipAddress = in.readString();
        port = in.readInt();
        accessible = in.readByte() != 0;
        hasPermission = in.readByte() != 0;
        manufacturer = in.readString();
        model = in.readString();
        description = in.readString();
        username = in.readString();
        password = in.readString();
        streamPath = in.readString();
    }
    
    public static final Creator<CameraInfo> CREATOR = new Creator<CameraInfo>() {
        @Override
        public CameraInfo createFromParcel(Parcel in) {
            return new CameraInfo(in);
        }
        
        @Override
        public CameraInfo[] newArray(int size) {
            return new CameraInfo[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(type.name());
        dest.writeString(ipAddress);
        dest.writeInt(port);
        dest.writeByte((byte) (accessible ? 1 : 0));
        dest.writeByte((byte) (hasPermission ? 1 : 0));
        dest.writeString(manufacturer);
        dest.writeString(model);
        dest.writeString(description);
        dest.writeString(username);
        dest.writeString(password);
        dest.writeString(streamPath);
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public CameraType getType() { return type; }
    public void setType(CameraType type) { this.type = type; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public boolean isAccessible() { return accessible; }
    public void setAccessible(boolean accessible) { this.accessible = accessible; }
    
    public boolean hasPermission() { return hasPermission; }
    public void setHasPermission(boolean hasPermission) { this.hasPermission = hasPermission; }
    
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getStreamPath() { return streamPath; }
    public void setStreamPath(String streamPath) { this.streamPath = streamPath; }
    
    // 获取完整的流URL
    public String getFullStreamUrl() {
        if (streamPath != null && !streamPath.isEmpty()) {
            if (streamPath.startsWith("rtsp://") || streamPath.startsWith("http://")) {
                return streamPath;
            } else if (ipAddress != null && !ipAddress.isEmpty() && port > 0) {
                return "rtsp://" + ipAddress + ":" + port + streamPath;
            }
        }
        
        // 尝试构建默认的RTSP URL
        if (ipAddress != null && !ipAddress.isEmpty() && port > 0) {
            return "rtsp://" + ipAddress + ":" + port + "/";
        }
        
        return null;
    }
    
    // 获取MAC地址
    private String macAddress;
    
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    
    public String getTypeString() {
        switch (type) {
            case LOCAL:
                return "本地摄像头";
            case NETWORK:
                return "网络摄像头";
            case BLUETOOTH:
                return "蓝牙摄像头";
            default:
                return "未知类型";
        }
    }
    
    public String getStatusString() {
        if (accessible && hasPermission) {
            return "可控制";
        } else if (accessible) {
            return "可访问";
        } else {
            return "不可访问";
        }
    }
}