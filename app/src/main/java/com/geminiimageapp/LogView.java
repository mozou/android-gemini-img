package com.geminiimageapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 自定义日志显示视图
 */
public class LogView extends FrameLayout implements LogManager.LogListener {
    
    private RecyclerView rvLogs;
    private Button btnClearLogs;
    private LogAdapter adapter;
    private LogManager logManager;
    
    public LogView(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    public LogView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public LogView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.log_view, this, true);
        
        // 初始化视图
        rvLogs = findViewById(R.id.rv_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
        
        // 设置RecyclerView
        rvLogs.setLayoutManager(new LinearLayoutManager(context));
        adapter = new LogAdapter();
        rvLogs.setAdapter(adapter);
        
        // 设置清除按钮点击事件
        btnClearLogs.setOnClickListener(v -> {
            logManager.clearLogs();
        });
        
        // 获取日志管理器实例
        logManager = LogManager.getInstance();
        logManager.addListener(this);
        
        // 加载现有日志
        adapter.setLogs(logManager.getLogs());
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 移除监听器
        if (logManager != null) {
            logManager.removeListener(this);
        }
    }
    
    @Override
    public void onNewLog(LogManager.LogEntry entry) {
        // 在UI线程上更新
        post(() -> {
            adapter.addLog(entry);
            rvLogs.smoothScrollToPosition(adapter.getItemCount() - 1);
        });
    }
    
    @Override
    public void onClearLogs() {
        // 在UI线程上更新
        post(() -> adapter.clearLogs());
    }
}