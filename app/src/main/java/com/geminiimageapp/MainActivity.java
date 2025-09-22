package com.geminiimageapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText apiKeyInput, sceneInput, numOutputsInput, maxRetriesInput, lolicationInput, posInput;
    private ImageView selectedImageView;
    private Button selectImageButton, takePhotoButton, generateButton;
    private ProgressBar progressBar;

    private Uri selectedImageUri;
    private String currentPhotoPath;
    private SharedPreferences sharedPreferences;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    loadImage(selectedImageUri);
                }
            });

    private final ActivityResultLauncher<Intent> takePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    selectedImageUri = Uri.fromFile(new File(currentPhotoPath));
                    loadImage(selectedImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("GeminiImageApp", MODE_PRIVATE);

        initViews();
        loadSavedValues();
        setupListeners();
    }

    private void initViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput);
        sceneInput = findViewById(R.id.sceneInput);
        numOutputsInput = findViewById(R.id.numOutputsInput);
        maxRetriesInput = findViewById(R.id.maxRetriesInput);
        lolicationInput = findViewById(R.id.lolicationInput);
        posInput = findViewById(R.id.posInput);
        selectedImageView = findViewById(R.id.selectedImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        takePhotoButton = findViewById(R.id.takePhotoButton);
        generateButton = findViewById(R.id.generateButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void loadSavedValues() {
        apiKeyInput.setText(sharedPreferences.getString("apiKey", ""));
        sceneInput.setText(sharedPreferences.getString("scene", "漫展"));
        numOutputsInput.setText(sharedPreferences.getString("numOutputs", "1"));
        maxRetriesInput.setText(sharedPreferences.getString("maxRetries", "3"));
        lolicationInput.setText(sharedPreferences.getString("lolication", ""));
        posInput.setText(sharedPreferences.getString("pos", "，并保持原有姿态"));
    }

    private void setupListeners() {
        selectImageButton.setOnClickListener(v -> checkGalleryPermissions());
        takePhotoButton.setOnClickListener(v -> checkCameraPermissions());
        generateButton.setOnClickListener(v -> validateAndGenerate());
    }

    private void checkGalleryPermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        Dexter.withContext(this)
                .withPermissions(permissions)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            openGallery();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private void checkCameraPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        Dexter.withContext(this)
                .withPermissions(permissions)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            openCamera();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePhotoLauncher.launch(takePictureIntent);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void loadImage(Uri uri) {
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(selectedImageView);
    }

    private void validateAndGenerate() {
        String apiKey = apiKeyInput.getText().toString().trim();
        String scene = sceneInput.getText().toString().trim();
        String numOutputsStr = numOutputsInput.getText().toString().trim();
        String maxRetriesStr = maxRetriesInput.getText().toString().trim();
        String lolication = lolicationInput.getText().toString().trim();
        String pos = posInput.getText().toString().trim();

        // 保存输入值
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("apiKey", apiKey);
        editor.putString("scene", scene);
        editor.putString("numOutputs", numOutputsStr);
        editor.putString("maxRetries", maxRetriesStr);
        editor.putString("lolication", lolication);
        editor.putString("pos", pos);
        editor.apply();

        // 验证输入
        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, R.string.no_api_key, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, R.string.no_image_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        // 设置默认值
        if (TextUtils.isEmpty(scene)) {
            scene = "漫展";
        }

        int numOutputs = 1;
        try {
            if (!TextUtils.isEmpty(numOutputsStr)) {
                numOutputs = Integer.parseInt(numOutputsStr);
            }
        } catch (NumberFormatException e) {
            numOutputs = 1;
        }

        int maxRetries = 3;
        try {
            if (!TextUtils.isEmpty(maxRetriesStr)) {
                maxRetries = Integer.parseInt(maxRetriesStr);
            }
        } catch (NumberFormatException e) {
            maxRetries = 3;
        }

        if (TextUtils.isEmpty(pos)) {
            pos = "，并保持原有姿态";
        }

        // 显示进度条
        progressBar.setVisibility(View.VISIBLE);
        generateButton.setEnabled(false);

        // 启动图像生成服务
        Intent serviceIntent = new Intent(this, ImageGenerationService.class);
        serviceIntent.putExtra("apiKey", apiKey);
        serviceIntent.putExtra("imageUri", selectedImageUri.toString());
        serviceIntent.putExtra("scene", scene);
        serviceIntent.putExtra("numOutputs", numOutputs);
        serviceIntent.putExtra("maxRetries", maxRetries);
        serviceIntent.putExtra("lolication", lolication);
        serviceIntent.putExtra("pos", pos);
        
        startService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重置UI状态
        progressBar.setVisibility(View.GONE);
        generateButton.setEnabled(true);
    }
}