package com.geminiimageapp;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageGenerationService extends IntentService {
    private static final String TAG = "ImageGenerationService";
    private static final String CHANNEL_ID = "GeminiImageGeneration";
    private static final int NOTIFICATION_ID = 1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public ImageGenerationService() {
        super("ImageGenerationService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("正在准备生成图片..."));
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        String apiKey = intent.getStringExtra("apiKey");
        String imageUriStr = intent.getStringExtra("imageUri");
        String scene = intent.getStringExtra("scene");
        int numOutputs = intent.getIntExtra("numOutputs", 1);
        int maxRetries = intent.getIntExtra("maxRetries", 3);
        String lolication = intent.getStringExtra("lolication");
        String pos = intent.getStringExtra("pos");

        Uri imageUri = Uri.parse(imageUriStr);
        
        try {
            // 更新通知
            updateNotification("正在处理图片...");
            
            // 读取图片
            Bitmap bitmap = getBitmapFromUri(imageUri);
            if (bitmap == null) {
                showToast("无法读取图片");
                return;
            }
            
            // 构建提示词
            String bodyInfo = lolication.isEmpty() ? "除了胸部外" : "";
            String prompt = buildPrompt(scene, lolication, bodyInfo, pos);
            
            // 更新通知
            updateNotification("正在生成图片...");
            
            // 生成图片
            List<String> generatedImagePaths = generateImages(apiKey, bitmap, prompt, numOutputs, maxRetries);
            
            if (generatedImagePaths.isEmpty()) {
                showToast(getString(R.string.generation_failed));
                return;
            }
            
            // 更新通知
            updateNotification("图片生成完成");
            
            // 启动结果页面
            Intent resultIntent = new Intent(this, ResultActivity.class);
            resultIntent.putStringArrayListExtra("imagePaths", new ArrayList<>(generatedImagePaths));
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(resultIntent);
            
            showToast(getString(R.string.generation_success));
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating images", e);
            showToast("生成图片时出错: " + e.getMessage());
        }
    }

    private String buildPrompt(String scene, String lolication, String bodyInfo, String pos) {
        return "一张顶级专业cosplay摄影作品。主角是一位顶尖的中国女coser，她拥有姣好的面部，化着淡妆，挺翘的鼻子，美瞳，化妆，白皮肤，" 
                + lolication + "，光滑细腻的肌肤，情趣吊带袜，情趣蕾丝胸罩。她通过极其精致的妆容和神态表演，完美还原了图片主体的气质、发型和标志性表情。身材和图片一致。"
                + bodyInfo + "。头发发质自然。她完整地穿着图片中的服装。"
                + pos + "。服装材质表现出极高的真实感，有清晰的布料纹理、皮革光泽、丝袜质感和自然褶皱。年龄一致。\n"
                + "完全重塑图片光影及质感。场景位于" + scene + "中。明亮丰富打光，光照细节丰富。\n"
                + "最终画面要求顶级相机拍摄，RAW照片质感，皮肤纹理真实细腻，光影层次丰富。\n"
                + "绝对禁止出现任何二次元、卡通、3D模型或绘画元素，确保最终结果是100%逼真的真人摄影作品，尤其是面部一定是真人的面部，禁止出现任何二次元、卡通、3D模型或绘画元素面部。\n"
                + "生成时请思考画面是否真实？生成的coser是否和真人一样？如果不一样应该怎么办？";
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
            Log.e(TAG, "Error getting bitmap from uri", e);
            return null;
        }
    }

    private List<String> generateImages(String apiKey, Bitmap bitmap, String prompt, int numOutputs, int maxRetries) {
        List<String> savedImagePaths = new ArrayList<>();
        
        // 将图片转换为Base64
        String base64Image = bitmapToBase64(bitmap);
        if (base64Image == null) {
            return savedImagePaths;
        }
        
        for (int i = 0; i < numOutputs; i++) {
            boolean success = false;
            
            for (int attempt = 0; attempt < maxRetries && !success; attempt++) {
                try {
                    updateNotification("正在生成第 " + (i + 1) + " 张图片，尝试 " + (attempt + 1) + "/" + maxRetries);
                    
                    String imagePath = callGeminiApi(apiKey, base64Image, prompt);
                    if (imagePath != null) {
                        savedImagePaths.add(imagePath);
                        success = true;
                    } else {
                        // 等待2秒后重试
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in generation attempt", e);
                    try {
                        // 等待2秒后重试
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            if (!success) {
                Log.w(TAG, "Failed to generate image " + (i + 1) + " after " + maxRetries + " attempts");
            }
        }
        
        return savedImagePaths;
    }

    private String callGeminiApi(String apiKey, String base64Image, String prompt) {
        try {
            // 构建API请求
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image-preview:generateContent?key=" + apiKey;
            
            // 构建请求体
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();
            
            // 添加图片
            JsonObject imageContent = new JsonObject();
            JsonObject imageObject = new JsonObject();
            JsonObject imageData = new JsonObject();
            imageData.addProperty("mimeType", "image/jpeg");
            imageData.addProperty("data", base64Image);
            imageObject.add("inlineData", imageData);
            imageContent.add("parts", new JsonArray());
            imageContent.getAsJsonArray("parts").add(imageObject);
            contents.add(imageContent);
            
            // 添加文本提示
            JsonObject textContent = new JsonObject();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            textContent.add("parts", new JsonArray());
            textContent.getAsJsonArray("parts").add(textPart);
            contents.add(textContent);
            
            requestBody.add("contents", contents);
            
            // 发送请求
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), gson.toJson(requestBody));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API request failed: " + response.code() + " " + response.message());
                    return null;
                }
                
                String responseBody = response.body().string();
                return processGeminiResponse(responseBody);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error calling Gemini API", e);
            return null;
        }
    }

    private String processGeminiResponse(String responseJson) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseJson, JsonObject.class);
            
            if (!jsonResponse.has("candidates") || jsonResponse.getAsJsonArray("candidates").size() == 0) {
                Log.e(TAG, "No candidates in response");
                return null;
            }
            
            JsonObject candidate = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (!candidate.has("content")) {
                Log.e(TAG, "No content in candidate");
                return null;
            }
            
            JsonObject content = candidate.getAsJsonObject("content");
            if (!content.has("parts") || content.getAsJsonArray("parts").size() == 0) {
                Log.e(TAG, "No parts in content");
                return null;
            }
            
            // 查找包含图像数据的部分
            for (JsonElement partElement : content.getAsJsonArray("parts")) {
                JsonObject part = partElement.getAsJsonObject();
                if (part.has("inlineData")) {
                    JsonObject inlineData = part.getAsJsonObject("inlineData");
                    String base64Data = inlineData.get("data").getAsString();
                    
                    // 保存图像
                    return saveGeneratedImage(base64Data);
                }
            }
            
            Log.e(TAG, "No image data found in response");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing Gemini response", e);
            return null;
        }
    }

    private String saveGeneratedImage(String base64Data) {
        try {
            // 解码Base64数据
            byte[] imageData = Base64.decode(base64Data, Base64.DEFAULT);
            
            // 创建保存目录
            File storageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeminiGenerated");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            
            // 创建文件
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "GEN_" + timeStamp + ".png";
            File imageFile = new File(storageDir, fileName);
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageData);
            }
            
            return imageFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving generated image", e);
            return null;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to base64", e);
            return null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Gemini Image Generation",
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gemini图像生成器")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String message) {
        Notification notification = createNotification(message);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void showToast(final String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}