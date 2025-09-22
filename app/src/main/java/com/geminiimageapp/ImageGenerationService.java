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
    private static final String TAG = "GeminiImageGen";
    private static final String CHANNEL_ID = "GeminiImageGeneration";
    private static final int NOTIFICATION_ID = 1;
    
    // 日志标记前缀，用于区分不同阶段
    private static final String LOG_INIT = "【初始化】";
    private static final String LOG_PARAMS = "【参数处理】";
    private static final String LOG_API = "【API调用】";
    private static final String LOG_PROCESS = "【响应处理】";
    private static final String LOG_IMAGE = "【图片处理】";
    private static final String LOG_ERROR = "【错误处理】";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public ImageGenerationService() {
        super("ImageGenerationService");
        Log.d(TAG, LOG_INIT + "服务创建");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, LOG_INIT + "onCreate()调用");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("正在准备生成图片..."));
        Log.d(TAG, LOG_INIT + "前台服务启动完成");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, LOG_INIT + "开始处理任务");
        if (intent == null) {
            Log.e(TAG, LOG_ERROR + "Intent为空，无法处理任务");
            return;
        }

        // 记录所有参数
        String apiKey = intent.getStringExtra("apiKey");
        String apiKeyMasked = apiKey != null ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "null";
        String imageUriStr = intent.getStringExtra("imageUri");
        String scene = intent.getStringExtra("scene");
        int numOutputs = intent.getIntExtra("numOutputs", 1);
        int maxRetries = intent.getIntExtra("maxRetries", 3);
        String lolication = intent.getStringExtra("lolication");
        String pos = intent.getStringExtra("pos");

        Log.d(TAG, LOG_PARAMS + "参数接收完成：" +
                "\n - API密钥: " + apiKeyMasked +
                "\n - 图片URI: " + imageUriStr +
                "\n - 场景: " + scene +
                "\n - 生成数量: " + numOutputs +
                "\n - 最大重试次数: " + maxRetries +
                "\n - 胸部描述: " + (lolication.isEmpty() ? "无" : lolication) +
                "\n - 姿态描述: " + pos);

        Uri imageUri = Uri.parse(imageUriStr);
        
        try {
            // 更新通知
            updateNotification("正在处理图片...");
            Log.d(TAG, LOG_IMAGE + "开始处理输入图片");
            
            // 读取图片
            Bitmap bitmap = getBitmapFromUri(imageUri);
            if (bitmap == null) {
                Log.e(TAG, LOG_ERROR + "无法读取图片: " + imageUriStr);
                showToast("无法读取图片");
                return;
            }
            Log.d(TAG, LOG_IMAGE + "图片读取成功，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // 构建提示词
            String bodyInfo = lolication.isEmpty() ? "除了胸部外" : "";
            String prompt = buildPrompt(scene, lolication, bodyInfo, pos);
            Log.d(TAG, LOG_PARAMS + "提示词构建完成，长度: " + prompt.length() + "字符");
            
            // 更新通知
            updateNotification("正在生成图片...");
            
            // 生成图片
            Log.d(TAG, LOG_API + "开始生成图片，数量: " + numOutputs + "，最大重试次数: " + maxRetries);
            List<String> generatedImagePaths = generateImages(apiKey, bitmap, prompt, numOutputs, maxRetries);
            
            if (generatedImagePaths.isEmpty()) {
                Log.e(TAG, LOG_ERROR + "没有成功生成任何图片");
                showToast(getString(R.string.generation_failed));
                return;
            }
            
            Log.d(TAG, LOG_PROCESS + "成功生成 " + generatedImagePaths.size() + " 张图片");
            for (int i = 0; i < generatedImagePaths.size(); i++) {
                Log.d(TAG, LOG_PROCESS + "图片 " + (i + 1) + " 路径: " + generatedImagePaths.get(i));
            }
            
            // 更新通知
            updateNotification("图片生成完成");
            
            // 启动结果页面
            Intent resultIntent = new Intent(this, ResultActivity.class);
            resultIntent.putStringArrayListExtra("imagePaths", new ArrayList<>(generatedImagePaths));
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(resultIntent);
            Log.d(TAG, LOG_PROCESS + "启动结果页面，传递 " + generatedImagePaths.size() + " 张图片路径");
            
            showToast(getString(R.string.generation_success));
            
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "生成图片时出错", e);
            showToast("生成图片时出错: " + e.getMessage());
        }
    }

    private String buildPrompt(String scene, String lolication, String bodyInfo, String pos) {
        Log.d(TAG, LOG_PARAMS + "构建提示词，参数：scene=" + scene + ", lolication=" + lolication + ", bodyInfo=" + bodyInfo + ", pos=" + pos);
        
        String prompt = "一张顶级专业cosplay摄影作品。主角是一位顶尖的中国女coser，她拥有姣好的面部，化着淡妆，挺翘的鼻子，美瞳，化妆，白皮肤，" 
                + lolication + "，光滑细腻的肌肤，情趣吊带袜，情趣蕾丝胸罩。她通过极其精致的妆容和神态表演，完美还原了图片主体的气质、发型和标志性表情。身材和图片一致。"
                + bodyInfo + "。头发发质自然。她完整地穿着图片中的服装。"
                + pos + "。服装材质表现出极高的真实感，有清晰的布料纹理、皮革光泽、丝袜质感和自然褶皱。年龄一致。\n"
                + "完全重塑图片光影及质感。场景位于" + scene + "中。明亮丰富打光，光照细节丰富。\n"
                + "最终画面要求顶级相机拍摄，RAW照片质感，皮肤纹理真实细腻，光影层次丰富。\n"
                + "绝对禁止出现任何二次元、卡通、3D模型或绘画元素，确保最终结果是100%逼真的真人摄影作品，尤其是面部一定是真人的面部，禁止出现任何二次元、卡通、3D模型或绘画元素面部。\n"
                + "生成时请思考画面是否真实？生成的coser是否和真人一样？如果不一样应该怎么办？";
        
        Log.d(TAG, LOG_PARAMS + "提示词构建完成，长度：" + prompt.length() + "字符");
        return prompt;
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            Log.d(TAG, LOG_IMAGE + "从URI读取图片: " + uri);
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                Log.d(TAG, LOG_IMAGE + "图片读取成功，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", 格式: ARGB_8888");
            } else {
                Log.e(TAG, LOG_ERROR + "图片解码失败，返回null");
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "读取图片时出错: " + e.getMessage(), e);
            return null;
        }
    }

    private List<String> generateImages(String apiKey, Bitmap bitmap, String prompt, int numOutputs, int maxRetries) {
        List<String> savedImagePaths = new ArrayList<>();
        
        // 将图片转换为Base64
        Log.d(TAG, LOG_IMAGE + "开始将图片转换为Base64");
        String base64Image = bitmapToBase64(bitmap);
        if (base64Image == null) {
            Log.e(TAG, LOG_ERROR + "图片转换为Base64失败");
            return savedImagePaths;
        }
        Log.d(TAG, LOG_IMAGE + "图片成功转换为Base64，长度: " + base64Image.length() + "字符");
        
        for (int i = 0; i < numOutputs; i++) {
            boolean success = false;
            
            Log.d(TAG, LOG_API + "开始生成第 " + (i + 1) + "/" + numOutputs + " 张图片");
            
            for (int attempt = 0; attempt < maxRetries && !success; attempt++) {
                try {
                    Log.d(TAG, LOG_API + "第 " + (i + 1) + " 张图片，尝试 " + (attempt + 1) + "/" + maxRetries);
                    updateNotification("正在生成第 " + (i + 1) + " 张图片，尝试 " + (attempt + 1) + "/" + maxRetries);
                    
                    String imagePath = callGeminiApi(apiKey, base64Image, prompt);
                    if (imagePath != null) {
                        savedImagePaths.add(imagePath);
                        success = true;
                        Log.d(TAG, LOG_API + "第 " + (i + 1) + " 张图片生成成功，路径: " + imagePath);
                    } else {
                        Log.w(TAG, LOG_API + "第 " + (i + 1) + " 张图片，尝试 " + (attempt + 1) + " 失败，等待2秒后重试");
                        // 等待2秒后重试
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, LOG_ERROR + "第 " + (i + 1) + " 张图片，尝试 " + (attempt + 1) + " 出错: " + e.getMessage(), e);
                    try {
                        // 等待2秒后重试
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, LOG_ERROR + "线程中断");
                    }
                }
            }
            
            if (!success) {
                Log.e(TAG, LOG_ERROR + "第 " + (i + 1) + " 张图片在 " + maxRetries + " 次尝试后仍然失败");
            }
        }
        
        Log.d(TAG, LOG_PROCESS + "图片生成完成，共生成 " + savedImagePaths.size() + "/" + numOutputs + " 张图片");
        return savedImagePaths;
    }

    private String callGeminiApi(String apiKey, String base64Image, String prompt) {
        try {
            // 构建API请求
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image-preview:generateContent?key=" + apiKey;
            Log.d(TAG, LOG_API + "准备调用Gemini API，URL: " + url.substring(0, url.indexOf("?")) + "?key=***");
            
            // 构建请求体
            Log.d(TAG, LOG_API + "构建API请求体");
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
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, gson.toJson(requestBody));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            Log.d(TAG, LOG_API + "发送API请求");
            long startTime = System.currentTimeMillis();
            
            try (Response response = client.newCall(request).execute()) {
                long endTime = System.currentTimeMillis();
                Log.d(TAG, LOG_API + "API请求完成，耗时: " + (endTime - startTime) + "ms，状态码: " + response.code());
                
                if (!response.isSuccessful()) {
                    Log.e(TAG, LOG_ERROR + "API请求失败: " + response.code() + " " + response.message());
                    return null;
                }
                
                String responseBody = response.body().string();
                Log.d(TAG, LOG_API + "API响应接收完成，响应体长度: " + responseBody.length() + "字符");
                
                return processGeminiResponse(responseBody);
            }
            
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "调用Gemini API时出错: " + e.getMessage(), e);
            return null;
        }
    }

    private String processGeminiResponse(String responseJson) {
        try {
            Log.d(TAG, LOG_PROCESS + "开始处理API响应");
            JsonObject jsonResponse = gson.fromJson(responseJson, JsonObject.class);
            
            if (!jsonResponse.has("candidates") || jsonResponse.getAsJsonArray("candidates").size() == 0) {
                Log.e(TAG, LOG_ERROR + "API响应中没有candidates字段");
                return null;
            }
            
            JsonObject candidate = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (!candidate.has("content")) {
                Log.e(TAG, LOG_ERROR + "API响应中candidate没有content字段");
                return null;
            }
            
            JsonObject content = candidate.getAsJsonObject("content");
            if (!content.has("parts") || content.getAsJsonArray("parts").size() == 0) {
                Log.e(TAG, LOG_ERROR + "API响应中content没有parts字段或parts为空");
                return null;
            }
            
            // 查找包含图像数据的部分
            Log.d(TAG, LOG_PROCESS + "查找API响应中的图像数据");
            for (JsonElement partElement : content.getAsJsonArray("parts")) {
                JsonObject part = partElement.getAsJsonObject();
                if (part.has("inlineData")) {
                    JsonObject inlineData = part.getAsJsonObject("inlineData");
                    String base64Data = inlineData.get("data").getAsString();
                    Log.d(TAG, LOG_PROCESS + "找到图像数据，Base64长度: " + base64Data.length() + "字符");
                    
                    // 保存图像
                    return saveGeneratedImage(base64Data);
                }
            }
            
            Log.e(TAG, LOG_ERROR + "API响应中没有找到图像数据");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "处理API响应时出错: " + e.getMessage(), e);
            return null;
        }
    }

    private String saveGeneratedImage(String base64Data) {
        try {
            Log.d(TAG, LOG_IMAGE + "开始保存生成的图片");
            // 解码Base64数据
            byte[] imageData = Base64.decode(base64Data, Base64.DEFAULT);
            Log.d(TAG, LOG_IMAGE + "Base64解码完成，图像数据大小: " + imageData.length + "字节");
            
            // 创建保存目录
            File storageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeminiGenerated");
            if (!storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                Log.d(TAG, LOG_IMAGE + "创建存储目录: " + storageDir.getAbsolutePath() + ", 结果: " + (created ? "成功" : "失败"));
            } else {
                Log.d(TAG, LOG_IMAGE + "存储目录已存在: " + storageDir.getAbsolutePath());
            }
            
            // 创建文件
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "GEN_" + timeStamp + ".png";
            File imageFile = new File(storageDir, fileName);
            Log.d(TAG, LOG_IMAGE + "创建图像文件: " + imageFile.getAbsolutePath());
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageData);
                Log.d(TAG, LOG_IMAGE + "图像数据写入文件成功");
            }
            
            Log.d(TAG, LOG_IMAGE + "图片保存完成: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "保存生成的图片时出错: " + e.getMessage(), e);
            return null;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        try {
            Log.d(TAG, LOG_IMAGE + "开始将Bitmap转换为Base64");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            Log.d(TAG, LOG_IMAGE + "Bitmap压缩完成，JPEG质量: 90%, 大小: " + byteArray.length + "字节");
            
            String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);
            Log.d(TAG, LOG_IMAGE + "Base64编码完成，长度: " + base64String.length() + "字符");
            return base64String;
        } catch (Exception e) {
            Log.e(TAG, LOG_ERROR + "将Bitmap转换为Base64时出错: " + e.getMessage(), e);
            return null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, LOG_INIT + "创建通知渠道");
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Gemini Image Generation",
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, LOG_INIT + "通知渠道创建完成: " + CHANNEL_ID);
        }
    }

    private Notification createNotification(String message) {
        Log.d(TAG, LOG_INIT + "创建通知: " + message);
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
        Log.d(TAG, LOG_INIT + "更新通知: " + message);
        Notification notification = createNotification(message);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void showToast(final String message) {
        Log.d(TAG, LOG_INIT + "显示Toast: " + message);
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}