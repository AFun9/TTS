# Sherpa TTS Android - 设计文档

## 1. 系统架构

### 1.1 总体架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   User Interface│────│   Business Logic │────│   JNI Layer     │
│   (Activities/  │    │   (ViewModels/   │    │   (C++ Code)    │
│    Fragments)   │    │    Repositories) │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────────┐
                    │  Sherpa-ONNX Core  │
                    │                     │
                    │  ┌─────────────────┐ │
                    │  │   VITS Model    │ │
                    │  │                 │ │
                    │  │  ┌─────────────┐ │ │
                    │  │  │  Lexicon    │ │ │
                    │  │  │  First      │ │ │
                    │  │  └─────────────┘ │ │
                    │  └─────────────────┘ │
                    │  │  ┌─────────────┐ │ │
                    │  │  │  Phonemizer │ │ │
                    │  │  └─────────────┘ │ │
                    │  └─────────────────┘ │
                    └─────────────────────┘
```

### 1.2 组件关系

- **User Interface**: Android UI 层，包含 Activity、Fragment、自定义 View
- **Business Logic**: 业务逻辑层，包含 ViewModel、Repository、UseCase
- **JNI Layer**: Java Native Interface，连接 Java 和 C++ 代码
- **Sherpa-ONNX**: 底层 TTS 引擎，提供模型推理能力

### 1.3 Android 架构模式

采用 MVVM (Model-View-ViewModel) 架构模式：

- **Model**: 数据模型和业务逻辑
- **View**: UI 组件 (Activity, Fragment, View)
- **ViewModel**: 连接 Model 和 View，处理 UI 逻辑

### 1.4 数据流

```
用户输入 → View → ViewModel → Repository → JNI → Sherpa-ONNX → JNI → Repository → ViewModel → View 更新
```

## 2. Android 组件设计

## 2. Android 组件设计

### 2.1 数据模型 (Model Layer)

#### TTS 配置数据类

```kotlin
data class TTSConfig(
    val modelPath: String,
    val tokensPath: String,
    val dataDir: String,
    val lexiconPath: String? = null,
    val speakerId: Int = 0,
    val speed: Float = 1.0f,
    val debug: Boolean = false
)

data class ModelInfo(
    val sampleRate: Int,
    val numSpeakers: Int,
    val language: String,
    val modelSize: Long
)

data class LexiconEntry(
    val word: String,
    val phonemes: List<String>
)
```

#### 本地存储配置

```kotlin
// SharedPreferences 存储
class TTSPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

    fun saveConfig(config: TTSConfig)
    fun loadConfig(): TTSConfig?
    fun saveLastUsedModel(modelPath: String)
    fun getLastUsedModel(): String?
}
```

### 2.2 业务逻辑层 (Business Logic Layer)

#### Repository 层

```kotlin
class TTSRepository(private val context: Context) {

    private var ttsEngine: SherpaTtsEngine? = null

