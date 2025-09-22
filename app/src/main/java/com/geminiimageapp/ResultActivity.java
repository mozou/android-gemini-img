package com.geminiimageapp;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

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

public class ResultActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button saveButton;
    private Button shareButton;
    private List<String> imagePaths;
    private ImagePagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // 获取传递的图片路径列表
        imagePaths = getIntent().getStringArrayListExtra("imagePaths");
        if (imagePaths == null) {
            imagePaths = new ArrayList<>();
        }

        // 初始化视图
        viewPager = findViewById(R.id.viewPager);
        saveButton = findViewById(R.id.saveButton);
        shareButton = findViewById(R.id.shareButton);

        // 设置适配器
        adapter = new ImagePagerAdapter(this, imagePaths);
        viewPager.setAdapter(adapter);

        // 设置按钮点击事件
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentImage();
            }
        });

        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCurrentImage();
            }
        });
    }

    private void saveCurrentImage() {
        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "没有可保存的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentPosition = viewPager.getCurrentItem();
        if (currentPosition >= 0 && currentPosition < imagePaths.size()) {
            String imagePath = imagePaths.get(currentPosition);
            File imageFile = new File(imagePath);

            if (!imageFile.exists()) {
                Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap == null) {
                    Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 保存到相册
                String savedImagePath = saveImageToGallery(bitmap);
                if (savedImagePath != null) {
                    Toast.makeText(this, R.string.image_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.image_save_failed, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "保存图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String saveImageToGallery(Bitmap bitmap) {
        String fileName = "GeminiGenerated_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeminiGenerated");
            
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    return uri.toString();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GeminiGenerated");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File file = new File(directory, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                
                // 通知相册更新
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(file);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);
                
                return file.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private void shareCurrentImage() {
        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "没有可分享的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentPosition = viewPager.getCurrentItem();
        if (currentPosition >= 0 && currentPosition < imagePaths.size()) {
            String imagePath = imagePaths.get(currentPosition);
            File imageFile = new File(imagePath);

            if (!imageFile.exists()) {
                Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri imageUri = Uri.fromFile(imageFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                startActivity(Intent.createChooser(shareIntent, "分享图片"));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "分享图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}