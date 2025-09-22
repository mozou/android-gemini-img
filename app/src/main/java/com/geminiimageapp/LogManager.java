package com.geminiimageapp;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志管理器，用于收集和管理应用内的日志
 */
public class LogManager {
    private static final String TAG = "GeminiImageGen";
    
    // 单例实例
    private static LogManager instance;
    
    // 日志条目列表，使用线程安全的集合
    private final CopyOnWriteArrayList<LogEntry> logs = new CopyOnWriteArrayList<>();
    
    // 日志监听器列表
    private final List<LogListener> listeners = new ArrayList<>();
    
    // 日志类型
    public static final int LOG_INFO = 0;
    public static final int LOG_DEBUG = 1;
    public static final int LOG_WARNING = 2;
    public static final int LOG_ERROR = 3;
    
    // 日志标记前缀
    public static final String LOG_INIT = "【初始化】";
    public static final String LOG_PARAMS = "【参数处理】";
    public static final String LOG_API = "【API调用】";
    public static final String LOG_PROCESS = "【响应处理】";
    public static final String LOG_IMAGE = "【图片处理】";
    public static final String LOG_ERROR_TAG = "【错误处理】";
    
    // 最大日志条数
    private static final int MAX_LOG_ENTRIES = 1000;
    
    private LogManager() {
        // 私有构造函数
    }
    
    /**
     * 获取LogManager实例
     */
    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }
    
    /**
     * 添加日志监听器
     */
    public void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除日志监听器
     */
    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 添加信息日志
     */
    public void i(String tag, String message) {
        addLog(LOG_INFO, tag, message);
        Log.i(TAG, message);
    }
    
    /**
     * 添加调试日志
     */
    public void d(String tag, String message) {
        addLog(LOG_DEBUG, tag, message);
        Log.d(TAG, message);
    }
    
    /**
     * 添加警告日志
     */
    public void w(String tag, String message) {
        addLog(LOG_WARNING, tag, message);
        Log.w(TAG, message);
    }
    
    /**
     * 添加错误日志
     */
    public void e(String tag, String message, Throwable throwable) {
        addLog(LOG_ERROR, tag, message + (throwable != null ? ": " + throwable.getMessage() : ""));
        Log.e(TAG, message, throwable);
    }
    
    /**
     * 添加错误日志（无异常）
     */
    public void e(String tag, String message) {
        e(tag, message, null);
    }
    
    /**
     * 添加日志条目
     */
    private void addLog(int level, String tag, String message) {
        // 创建日志条目
        LogEntry entry = new LogEntry(level, tag, message);
        
        // 添加到日志列表
        logs.add(entry);
        
        // 如果日志数量超过最大值，移除最旧的日志
        if (logs.size() > MAX_LOG_ENTRIES) {
            logs.remove(0);
        }
        
        // 通知所有监听器
        notifyListeners(entry);
    }
    
    /**
     * 通知所有监听器
     */
    private void notifyListeners(LogEntry entry) {
        for (LogListener listener : listeners) {
            listener.onNewLog(entry);
        }
    }
    
    /**
     * 获取所有日志
     */
    public List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }
    
    /**
     * 清除所有日志
     */
    public void clearLogs() {
        logs.clear();
        for (LogListener listener : listeners) {
            listener.onClearLogs();
        }
    }
    
    /**
     * 日志条目类
     */
    public static class LogEntry {
        private final int level;
        private final String tag;
        private final String message;
        private final long timestamp;
        
        public LogEntry(int level, String tag, String message) {
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getTag() {
            return tag;
        }
        
        public String getMessage() {
            return message;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
        
        @Override
        public String toString() {
            return getFormattedTime() + " " + tag + ": " + message;
        }
    }
    
    /**
     * 日志监听器接口
     */
    public interface LogListener {
        void onNewLog(LogEntry entry);
        void onClearLogs();
    }
}