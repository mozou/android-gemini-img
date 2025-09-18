package com.example.geminiimageapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.Part;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

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

public class GeminiImageGenerator {
    private static final String TAG = "GeminiImageGenerator";
    private static final String MODEL_NAME = "gemini-2.5-flash-image-preview";
    
    private Context context;
    private String apiKey;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeminiImageGenerator(Context context) {
        this.context = context;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public interface GenerationCallback {
        void onSuccess(List<Bitmap> images, List<String> paths);
        void onFailure(String error);
        void onProgress(String message);
    }

    public void generate(Uri imageUri, String scene, int numOutputs, int maxRetries,
                         String lolication, String pos, String saveRoot, GenerationCallback callback) {
        executor.execute(() -> {
            try {
                // 读取输入图片
                Bitmap inputBitmap = getBitmapFromUri(imageUri);
                if (inputBitmap == null) {
                    mainHandler.post(() -> callback.onFailure("无法读取输入图片"));
                    return;
                }

                // 创建保存目录
                String baseName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File saveDir = new File(saveRoot, baseName);
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                // 构建提示词
                String bodyInfo = lolication.isEmpty() ? "" : "除了胸部外";
                String textInput = buildPrompt(scene, lolication, bodyInfo, pos);
                
                mainHandler.post(() -> callback.onProgress("正在初始化Gemini模型..."));
                
                // 初始化Gemini客户端
                GenerativeModel model = new GenerativeModel(
                        MODEL_NAME,
                        apiKey
                );
                GenerativeModelFutures modelFutures = GenerativeModelFutures.from(model);

                List<Bitmap> resultBitmaps = new ArrayList<>();
                List<String> savedFilePaths = new ArrayList<>();

                for (int i = 0; i < numOutputs; i++) {
                    boolean success = false;
                    for (int attempt = 0; attempt < maxRetries && !success; attempt++) {
                        final int currentOutput = i + 1;
                        final int currentAttempt = attempt + 1;
                        
                        mainHandler.post(() -> callback.onProgress(
                                String.format("正在生成第%d张图片 (尝试 %d/%d)...", 
                                        currentOutput, currentAttempt, maxRetries)));
                        
                        try {
                            // 准备内容
                            List<Part> parts = new ArrayList<>();
                            parts.add(Part.fromBitmap(inputBitmap));
                            parts.add(Part.fromText(textInput));
                            Content content = Content.fromParts(parts);
                            
                            // 发送请求
                            ListenableFuture<GenerateContentResponse> future = 
                                    modelFutures.generateContent(content);
                            
                            // 处理响应
                            GenerateContentResponse response = future.get();
                            
                            if (response != null && response.getCandidates() != null && 
                                    !response.getCandidates().isEmpty()) {
                                
                                List<Part> responseParts = response.getCandidates().get(0)
                                        .getContent().getParts();
                                
                                for (Part part : responseParts) {
                                    if (part.getInlineData() != null) {
                                        byte[] imageData = part.getInlineData().getData();
                                        Bitmap resultBitmap = BitmapFactory.decodeByteArray(
                                                imageData, 0, imageData.length);
                                        
                                        if (resultBitmap != null) {
                                            // 保存图片
                                            String filename = String.format("%s_%d.png", 
                                                    baseName, System.currentTimeMillis());
                                            File outputFile = new File(saveDir, filename);
                                            
                                            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                                                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                                resultBitmaps.add(resultBitmap);
                                                savedFilePaths.add(outputFile.getAbsolutePath());
                                                success = true;
                                                
                                                mainHandler.post(() -> callback.onProgress(
                                                        String.format("第%d张图片生成成功!", currentOutput)));
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (!success) {
                                mainHandler.post(() -> callback.onProgress(
                                        String.format("第%d张图片 - 第%d次尝试失败: 无图像数据", 
                                                currentOutput, currentAttempt)));
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "生成失败", e);
                            mainHandler.post(() -> callback.onProgress(
                                    String.format("第%d张图片 - 第%d次尝试失败: %s", 
                                            currentOutput, currentAttempt, e.getMessage())));
                        }
                        
                        // 避免频繁请求
                        if (!success && attempt < maxRetries - 1) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    
                    if (!success) {
                        mainHandler.post(() -> callback.onProgress(
                                String.format("第%d张图片已达到最大重试次数，仍未成功。", currentOutput)));
                    }
                }
                
                // 返回结果
                final List<Bitmap> finalResultBitmaps = resultBitmaps;
                final List<String> finalSavedFilePaths = savedFilePaths;
                
                mainHandler.post(() -> {
                    if (!finalResultBitmaps.isEmpty()) {
                        callback.onSuccess(finalResultBitmaps, finalSavedFilePaths);
                    } else {
                        callback.onFailure("未能生成任何图片");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "生成过程中发生错误", e);
                mainHandler.post(() -> callback.onFailure("生成过程中发生错误: " + e.getMessage()));
            }
        });
    }

    private String buildPrompt(String scene, String lolication, String bodyInfo, String pos) {
        return "一张顶级专业cosplay摄影作品。主角是一位身高148cm顶尖的可爱中国女coser，她拥有姣好的面部，化着淡妆，挺翘的鼻子，美瞳，化妆，白皮肤，" 
                + lolication + "，光滑细腻的肌肤。她通过极其精致的妆容和神态表演，完美还原了图片主体的气质、发型和标志性表情。身材" 
                + bodyInfo + "和图片一致。头发发质自然。她完整地穿着图片中的服装" 
                + pos + "。服装材质表现出极高的真实感，有清晰的布料纹理、皮革光泽、丝袜质感和自然褶皱。年龄一致。\n"
                + "完全重塑图片光影及质感。场景位于" + scene + "中。明亮丰富打光，光照细节丰富。\n"
                + "最终画面要求顶级相机拍摄，RAW照片质感，皮肤纹理真实细腻，光影层次丰富。\n"
                + "绝对禁止出现任何二次元、卡通、3D模型或绘画元素，确保最终结果是100%逼真的真人摄影作品，尤其是面部一定是真人的面部，禁止出现任何二次元、卡通、3D模型或绘画元素面部。\n"
                + "生成时请思考画面是否真实？生成的coser是否和真人一样？如果不一样应该怎么办？";
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.e(TAG, "无法从URI获取位图", e);
            return null;
        }
    }
}