    suspend fun initialize(config: TTSConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                ttsEngine = SherpaTtsEngine(config)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateSpeech(text: String): Result<AudioData> {
        return withContext(Dispatchers.IO) {
            try {
                val audio = ttsEngine?.generate(text)
                    ?: throw IllegalStateException("TTS engine not initialized")
                Result.success(audio)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun release() {
        ttsEngine?.release()
        ttsEngine = null
    }
}

class FileRepository(private val context: Context) {

    suspend fun importModel(uri: Uri): Result<String> {
        // 从 URI 复制文件到应用私有目录
    }

    suspend fun importLexicon(uri: Uri): Result<String> {
        // 解析和验证词典文件
    }

    suspend fun exportAudio(audioData: AudioData, filename: String): Result<Uri> {
        // 保存音频到外部存储
    }

    fun getModelList(): List<ModelInfo> {
        // 扫描应用私有目录中的模型文件
    }
}
```

#### UseCase 层

```kotlin
class GenerateSpeechUseCase(
    private val ttsRepository: TTSRepository,
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(text: String): Result<SpeechResult> {
        return ttsRepository.generateSpeech(text).map { audioData ->
            SpeechResult(audioData, System.currentTimeMillis())
        }
    }
}

class ImportModelUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(uri: Uri): Result<ModelInfo> {
        return fileRepository.importModel(uri).flatMap { modelPath ->
            validateAndExtractModelInfo(modelPath)
        }
    }
}

class ManageLexiconUseCase(private val fileRepository: FileRepository) {

    suspend fun loadLexicon(path: String): Result<List<LexiconEntry>> {
        // 加载词典文件
    }

    suspend fun saveLexicon(entries: List<LexiconEntry>, path: String): Result<Unit> {
        // 保存词典文件
    }

    fun validateLexiconEntry(entry: LexiconEntry): Boolean {
        // 验证词典条目格式
    }
}
```

### 2.3 ViewModel 层

#### 主界面 ViewModel

```kotlin
class MainViewModel(
    private val generateSpeechUseCase: GenerateSpeechUseCase,
    private val preferences: TTSPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _generatedAudio = MutableSharedFlow<AudioData>()
    val generatedAudio: SharedFlow<AudioData> = _generatedAudio

    fun generateSpeech(text: String) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            generateSpeechUseCase(text)
                .onSuccess { result ->
                    _uiState.value = MainUiState.Success
                    _generatedAudio.emit(result.audioData)
                }
                .onFailure { error ->
                    _uiState.value = MainUiState.Error(error.message ?: "Unknown error")
                }
        }
    }

    fun saveAudio(audioData: AudioData, filename: String) {
        // 保存音频逻辑
    }
}

sealed class MainUiState {
    object Idle : MainUiState()
    object Loading : MainUiState()
    data class Success(val message: String = "Generated successfully") : MainUiState()
    data class Error(val message: String) : MainUiState()
}
```

#### 词典编辑 ViewModel

```kotlin
class LexiconViewModel(
    private val manageLexiconUseCase: ManageLexiconUseCase
) : ViewModel() {

    private val _lexiconEntries = MutableStateFlow<List<LexiconEntry>>(emptyList())
    val lexiconEntries: StateFlow<List<LexiconEntry>> = _lexiconEntries

    private val _isEditing = MutableStateFlow<Boolean>(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    fun loadLexicon(path: String) {
        viewModelScope.launch {
            manageLexiconUseCase.loadLexicon(path)
                .onSuccess { entries ->
                    _lexiconEntries.value = entries
                }
                .onFailure { error ->
                    // 处理错误
                }
        }
    }

    fun addEntry(entry: LexiconEntry) {
        if (manageLexiconUseCase.validateLexiconEntry(entry)) {
            val current = _lexiconEntries.value.toMutableList()
            current.add(entry)
            _lexiconEntries.value = current
        }
    }

    fun updateEntry(index: Int, entry: LexiconEntry) {
        if (manageLexiconUseCase.validateLexiconEntry(entry)) {
            val current = _lexiconEntries.value.toMutableList()
            current[index] = entry
            _lexiconEntries.value = current
        }
    }

    fun deleteEntry(index: Int) {
        val current = _lexiconEntries.value.toMutableList()
        current.removeAt(index)
        _lexiconEntries.value = current
    }

    fun saveLexicon(path: String) {
        viewModelScope.launch {
            manageLexiconUseCase.saveLexicon(_lexiconEntries.value, path)
                .onSuccess {
                    _isEditing.value = false
                }
                .onFailure { error ->
                    // 处理错误
                }
        }
    }
}
```

### 2.4 JNI 层设计

#### JNI 接口定义

```cpp
// native_tts.h
class SherpaTtsEngine {
public:
    explicit SherpaTtsEngine(const TTSConfig& config);
    ~SherpaTtsEngine();

    std::unique_ptr<AudioData> generate(const std::string& text);
    void release();

private:
    TTSConfig config_;
    std::unique_ptr<OfflineTts> tts_;
};

// JNI 方法声明
extern "C" {
JNIEXPORT jlong JNICALL Java_com_k2fsa_sherpa_tts_TTSEngine_nativeCreate(
    JNIEnv* env, jobject thiz, jobject config);

JNIEXPORT jbyteArray JNICALL Java_com_k2fsa_sherpa_tts_TTSEngine_nativeGenerate(
    JNIEnv* env, jobject thiz, jlong handle, jstring text);

JNIEXPORT void JNICALL Java_com_k2fsa_sherpa_tts_TTSEngine_nativeRelease(
    JNIEnv* env, jobject thiz, jlong handle);
}
```

#### Java JNI 包装类

```kotlin
class TTSEngine(private val config: TTSConfig) {

    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeCreate(config)
    }

    fun generate(text: String): AudioData {
        val audioBytes = nativeGenerate(nativeHandle, text)
        return AudioData.fromBytes(audioBytes)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun nativeCreate(config: TTSConfig): Long
    private external fun nativeGenerate(handle: Long, text: String): ByteArray
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("sherpa-tts-jni")
        }
    }
}
```

## 3. 用户界面设计

### 3.1 主界面 (MainActivity)

```
┌─────────────────────────────────────┐
│           Sherpa TTS                │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Text Input Area                 │ │
│ │                                 │ │
│ │ [Multi-line text input]        │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [🎤 Generate Speech] [⏹️ Stop]     │
├─────────────────────────────────────┤
│ Speed: [-----○--------] 1.0x       │
│ Volume: [-----○--------] 80%       │
├─────────────────────────────────────┤
│ [📁 Import Text] [💾 Export Audio] │
├─────────────────────────────────────┤
│ Progress: [██████████████████] 100% │
└─────────────────────────────────────┘
```

#### 界面组件说明

- **文本输入区域**: 支持多行文本输入，自动检测语言
- **控制按钮**: 生成语音、停止播放、导入文本、导出音频
- **参数调节**: 滑块调节语速和音量
- **进度显示**: 生成和播放进度条

### 3.2 模型管理界面 (ModelManagerActivity)

```
┌─────────────────────────────────────┐
│         Model Manager               │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Available Models                │ │
│ │ ├─────────────────────────────┤ │ │
│ │ │ Russian VITS (22050Hz)      │ │ │
│ │ │ English VITS (24000Hz)      │ │ │
│ │ └─────────────────────────────┘ │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [➕ Import Model] [⚙️ Settings]     │
├─────────────────────────────────────┤
│ Current: Russian VITS              │
└─────────────────────────────────────┘
```

### 3.3 词典编辑器界面 (LexiconEditorActivity)

```
┌─────────────────────────────────────┐
│       Lexicon Editor                │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Search: [___________] [🔍]      │ │
│ ├─────────────────────────────────┤ │
│ │ hello → h ɛ l o                │ │
│ │ world → w ɜ r l d              │ │
│ │ [Add new entry...]             │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [➕ Add] [✏️ Edit] [🗑️ Delete]      │
├─────────────────────────────────────┤
│ [💾 Save] [📁 Import] [📤 Export]  │
└─────────────────────────────────────┘
```

#### 词典编辑功能

- **条目管理**: 添加、编辑、删除词典条目
- **搜索功能**: 按单词搜索条目
- **导入导出**: 支持词典文件的导入导出
- **验证提示**: 实时验证音素格式是否正确

### 3.4 设置界面 (SettingsActivity)

```
┌─────────────────────────────────────┐
│           Settings                  │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Audio Settings                  │ │
│ │ ├─────────────────────────────┤ │ │
│ │ │ Sample Rate: 22050 Hz       │ │ │
│ │ │ Format: WAV                  │ │ │
│ │ └─────────────────────────────┘ │ │
│ ├─────────────────────────────────┤ │
│ │ │ Performance Settings         │ │
│ │ │ ├─────────────────────────────┤ │ │
│ │ │ │ Num Threads: 2             │ │ │
│ │ │ │ Enable Debug: OFF         │ │ │
│ │ └─────────────────────────────┘ │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [💾 Save Settings] [🔄 Reset]       │
└─────────────────────────────────────┘
```

## 4. 文件和数据管理

### 4.1 文件存储结构

```
/data/data/com.k2fsa.sherpa.tts/
├── files/
│   ├── models/
│   │   ├── russian_vits.onnx
│   │   ├── english_vits.onnx
│   │   └── tokens.txt
│   ├── lexicons/
│   │   ├── russian_to_english.txt
│   │   └── custom_lexicon.txt
│   └── temp/
│       └── generated_audio.wav
├── shared_prefs/
│   └── tts_preferences.xml
└── databases/
    └── tts_history.db
