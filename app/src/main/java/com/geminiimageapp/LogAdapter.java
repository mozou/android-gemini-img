package com.geminiimageapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志适配器，用于在RecyclerView中显示日志
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
    
    private final List<LogManager.LogEntry> logs = new ArrayList<>();
    
    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogManager.LogEntry log = logs.get(position);
        holder.bind(log);
    }
    
    @Override
    public int getItemCount() {
        return logs.size();
    }
    
    /**
     * 添加新的日志条目
     */
    public void addLog(LogManager.LogEntry log) {
        logs.add(log);
        notifyItemInserted(logs.size() - 1);
    }
    
    /**
     * 清除所有日志
     */
    public void clearLogs() {
        int size = logs.size();
        logs.clear();
        notifyItemRangeRemoved(0, size);
    }
    
    /**
     * 设置日志列表
     */
    public void setLogs(List<LogManager.LogEntry> logs) {
        this.logs.clear();
        this.logs.addAll(logs);
        notifyDataSetChanged();
    }
    
    /**
     * 日志ViewHolder
     */
    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvLog;
        
        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLog = itemView.findViewById(R.id.tv_log);
        }
        
        public void bind(LogManager.LogEntry log) {
            // 格式化日志文本
            String logText = log.getFormattedTime() + " " + log.getTag() + ": " + log.getMessage();
            tvLog.setText(logText);
            
            // 根据日志级别设置不同的颜色
            switch (log.getLevel()) {
                case LogManager.LOG_INFO:
                    tvLog.setTextColor(Color.BLACK);
                    break;
                case LogManager.LOG_DEBUG:
                    tvLog.setTextColor(Color.parseColor("#0066CC"));
                    break;
                case LogManager.LOG_WARNING:
                    tvLog.setTextColor(Color.parseColor("#FF9900"));
                    break;
                case LogManager.LOG_ERROR:
                    tvLog.setTextColor(Color.RED);
                    break;
            }
        }
    }
}