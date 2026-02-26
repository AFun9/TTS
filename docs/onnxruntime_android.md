# 为 Android 使用 ONNX Runtime

本应用的真实 TTS 依赖 **ONNX Runtime**（仅此依赖，不包含 sherpa-onnx）。推荐直接用 Gradle 依赖，由构建自动下载并参与链接。

## 一、推荐：直接作为依赖（无需手动下载）

在 **`android/app/build.gradle.kts`** 里已添加：

```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.1")
```

- **Gradle 会从 Maven 自动下载**该 AAR，其中的 `libonnxruntime.so` 会打进 APK。
- 构建时会跑任务 **`prepareOnnxRuntime`**：从该 AAR 解出 `.so` 到 `build/onnxruntime/lib/`，并从 GitHub 下载对应版本的 C++ 头文件到 `build/onnxruntime/include/`，供 CMake 编译并链接。
- 你只需 **Sync Project / 编译运行**，无需再配置路径。首次构建需要网络（下载 AAR 和源码 zip）。

## 二、可选：使用本机已构建的 ONNX Runtime

若你已在本地为 Android 构建好 ONNX Runtime（含 `include/` 与 `lib/arm64-v8a` 等），可跳过自动下载，在 **`android/gradle.properties`** 中配置：

```properties
onnxruntime.root=/你的/onnxruntime-android/install
```

这样 CMake 会使用该目录，不再执行 `prepareOnnxRuntime` 的下载与解压。

构建方式参考：[Build ONNX Runtime for Android](https://onnxruntime.ai/docs/build/android.html)。

## 三、不使用时

若移除 `implementation("com.microsoft.onnxruntime:onnxruntime-android:...")` 且未设置 `onnxruntime.root`，只会编译占位 JNI，应用内点击生成会失败，logcat 会看到「当前为占位构建，未链接 ONNX Runtime」。