```

### 4.2 数据库设计

#### 历史记录表

```sql
CREATE TABLE speech_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    audio_path TEXT,
    model_name TEXT,
    lexicon_name TEXT,
    created_at INTEGER,
    duration REAL
);
```

#### 模型信息表

```sql
CREATE TABLE models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    path TEXT NOT NULL,
    sample_rate INTEGER,
    num_speakers INTEGER,
    language TEXT,
    file_size INTEGER,
    created_at INTEGER
);
```

## 5. 权限和安全

### 5.1 Android 权限

#### 必需权限

```xml
<!-- 读取外部存储（导入模型/词典/文本） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- 写入外部存储（导出音频） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 音频播放 -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

#### 运行时权限请求

```kotlin
class PermissionManager(private val activity: Activity) {

    fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), REQUEST_STORAGE)
    }
}
```

### 5.2 数据安全

- **敏感数据**: 不存储用户文本内容（除非用户明确保存）
- **文件访问**: 严格限制在应用私有目录和用户选择的外部目录
- **网络安全**: 不进行网络通信，无数据泄漏风险
- **权限最小化**: 仅请求必需权限，按需申请

## 6. 错误处理和异常管理

### 6.1 异常类型

```kotlin
sealed class TTSException : Exception() {
    data class ModelLoadException(val modelPath: String, override val message: String)
        : TTSException()

    data class LexiconParseException(val line: Int, override val message: String)
        : TTSException()

    data class InferenceException(override val message: String)
        : TTSException()

    data class AudioPlaybackException(override val message: String)
        : TTSException()
}
```

