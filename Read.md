# Sherpa TTS Android（本项目实现）

一个 Android 端 TTS Demo，核心链路为：

`文本 -> 前端(lexicon/espeak) -> token ids -> ONNX VITS 推理 -> wav`

## 说明与边界

- 项目内为**自实现 JNI 与前端路由逻辑**，不直接链接 `sherpa-onnx` 运行库。
- ONNX 推理使用 `onnxruntime-android`（Gradle 自动下载）。
- 无 lexicon 时支持 `espeak-ng + piper-phonemize` 音素化。
- `models/espeak-ng-data` 会打包到 APK assets，运行时拷贝到应用私有目录后再给 native 使用。

## 当前能力

- 支持 `FrontendMode`：
  - `Auto`：先 lexicon/token，失败后转 espeak
  - `LexiconFirst`：只走 lexicon 优先逻辑
  - `EspeakOnly`：直接走 espeak 音素化
- 支持在 UI 侧切换 `model.onnx` 与 `tokens.txt`（用于不同语言模型）。
- 内置细粒度错误码映射，便于定位是 token miss、espeak 初始化失败、推理失败还是写 wav 失败。

## 环境要求

- JDK 17（必须）
- Android SDK（建议 API 34）
- NDK `27.0.12077973`
- CMake `3.22.1`
- Linux/macOS/Windows 均可（命令示例以 Linux 为主）

## 目录约定

```text
sherpa-tts-wrapper/
├── android/
│   ├── app/
│   │   └── src/main/
│   │       ├── cpp/                   # JNI + C++ 前端 + ONNX 推理
│   │       └── java/...               # Kotlin UI/Repository/Engine
│   ├── gradle.properties
│   └── third_party/                   # 固化后的本地依赖（离线构建用）
└── models/
    └── espeak-ng-data/                # 运行时数据，打包进 assets
```

## 快速开始（推荐）

### 1) 打开工程

- Android Studio / Cursor 都要直接打开 `sherpa-tts-wrapper/android` 作为项目根目录。

### 2) 检查 `gradle.properties`

确保至少有这些关键项：

```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
sherpa.tts.enableEspeakNg=true
sherpa.tts.abis=arm64-v8a
onnxruntime.include.root=../build/onnxruntime
sherpa.tts.espeakNg.sourceDir=../third_party/espeak-ng
sherpa.tts.piperPhonemize.sourceDir=../third_party/piper-phonemize
```

### 3) 构建

```bash
cd /path/to/sherpa-tts-wrapper/android
./gradlew :app:assembleDebug
```

## 离线/稳定构建（重点）

如果你已经成功构建过一次，执行：

```bash
cd /path/to/sherpa-tts-wrapper/android
./gradlew :app:freezePhonemizeDeps
```

此任务会把 `app/.cxx/.../_deps` 中已下载可用的：

- `espeak-ng`
- `piper-phonemize`

复制到稳定目录：

- `android/third_party/espeak-ng`
- `android/third_party/piper-phonemize`

之后正常 `assembleDebug` 就不再依赖 `.cxx` 临时哈希目录，离线更稳定。

## 模型与文件准备

至少需要：

- `model.onnx`
- `tokens.txt`
- `models/espeak-ng-data/`（目录完整）
- `lexicon.txt`（可选）

`espeak-ng-data` 为运行时资源，不是 native 库源码，不能替代 `espeak-ng/piper-phonemize` 的 C/C++ 依赖。

## 常用命令

```bash
# Debug 包
./gradlew :app:assembleDebug

# 先清理再构建
./gradlew clean :app:assembleDebug

# 固化本地依赖（推荐在首个可用构建后执行）
./gradlew :app:freezePhonemizeDeps
```

## 常见问题

### 1) `Android Gradle plugin requires Java 17`

- 原因：JDK 版本不对。
- 处理：安装 JDK17，并在 `gradle.properties` 设置 `org.gradle.java.home`。

### 2) `FetchContent` 下载 espeak/piper 失败（SSL/proxy）

- 处理 A：只构建 `arm64-v8a`（已默认）。
- 处理 B：使用 `third_party` 本地源码（推荐）。
- 处理 C：先在网络好的环境成功构建一次，再执行 `freezePhonemizeDeps` 固化。

### 3) `.cxx` 路径失效导致本地源码找不到

- 不要长期依赖 `.cxx/.../_deps` 路径。
- 用 `freezePhonemizeDeps` 固化后，改用 `../third_party/...`。

### 4) espeak 模式无输出

- 检查 `models/espeak-ng-data` 是否完整。
- 检查 `tokens.txt` 是否包含 `^`、`_`、`$` 以及目标音素字符。
- 检查 `voice` 与文本语言是否匹配（如 `ru`、`en-us`）。

## 测试建议

- 先做端到端冒烟：
  - `FrontendMode.LexiconFirst`
  - `FrontendMode.EspeakOnly`
- 再跑 instrumentation：
  - `android/app/src/androidTest/.../TtsRuEspeakSmokeTest.kt`

## 许可证

遵循仓库根目录中的许可证说明。# TTS
