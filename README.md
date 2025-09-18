# Android Gemini 图像生成应用

这是一个使用Google Gemini API进行图像生成和修改的Android应用。该应用可以根据输入的提示词和上传的图片，完成生图、改图、合并图片等操作。

## 功能特点

- 上传本地图片作为基础图像
- 自定义场景描述
- 调整生成图片数量和重试次数
- 自定义身材特征和姿态描述
- 保存生成的图片到相册

## 使用方法

1. 获取Gemini API密钥：
   - 访问 [Google AI Studio](https://makersuite.google.com/app/apikey)
   - 创建一个API密钥

2. 在应用中填写以下信息：
   - API密钥（必填）
   - 场景描述（默认：漫展）
   - 生成图片数量（默认：1）
   - 最大重试次数（默认：3）
   - 身材特征（可选）
   - 姿态描述（默认：保持原有姿态）

3. 点击"选择图片"按钮上传一张基础图像

4. 点击"生成图片"按钮开始生成

5. 生成完成后，可以点击每张图片下方的"保存图片"按钮将图片保存到相册

## 技术实现

- 使用Java语言开发
- 集成Google Gemini API进行图像生成
- 使用RecyclerView显示生成结果
- 支持Android 7.0及以上版本

## 构建项目

1. 克隆仓库：
   ```
   git clone https://github.com/yourusername/android-gemini-img.git
   ```

2. 使用Android Studio打开项目

3. 构建并运行应用

## 注意事项

- 需要网络连接才能使用Gemini API
- 图像生成可能需要一些时间，请耐心等待
- 生成的图片质量取决于Gemini模型的能力和输入的提示词
- 请确保有足够的存储空间保存生成的图片

## 许可证

MIT License