### 6.2 错误处理策略

#### UI 层错误处理

```kotlin
class ErrorHandler(private val context: Context) {

    fun handleError(error: Throwable, uiScope: CoroutineScope) {
        val message = when (error) {
            is TTSException.ModelLoadException ->
                "Failed to load model: ${error.modelPath}"
            is TTSException.LexiconParseException ->
                "Invalid lexicon format at line ${error.line}"
            else -> "An error occurred: ${error.localizedMessage}"
        }

        uiScope.launch(Dispatchers.Main) {
            showErrorDialog(message)
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
```

#### 业务层错误处理

```kotlin
suspend fun <T> safeTTSOperation(
    block: suspend () -> Result<T>
): Result<T> = try {
    block()
} catch (e: IOException) {
    Result.failure(TTSException.FileAccessException("File access failed: ${e.message}"))
} catch (e: OutOfMemoryError) {
    Result.failure(TTSException.MemoryException("Not enough memory"))
} catch (e: Exception) {
    Result.failure(TTSException.UnknownException("Unknown error: ${e.message}"))
}
```

## 7. 性能优化

### 7.1 内存管理

```kotlin
class MemoryManager {

    private val modelCache = LruCache<String, SherpaTtsEngine>(maxSize = 2)

    fun getOrCreateEngine(config: TTSConfig): SherpaTtsEngine {
        val key = config.modelPath
        return modelCache.get(key) ?: createAndCacheEngine(key, config)
    }

    private fun createAndCacheEngine(key: String, config: TTSConfig): SherpaTtsEngine {
        // 检查内存使用情况
        if (getAvailableMemory() < MIN_MEMORY_THRESHOLD) {
            // 清理缓存
            modelCache.evictAll()
        }

        val engine = SherpaTtsEngine(config)
        modelCache.put(key, engine)
        return engine
    }

    private fun getAvailableMemory(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        getSystemService(Context.ACTIVITY_SERVICE)
            .getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    companion object {
        private const val MIN_MEMORY_THRESHOLD = 500 * 1024 * 1024L // 500MB
    }
}
```

