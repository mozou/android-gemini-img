<![CDATA[package com.example.geminiimage;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private EditText apiKeyEditText, sceneEditText, lolicationEditText, posEditText, numOutputsEditText;
    private Button selectImageButton, generateButton;
    private ImageView imageView;
    private ProgressBar progressBar;
    private TextView resultTextView;

    private Uri selectedImageUri;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    Glide.with(this).load(selectedImageUri).into(imageView);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiKeyEditText = findViewById(R.id.apiKeyEditText);
        sceneEditText = findViewById(R.id.sceneEditText);
        lolicationEditText = findViewById(R.id.lolicationEditText);
        posEditText = findViewById(R.id.posEditText);
        numOutputsEditText = findViewById(R.id.numOutputsEditText);
        selectImageButton = findViewById(R.id.selectImageButton);
        generateButton = findViewById(R.id.generateButton);
        imageView = findViewById(R.id.imageView);
        progressBar = findViewById(R.id.progressBar);
        resultTextView = findViewById(R.id.resultTextView);

        selectImageButton.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                openImageSelector();
            }
        });

        generateButton.setOnClickListener(v -> generateImage());
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImageSelector();
            } else {
                Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void openImageSelector() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void generateImage() {
        String apiKey = apiKeyEditText.getText().toString().trim();
        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "API Key 不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "请先选择一张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        String scene = !TextUtils.isEmpty(sceneEditText.getText()) ? sceneEditText.getText().toString() : "漫展";
        String lolication = lolicationEditText.getText().toString();
        String pos = !TextUtils.isEmpty(posEditText.getText()) ? posEditText.getText().toString() : "，并保持原有姿态";
        int numOutputs;
        try {
            numOutputs = !TextUtils.isEmpty(numOutputsEditText.getText()) ? Integer.parseInt(numOutputsEditText.getText().toString()) : 1;
        } catch (NumberFormatException e) {
            numOutputs = 1;
        }

        String bodyInfo = !TextUtils.isEmpty(lolication) ? "除了胸部外" : "";

        String textInput = "一张顶级专业cosplay摄影作品。主角是一位身高148cm顶尖的可爱中国女coser，她拥有姣好的面部，化着淡妆，挺翘的鼻子，美瞳，化妆，白皮肤，" +
                lolication + "，光滑细腻的肌肤。她通过极其精致的妆容和神态表演，完美还原了图片主体的气质、发型和标志性表情。身材" +
                bodyInfo + "和图片一致。头发发质自然。她完整地穿着图片中的服装" + pos +
                "。服装材质表现出极高的真实感，有清晰的布料纹理、皮革光泽、丝袜质感和自然褶皱。年龄一致。\n" +
                "完全重塑图片光影及质感。场景位于" + scene + "中。明亮丰富打光，光照细节丰富。\n" +
                "最终画面要求顶级相机拍摄，RAW照片质感，皮肤纹理真实细腻，光影层次丰富。\n" +
                "绝对禁止出现任何二次元、卡通、3D模型或绘画元素，确保最终结果是100%逼真的真人摄影作品，尤其是面部一定是真人的面部，禁止出现任何二次元、卡通、3D模型或绘画元素面部。\n" +
                "生成时请思考画面是否真实？生成的coser是否和真人一样？如果不一样应该怎么办？";


        progressBar.setVisibility(View.VISIBLE);
        resultTextView.setText("正在生成中...");

        try {
            Bitmap inputImage = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
            GenerativeModel gm = new GenerativeModel("gemini-pro-vision", apiKey);
            GenerativeModelFutures model = GenerativeModelFutures.from(gm);

            Content content = new Content.Builder()
                    .addImage(inputImage)
                    .addText(textInput)
                    .build();

            for (int i = 0; i < numOutputs; i++) {
                final int index = i + 1;
                ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
                Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            try {
                                Bitmap generatedBitmap = result.getCandidates().get(0).getContent().getParts().get(0).getBlob().getDataAsBitmap();
                                imageView.setImageBitmap(generatedBitmap);
                                resultTextView.setText("第 " + index + " 张图片生成成功!");
                                saveImageToGallery(generatedBitmap);
                            } catch (Exception e) {
                                resultTextView.setText("生成成功，但解析图片失败: " + e.getMessage());
                                Log.e("GeminiApp", "Image parsing error", e);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            resultTextView.setText("生成失败: " + t.getMessage());
                            Log.e("GeminiApp", "Generation error", t);
                        });
                    }
                }, executor);
            }

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            resultTextView.setText("发生错误: " + e.getMessage());
            Log.e("GeminiApp", "Error", e);
        }
    }

    private void saveImageToGallery(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Gemini_Image_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeminiImages");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show();
                Log.e("GeminiApp", "Save image error", e);
            }
        }
    }
}
]]>