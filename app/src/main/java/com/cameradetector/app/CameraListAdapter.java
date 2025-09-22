package com.cameradetector.app;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class CameraListAdapter extends BaseAdapter {
    
    private Context context;
    private List<CameraInfo> cameraList;
    private CameraExploiter cameraExploiter;
    
    public CameraListAdapter(Context context, List<CameraInfo> cameraList) {
        this.context = context;
        this.cameraList = cameraList;
        this.cameraExploiter = new CameraExploiter();
    }
    
    @Override
    public int getCount() {
        return cameraList.size();
    }
    
    @Override
    public Object getItem(int position) {
        return cameraList.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        LayoutInflater inflater = LayoutInflater.from(context);
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_camera, parent, false);
            holder = new ViewHolder();
            holder.ivCameraIcon = convertView.findViewById(R.id.iv_camera_icon);
            holder.tvCameraName = convertView.findViewById(R.id.tv_camera_name);
            holder.tvCameraType = convertView.findViewById(R.id.tv_camera_type);
            holder.tvCameraStatus = convertView.findViewById(R.id.tv_camera_status);
            holder.tvCameraId = convertView.findViewById(R.id.tv_camera_id);
            holder.btnExploit = convertView.findViewById(R.id.btn_exploit);
            holder.btnView = convertView.findViewById(R.id.btn_view);
            holder.btnControl = convertView.findViewById(R.id.btn_control);
            holder.layoutCameraDetails = convertView.findViewById(R.id.layout_camera_details);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        CameraInfo camera = cameraList.get(position);
        
        // 设置摄像头图标
        switch (camera.getType()) {
            case LOCAL:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                break;
            case NETWORK:
                // 根据制造商设置不同的图标
                if (camera.getManufacturer() != null) {
                    String manufacturer = camera.getManufacturer().toLowerCase();
                    if (manufacturer.contains("hikvision") || manufacturer.contains("海康")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_hikvision);
                    } else if (manufacturer.contains("dahua") || manufacturer.contains("大华")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_dahua);
                    } else if (manufacturer.contains("axis")) {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_axis);
                    } else {
                        holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                    }
                } else {
                    holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_network);
                }
                break;
            case BLUETOOTH:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_bluetooth);
                break;
            default:
                holder.ivCameraIcon.setImageResource(R.drawable.ic_camera_unknown);
                break;
        }
        
        // 设置摄像头信息
        holder.tvCameraName.setText(camera.getName());
        holder.tvCameraType.setText(camera.getTypeString());
        holder.tvCameraStatus.setText(camera.getStatusString());
        holder.tvCameraId.setText("ID: " + camera.getId());
        
        // 根据状态设置文字颜色
        if (camera.isAccessible() && camera.hasPermission()) {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else if (camera.isAccessible()) {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.tvCameraStatus.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
        }
        
        // 设置漏洞利用按钮
        if (camera.getType() == CameraInfo.CameraType.NETWORK) {
            holder.btnExploit.setVisibility(View.VISIBLE);
            holder.btnExploit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exploitCamera(camera);
                }
            });
            
            // 设置查看按钮
            holder.btnView.setVisibility(View.VISIBLE);
            holder.btnView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewCameraStream(camera);
                }
            });
            
            // 设置控制按钮
            holder.btnControl.setVisibility(View.VISIBLE);
            holder.btnControl.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    controlCamera(camera);
                }
            });
        } else {
            holder.btnExploit.setVisibility(View.GONE);
            holder.btnView.setVisibility(View.GONE);
            holder.btnControl.setVisibility(View.GONE);
        }
        
        // 设置详情区域点击事件
        holder.layoutCameraDetails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCameraDetails(camera);
            }
        });
        
        return convertView;
    }
    
    /**
     * 执行摄像头漏洞利用
     */
    private void exploitCamera(CameraInfo camera) {
        Toast.makeText(context, "开始尝试获取 " + camera.getName() + " 的权限...", Toast.LENGTH_SHORT).show();
        
        cameraExploiter.exploitCamera(camera, new CameraExploiter.ExploitCallback() {
            @Override
            public void onExploitAttempt(String method, String status) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, method + ": " + status, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onExploitSuccess(String method, String result) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "✅ " + method + " 成功!\n" + result, Toast.LENGTH_LONG).show();
                        // 更新摄像头权限状态
                        camera.setHasPermission(true);
                        notifyDataSetChanged();
                    }
                });
            }
            
            @Override
            public void onExploitComplete(List<CameraExploiter.ExploitResult> results) {
                ((MainActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder summary = new StringBuilder();
                        int successCount = 0;
                        
                        for (CameraExploiter.ExploitResult result : results) {
                            if (result.success) {
                                successCount++;
                                summary.append("✅ ").append(result.method).append("\n");
                            }
                        }
                        
                        if (successCount > 0) {
                            summary.insert(0, "漏洞利用完成! 成功: " + successCount + "/" + results.size() + "\n\n");
                            Toast.makeText(context, summary.toString(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "未发现可利用的漏洞，该摄像头安全性较好", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
    
    /**
     * 查看摄像头视频流
     */
    private void viewCameraStream(CameraInfo camera) {
        if (!camera.isAccessible()) {
            Toast.makeText(context, "摄像头不可访问", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 启动摄像头控制活动，仅查看模式
        Intent intent = new Intent(context, CameraControlActivity.class);
        intent.putExtra("camera_info", camera);
        intent.putExtra("stream_url", camera.getFullStreamUrl());
        intent.putExtra("control_mode", false);
        context.startActivity(intent);
    }
    
    /**
     * 控制摄像头
     */
    private void controlCamera(CameraInfo camera) {
        if (!camera.isAccessible()) {
            Toast.makeText(context, "摄像头不可访问", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 启动摄像头控制活动，控制模式
        Intent intent = new Intent(context, CameraControlActivity.class);
        intent.putExtra("camera_info", camera);
        intent.putExtra("stream_url", camera.getFullStreamUrl());
        intent.putExtra("control_mode", true);
        context.startActivity(intent);
    }
    
    /**
     * 显示摄像头详细信息
     */
    private void showCameraDetails(CameraInfo camera) {
        StringBuilder details = new StringBuilder();
        details.append("名称: ").append(camera.getName()).append("\n");
        details.append("类型: ").append(camera.getTypeString()).append("\n");
        details.append("状态: ").append(camera.getStatusString()).append("\n");
        
        if (camera.getType() == CameraInfo.CameraType.NETWORK) {
            details.append("IP地址: ").append(camera.getIpAddress()).append("\n");
            details.append("端口: ").append(camera.getPort()).append("\n");
            
            if (camera.getManufacturer() != null && !camera.getManufacturer().isEmpty()) {
                details.append("制造商: ").append(camera.getManufacturer()).append("\n");
            }
            
            if (camera.getModel() != null && !camera.getModel().isEmpty()) {
                details.append("型号: ").append(camera.getModel()).append("\n");
            }
            
            if (camera.getMacAddress() != null && !camera.getMacAddress().isEmpty()) {
                details.append("MAC地址: ").append(camera.getMacAddress()).append("\n");
            }
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("摄像头详情")
            .setMessage(details.toString())
            .setPositiveButton("确定", null)
            .show();
    }
    
    private static class ViewHolder {
        ImageView ivCameraIcon;
        TextView tvCameraName;
        TextView tvCameraType;
        TextView tvCameraStatus;
        TextView tvCameraId;
        Button btnExploit;
        Button btnView;
        Button btnControl;
        LinearLayout layoutCameraDetails;
    }
}