### 7.2 后台处理

```kotlin
class TTSWorker(context: Context, workerParameters: WorkerParameters)
    : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val text = inputData.getString("text") ?: return@withContext Result.failure()
                val configJson = inputData.getString("config") ?: return@withContext Result.failure()

                val config = Json.decodeFromString<TTSConfig>(configJson)
                val engine = SherpaTtsEngine(config)

                val audioData = engine.generate(text)

                // 保存到缓存目录
                val cacheFile = File(context.cacheDir, "generated_audio.wav")
                audioData.saveToFile(cacheFile)

                engine.release()

                Result.success(workDataOf("audio_path" to cacheFile.absolutePath))
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}
```

## 8. 测试策略

### 8.1 单元测试

```kotlin
class TTSRepositoryTest {

    @Test
    fun `generate speech with valid text returns audio data`() = runTest {
        // Given
        val repository = TTSRepository(mockContext)
        val config = createTestConfig()

        // When
        repository.initialize(config)
        val result = repository.generateSpeech("hello")

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `generate speech with empty text returns failure`() = runTest {
        // Given
        val repository = TTSRepository(mockContext)

        // When
        val result = repository.generateSpeech("")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
```

### 8.2 集成测试

```kotlin
class MainViewModelIntegrationTest {

    @Test
    fun `generate speech updates UI state correctly`() = runTest {
        // Given
        val viewModel = MainViewModel(
            generateSpeechUseCase = FakeGenerateSpeechUseCase(),
            preferences = mockPreferences
        )

        // When
        viewModel.generateSpeech("test text")

        // Then
        assertEquals(MainUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MainUiState.Success)
    }
}
```

### 8.3 UI 测试

```kotlin
class MainActivityTest {

    @Test
    fun `click generate button shows loading state`() {
        // Given
        composeTestRule.setContent {
            MainScreen(viewModel = fakeViewModel)
        }

        // When
        composeTestRule.onNodeWithText("Generate Speech").performClick()

        // Then
        composeTestRule.onNodeWithText("Generating...").assertIsDisplayed()
    }
}
```

## 9. 部署和分发

### 9.1 构建配置

#### build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("kotlin-android")
    kotlin("kapt")
}

android {
    namespace = "com.k2fsa.sherpa.tts"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.k2fsa.sherpa.tts"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")

    // 协程和Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 文件处理
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 音频播放
    implementation("androidx.media:media:1.6.0")

    // 数据库
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}
```

### 9.2 CMake 配置

#### CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.22.1)

project("sherpa-tts-jni")

# 设置 Sherpa-ONNX
set(SHERPA_ONNX_VERSION 1.12.0)
set(SHERPA_ONNX_URL "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_ONNX_VERSION}/sherpa-onnx-${SHERPA_ONNX_VERSION}-android.tar.bz2")

# 下载和解压
include(FetchContent)
FetchContent_Declare(
  sherpa_onnx
  URL ${SHERPA_ONNX_URL}
)
FetchContent_MakeAvailable(sherpa_onnx)

# JNI 库
add_library(sherpa-tts-jni SHARED
    sherpa_tts_jni.cpp
    tts_engine.cpp
)

target_include_directories(sherpa-tts-jni PRIVATE
    ${sherpa_onnx_SOURCE_DIR}/include
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(sherpa-tts-jni
    sherpa-onnx-core
    ${JNI_LIBRARIES}
)
```

