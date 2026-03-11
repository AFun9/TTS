# Android App

这个目录包含 `Sherpa TTS` 的 Android 应用、JNI 桥接代码，以及所需的原生依赖集成逻辑。

## 目录说明

- `app/src/main/java/com/k2fsa/sherpa/tts/data`
  Android 侧配置与数据模型。
- `app/src/main/java/com/k2fsa/sherpa/tts/engine`
  Kotlin 到 JNI 的引擎包装。
- `app/src/main/java/com/k2fsa/sherpa/tts/repository`
  负责创建/复用 TTS 引擎并生成音频。
- `app/src/main/java/com/k2fsa/sherpa/tts/ui`
  主界面、历史记录、自定义词典等 UI。
- `app/src/main/java/com/k2fsa/sherpa/tts/util`
  偏好、文件复制、历史记录、`espeak-ng-data` 解压等辅助逻辑。
- `app/src/main/cpp`
  JNI、前端路由、词典切分、VITS 推理与 WAV 写出。
- `third_party`
  固化后的 `espeak-ng` 与 `piper-phonemize` 源码。

## 环境要求

- Android Studio Hedgehog 或更高
- JDK 17
- Android SDK 34
- Android NDK / CMake（Android Studio 默认组件即可）

## 构建

在 `android/` 目录执行：

```bash
./gradlew :app:assembleDebug
```

默认会：

1. 解析 `onnxruntime-android` AAR。
2. 自动准备 ONNX Runtime 的头文件与 Android `.so`。
3. 通过 CMake 构建 `sherpa-tts-jni`。

## 可选 Gradle 属性

- `onnxruntime.include.root`
  指向本地 ONNX Runtime 头文件根目录，跳过自动下载源码 zip。
- `onnxruntime.root`
  指向本地 ONNX Runtime 根目录。
- `sherpa.tts.enableEspeakNg=true`
  启用 `espeak-ng` 前端。
- `sherpa.tts.espeakNg.sourceDir`
  指向本地 `espeak-ng` 源码目录。
- `sherpa.tts.piperPhonemize.sourceDir`
  指向本地 `piper-phonemize` 源码目录。
- `sherpa.tts.abis=arm64-v8a`
  指定构建 ABI，默认仅 `arm64-v8a`。

## 运行与资源

- 应用会从文档选择器导入模型、`tokens.txt`、可选 `lexicon.txt`。
- `main` source set 会把 `src/main/assets` 与仓库根的 `models` 目录作为 assets。
- `EspeakDataHelper` 会在首次使用时将 `espeak-ng-data` 解压到应用私有目录。

## 测试

仅构建验证：

```bash
./gradlew :app:assembleDebug
```

连接设备或模拟器后的烟雾测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

当前包含的 Android 仪器测试：

- `TtsRuEspeakSmokeTest`
  使用俄语模型和 `EspeakOnly` 前端，验证可成功生成 WAV。

## 调试建议

- 查看 native / JNI 日志：

```bash
adb logcat -s SherpaTts SherpaTtsRepo
```

- 如果看到引擎初始化失败，优先检查：
  - 模型路径与 `tokens.txt` 是否匹配
  - `espeak-ng-data` 是否成功解压
  - ONNX Runtime 头文件与 Android `.so` 是否准备完成

## 注意事项

- 根目录新增的 `.gitignore` 只会阻止未来新的构建产物继续被纳入版本控制，不会自动移除已经被 Git 跟踪的文件。
- `lexicon.cpp` 的前端分词已额外覆盖常见中文、俄语、法语等标点，避免词典命中被尾部标点破坏。
