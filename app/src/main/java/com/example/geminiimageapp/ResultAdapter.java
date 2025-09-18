package com.example.geminiimageapp;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {

    private Context context;
    private List<Bitmap> images;
    private List<String> imagePaths;

    public ResultAdapter(Context context, List<Bitmap> images) {
        this.context = context;
        this.images = images;
        this.imagePaths = new ArrayList<>();
    }

    public void updateImages(List<Bitmap> images, List<String> paths) {
        this.images = images;
        this.imagePaths = paths;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        Bitmap image = images.get(position);
        Glide.with(context).load(image).into(holder.resultImageView);

        holder.saveButton.setOnClickListener(v -> {
            if (position < imagePaths.size()) {
                saveImageToGallery(imagePaths.get(position));
            }
        });
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        ImageView resultImageView;
        Button saveButton;

        public ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            resultImageView = itemView.findViewById(R.id.resultImageView);
            saveButton = itemView.findViewById(R.id.saveButton);
        }
    }

    private void saveImageToGallery(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                Toast.makeText(context, "图片文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            String fileName = "Gemini_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上使用MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeminiImages");

                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                         FileInputStream inputStream = new FileInputStream(imageFile)) {
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        
                        Toast.makeText(context, R.string.success_save, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                // Android 9及以下使用传统方法
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File geminiDir = new File(picturesDir, "GeminiImages");
                if (!geminiDir.exists()) {
                    geminiDir.mkdirs();
                }

                File destFile = new File(geminiDir, fileName);
                try (FileInputStream inputStream = new FileInputStream(imageFile);
                     FileOutputStream outputStream = new FileOutputStream(destFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    // 通知媒体库更新
                    context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));
                    Toast.makeText(context, R.string.success_save, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.error_save, Toast.LENGTH_SHORT).show();
        }
    }
}