### 9.3 APK 大小优化

- **ABI 分包**: 只包含 armeabi-v7a 和 arm64-v8a
- **资源压缩**: 使用 WebP 格式图标
- **代码混淆**: 启用 ProGuard
- **动态加载**: 模型文件不打包在 APK 中

### 9.4 Google Play 发布

#### 应用信息
- **应用名称**: Sherpa TTS
- **包名**: com.k2fsa.sherpa.tts
- **版本**: 1.0.0
- **最低 Android 版本**: 7.0 (API 24)

#### 商店描述
```
Sherpa TTS 是一款强大的跨语言语音合成应用，支持自定义词典实现灵活的发音控制。

主要特性：
• 支持多种语言的 TTS 模型
• 自定义词典实现跨语言发音
• 实时语音播放和音频导出
• 直观的词典编辑界面
• 轻量级设计，保护隐私
```
```
```

#### 职责分离

- **TTSConfig**: 配置管理，参数验证
- **TTSEngine**: 核心业务逻辑，模型管理和推理
- **文件管理**: 模型加载、词典解析、音频输出

### 2.2 Python API (sherpa_tts_wrapper/__init__.py)

#### 接口设计

```python
from .core import TTS, TTSConfig

def create_tts(config_path: str) -> TTS:
    """工厂函数：从配置文件创建 TTS 实例"""
    pass

class TTS:
    """用户友好的 TTS 接口"""

    def __init__(self, config: TTSConfig):
        self.engine = TTSEngine(config)

    @classmethod
    def from_config_file(cls, config_path: str) -> 'TTS':
        """从 JSON 配置文件创建实例"""
        pass

    def generate(self, text: str, output_path: Optional[str] = None) -> GeneratedAudio:
        """生成语音，可选保存到文件"""
        pass

    def generate_batch(self, texts: List[str]) -> List[GeneratedAudio]:
        """批量生成语音"""
        pass
```

### 2.3 CLI Interface (scripts/sherpa-tts-wrapper)

#### 命令行设计

```bash
# 基本用法
sherpa-tts-wrapper --config config.json --text "Hello world" --output output.wav

# 批量处理
sherpa-tts-wrapper --config config.json --input texts.txt --output-dir outputs/

# 交互模式
sherpa-tts-wrapper --config config.json --interactive
```

#### 参数设计

| 参数 | 类型 | 必需 | 描述 |
|------|------|------|------|
| --config | str | 是 | JSON 配置文件路径 |
| --text | str | 否 | 要转换的文本 |
| --input | str | 否 | 输入文本文件路径 |
| --output | str | 否 | 输出音频文件路径 |
| --output-dir | str | 否 | 输出目录（批量模式） |
| --interactive | flag | 否 | 进入交互模式 |
| --verbose | flag | 否 | 详细输出 |

## 3. 数据流设计

### 3.1 单句 TTS 流程

```
输入文本 → 文本预处理 → 词典查找 → 音素转换 → 模型推理 → 音频生成 → 输出文件
    ↓           ↓           ↓           ↓           ↓           ↓           ↓
"hello" → "hello" → 查词典 → [h,ɛ,l,o] → token IDs → 音频数据 → output.wav
```

### 3.2 词典处理流程

```
词典文件 → 解析词典 → 建立映射表 → 文本分词 → 词典查找 → 音素序列
    ↓           ↓           ↓           ↓           ↓           ↓
lexicon.txt → 解析每行 → word→phonemes → split words → 匹配词条 → 合并音素
```

### 3.3 配置加载流程

