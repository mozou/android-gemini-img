package com.example.geminiimageapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private TextInputEditText apiKeyEditText;
    private TextInputEditText sceneEditText;
    private TextInputEditText numOutputsEditText;
    private TextInputEditText maxRetriesEditText;
    private TextInputEditText lolicationEditText;
    private TextInputEditText posEditText;
    private Button selectImageButton;
    private ImageView inputImageView;
    private Button generateButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private RecyclerView resultRecyclerView;

    private Uri selectedImageUri;
    private ResultAdapter resultAdapter;
    private List<Bitmap> resultImages = new ArrayList<>();
    private GeminiImageGenerator imageGenerator;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                        inputImageView.setVisibility(View.VISIBLE);
                        Glide.with(this).load(bitmap).into(inputImageView);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        setupRecyclerView();
        checkPermissions();
    }

    private void initViews() {
        apiKeyEditText = findViewById(R.id.apiKeyEditText);
        sceneEditText = findViewById(R.id.sceneEditText);
        numOutputsEditText = findViewById(R.id.numOutputsEditText);
        maxRetriesEditText = findViewById(R.id.maxRetriesEditText);
        lolicationEditText = findViewById(R.id.lolicationEditText);
        posEditText = findViewById(R.id.posEditText);
        selectImageButton = findViewById(R.id.selectImageButton);
        inputImageView = findViewById(R.id.inputImageView);
        generateButton = findViewById(R.id.generateButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        resultRecyclerView = findViewById(R.id.resultRecyclerView);
    }

    private void setupListeners() {
        selectImageButton.setOnClickListener(v -> openImagePicker());
        generateButton.setOnClickListener(v -> generateImages());
    }

    private void setupRecyclerView() {
        resultAdapter = new ResultAdapter(this, resultImages);
        resultRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultRecyclerView.setAdapter(resultAdapter);
    }

    private void checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void generateImages() {
        String apiKey = apiKeyEditText.getText().toString().trim();
        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, R.string.error_api_key, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, R.string.error_image, Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取参数
        String scene = sceneEditText.getText().toString().trim();
        if (TextUtils.isEmpty(scene)) {
            scene = "漫展";
        }

        int numOutputs;
        try {
            numOutputs = Integer.parseInt(numOutputsEditText.getText().toString().trim());
        } catch (NumberFormatException e) {
            numOutputs = 1;
        }

        int maxRetries;
        try {
            maxRetries = Integer.parseInt(maxRetriesEditText.getText().toString().trim());
        } catch (NumberFormatException e) {
            maxRetries = 3;
        }

        String lolication = lolicationEditText.getText().toString().trim();
        String pos = posEditText.getText().toString().trim();
        if (TextUtils.isEmpty(pos)) {
            pos = "，并保持原有姿态";
        }

        // 显示进度
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText(R.string.processing);
        generateButton.setEnabled(false);

        // 创建保存目录
        File saveDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeminiImages");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        // 初始化生成器
        if (imageGenerator == null) {
            imageGenerator = new GeminiImageGenerator(this);
        }
        imageGenerator.setApiKey(apiKey);

        // 开始生成
        try {
            imageGenerator.generate(selectedImageUri, scene, numOutputs, maxRetries, lolication, pos, saveDir.getAbsolutePath(),
                    new GeminiImageGenerator.GenerationCallback() {
                        @Override
                        public void onSuccess(List<Bitmap> images, List<String> paths) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusTextView.setText(R.string.success_generate);
                                generateButton.setEnabled(true);
                                
                                resultImages.clear();
                                resultImages.addAll(images);
                                resultAdapter.updateImages(images, paths);
                                resultAdapter.notifyDataSetChanged();
                            });
                        }

                        @Override
                        public void onFailure(String error) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusTextView.setText(error);
                                generateButton.setEnabled(true);
                            });
                        }

                        @Override
                        public void onProgress(String message) {
                            runOnUiThread(() -> {
                                statusTextView.setText(message);
                            });
                        }
                    });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            statusTextView.setText(e.getMessage());
            generateButton.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "需要权限才能正常使用应用", Toast.LENGTH_LONG).show();
            }
        }
    }
}