```
JSON 文件 → 解析配置 → 验证参数 → 创建配置对象 → 初始化引擎
    ↓           ↓           ↓           ↓           ↓
config.json → 读取字段 → 检查文件存在 → TTSConfig() → TTSEngine()
```

## 4. 接口设计

### 4.1 配置文件格式

```json
{
  "model": {
    "model_path": "path/to/model.onnx",
    "tokens_path": "path/to/tokens.txt",
    "lexicon_path": "path/to/lexicon.txt",
    "data_dir": "path/to/espeak-ng-data"
  },
  "runtime": {
    "speaker_id": 0,
    "speed": 1.0,
    "debug": false
  },
  "output": {
    "default_format": "wav",
    "sample_rate": 22050
  }
}
```

### 4.2 Python API 示例

```python
from sherpa_tts_wrapper import TTS

# 方法1：使用配置文件
tts = TTS.from_config_file("config.json")

# 方法2：直接创建配置
from sherpa_tts_wrapper import TTSConfig
config = TTSConfig(
    model_path="model.onnx",
    tokens_path="tokens.txt",
    data_dir="espeak-ng-data",
    lexicon_path="lexicon.txt"
)
tts = TTS(config)

# 生成语音
audio = tts.generate("Hello world")
audio.save("output.wav")

# 批量生成
audios = tts.generate_batch(["Hello", "World"])
for i, audio in enumerate(audios):
    audio.save(f"output_{i}.wav")
```

### 4.3 命令行示例

```bash
# 单句转换
sherpa-tts-wrapper --config config.json --text "Hello world" --output hello.wav

# 从文件读取文本
sherpa-tts-wrapper --config config.json --input input.txt --output-dir outputs/

# 交互模式
sherpa-tts-wrapper --config config.json --interactive
# > Hello world
# > Generating audio...
# > Saved to output.wav
```

## 5. 错误处理设计

### 5.1 异常层次

```python
class TTSException(Exception):
    """TTS 基础异常"""
    pass

class ConfigError(TTSException):
    """配置相关错误"""
    pass

class ModelError(TTSException):
    """模型加载相关错误"""
    pass

class InferenceError(TTSException):
    """推理相关错误"""
    pass

class AudioError(TTSException):
    """音频处理相关错误"""
    pass
```

### 5.2 错误场景处理

#### 配置错误
- **场景**: 模型文件不存在
- **处理**: 抛出 `ConfigError`，提供文件路径信息
- **恢复**: 用户检查并修正文件路径

#### 词典错误
- **场景**: 词典文件格式错误
- **处理**: 记录警告，跳过无效行，继续处理
- **恢复**: 自动跳过，继续使用默认 phonemizer

#### 推理错误
- **场景**: 模型输入格式不匹配
- **处理**: 抛出 `InferenceError`，记录详细错误信息
- **恢复**: 检查模型和输入文本兼容性

### 5.3 日志设计

#### 日志级别
- **DEBUG**: 详细的调试信息，词典加载、推理过程
- **INFO**: 重要事件，模型加载成功、文件生成
- **WARNING**: 警告信息，词典格式问题、参数异常
- **ERROR**: 错误信息，失败原因和建议

#### 日志格式
```
[2024-01-16 20:02:15] INFO: Model loaded successfully: model.onnx
[2024-01-16 20:02:15] DEBUG: Lexicon loaded: 150 entries
[2024-01-16 20:02:15] INFO: Generating audio for: "Hello world"
[2024-01-16 20:02:16] INFO: Audio saved to: output.wav
```

## 6. 配置设计

### 6.1 配置验证

```python
def validate_config(config: dict) -> List[str]:
    """验证配置，返回错误列表"""
    errors = []

    # 检查必需文件存在
    required_files = [
        config.get('model', {}).get('model_path'),
        config.get('model', {}).get('tokens_path'),
        config.get('model', {}).get('data_dir')
    ]

    for file_path in required_files:
        if not file_path or not os.path.exists(file_path):
            errors.append(f"Required file not found: {file_path}")

    # 检查可选文件
    lexicon_path = config.get('model', {}).get('lexicon_path')
    if lexicon_path and not os.path.exists(lexicon_path):
        errors.append(f"Lexicon file not found: {lexicon_path}")

    # 检查参数范围
    speed = config.get('runtime', {}).get('speed', 1.0)
    if not 0.1 <= speed <= 3.0:
        errors.append(f"Speed must be between 0.1 and 3.0, got: {speed}")

    return errors
```

### 6.2 环境变量覆盖

```python
def load_config_with_env_override(config_path: str) -> dict:
    """加载配置，支持环境变量覆盖"""
    with open(config_path, 'r', encoding='utf-8') as f:
        config = json.load(f)

    # 环境变量覆盖
    env_overrides = {
        'SHERPA_TTS_MODEL_PATH': ['model', 'model_path'],
        'SHERPA_TTS_TOKENS_PATH': ['model', 'tokens_path'],
        'SHERPA_TTS_LEXICON_PATH': ['model', 'lexicon_path'],
        'SHERPA_TTS_DATA_DIR': ['model', 'data_dir'],
        'SHERPA_TTS_SPEAKER_ID': ['runtime', 'speaker_id'],
        'SHERPA_TTS_SPEED': ['runtime', 'speed'],
        'SHERPA_TTS_DEBUG': ['runtime', 'debug']
    }

    for env_var, path in env_overrides.items():
        if env_var in os.environ:
            set_nested_value(config, path, os.environ[env_var])

    return config
```

## 7. 测试设计

### 7.1 单元测试

```python
# tests/test_core.py
def test_lexicon_loading():
    """测试词典加载功能"""
    pass

def test_text_to_phonemes():
    """测试文本到音素转换"""
    pass

def test_model_inference():
    """测试模型推理"""
    pass
```

### 7.2 集成测试

```python
# tests/test_integration.py
def test_full_tts_pipeline():
    """测试完整 TTS 流程"""
    pass

def test_lexicon_first_behavior():
    """测试 lexicon-first 行为"""
    pass
```

### 7.3 性能测试

```python
# tests/test_performance.py
def test_inference_speed():
    """测试推理速度"""
    pass

def test_memory_usage():
    """测试内存使用"""
    pass
```

## 8. 部署设计

### 8.1 包结构

```
sherpa-tts-wrapper/
├── sherpa_tts_wrapper/
│   ├── __init__.py
│   ├── core.py
│   ├── config.py
│   └── utils.py
├── scripts/
│   └── sherpa-tts-wrapper
├── tests/
│   ├── test_core.py
│   ├── test_integration.py
│   └── test_performance.py
├── docs/
│   ├── requirements.md
│   ├── design.md
│   └── user_guide.md
├── examples/
│   ├── config.json
│   └── usage.py
├── setup.py
├── requirements.txt
└── README.md
```

### 8.2 依赖管理

```python
# requirements.txt
sherpa-onnx>=1.12.0
numpy>=1.21.0
click>=8.0.0
pydantic>=1.8.0
```

### 8.3 安装脚本

```python
# setup.py
from setuptools import setup, find_packages

setup(
    name="sherpa-tts-wrapper",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "sherpa-onnx>=1.12.0",
        "numpy>=1.21.0",
        "click>=8.0.0",
        "pydantic>=1.8.0",
    ],
    entry_points={
        "console_scripts": [
            "sherpa-tts-wrapper=sherpa_tts_wrapper.cli:main",
        ],
    },
)
```

## 9. 安全考虑

### 9.1 输入验证
- 文本长度限制（防止内存溢出）
- 文件路径安全检查（防止路径遍历）
- 参数范围验证

### 9.2 资源管理
- 模型文件访问权限控制
- 临时文件清理
- 内存使用监控

### 9.3 日志安全
- 敏感信息过滤
- 日志文件权限控制
- 日志轮转策略