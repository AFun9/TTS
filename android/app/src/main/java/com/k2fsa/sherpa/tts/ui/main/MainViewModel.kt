package com.k2fsa.sherpa.tts.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2fsa.sherpa.tts.data.FrontendMode
import com.k2fsa.sherpa.tts.data.TTSConfig
import com.k2fsa.sherpa.tts.repository.TTSRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面只消费这一组高层状态，不直接感知底层 JNI/Repository 细节。
 */
sealed class MainUiState {
    data object Idle : MainUiState()
    data object Loading : MainUiState()
    data class Success(val message: String = "生成成功") : MainUiState()
    data class Error(val message: String) : MainUiState()
    data object Playing : MainUiState()
    data object Stopped : MainUiState()
}

/**
 * 主界面状态协调者。
 *
 * 它维护模型路径、生成进度和错误信息，并把成功生成的 WAV 路径下发给
 * Activity；播放、文件管理等副作用则交给 UI 层的 handler。
 */
class MainViewModel(
    private val ttsRepository: TTSRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _modelPath = MutableStateFlow("")
    val modelPath: StateFlow<String> = _modelPath.asStateFlow()

    private val _tokensPath = MutableStateFlow("")
    val tokensPath: StateFlow<String> = _tokensPath.asStateFlow()

    private val _lexiconPath = MutableStateFlow("")
    val lexiconPath: StateFlow<String> = _lexiconPath.asStateFlow()

    /** 生成成功后下发 WAV 路径，由 Activity 播放。 */
    private val _generatedWavPath = MutableSharedFlow<String>()
    val generatedWavPath: SharedFlow<String> = _generatedWavPath

    private var speed: Float = 1.0f
    private var volume: Int = 80
    private var frontendMode: FrontendMode = FrontendMode.Auto
    private var voice: String = "ru"
    private var generateJob: Job? = null
    private var progressJob: Job? = null

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2.0f)
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
    }

    fun setFrontendMode(mode: FrontendMode) {
        frontendMode = mode
    }

    fun setVoice(value: String) {
        voice = value.ifBlank { "ru" }
    }

    fun setModelPath(path: String) {
        _modelPath.value = path
    }

    fun setTokensPath(path: String) {
        _tokensPath.value = path
    }

    fun setLexiconPath(path: String) {
        _lexiconPath.value = path
    }

    fun isModelReady(): Boolean = _modelPath.value.isNotBlank() && _tokensPath.value.isNotBlank()

    fun initializeTTS() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Idle
        }
    }

    /**
     * 使用本项目的 TTSRepository（JNI）生成语音；成功则下发 WAV 路径供播放。
     */
    fun generateSpeech(text: String) {
        if (text.isBlank()) return
        generateJob?.cancel()
        progressJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            _progress.value = 0
            _lastError.value = ""
            startProgressTicker(text)
            val config = TTSConfig(
                modelPath = _modelPath.value,
                tokensPath = _tokensPath.value,
                lexiconPath = _lexiconPath.value,
                frontendMode = frontendMode,
                voice = voice,
                speed = speed
            )
            ttsRepository.generateSpeech(config, text, speed)
                .onSuccess { audio ->
                    progressJob?.cancel()
                    _progress.value = 100
                    _uiState.value = MainUiState.Success()
                    _generatedWavPath.emit(audio.wavFilePath)
                }
                .onFailure { e ->
                    progressJob?.cancel()
                    val msg = when {
                        e.message?.contains("nativeCreate failed") == true ->
                            "引擎创建失败：未链接 ONNX Runtime 或模型/tokens 路径有误。请用 logcat -s SherpaTts 查看原因。"
                        e.message?.contains("FRONTEND_LEXICON_MISS") == true ->
                            "前端失败：当前为 lexiconFirst 且词典未命中。请补充 lexicon 或改为 auto/espeakOnly。"
                        e.message?.contains("FRONTEND_ESPEAK_DATA_MISSING") == true ->
                            "前端失败：未找到 espeak-ng-data。请检查 assets 解压与 dataDir。"
                        e.message?.contains("FRONTEND_ESPEAK_DISABLED") == true ->
                            "前端失败：native 未启用 espeak（SHERPA_TTS_ENABLE_ESPEAK_NG）。"
                        e.message?.contains("FRONTEND_ESPEAK_INIT_FAILED") == true ->
                            "前端失败：espeak 初始化失败。请检查 dataDir 内容是否完整。"
                        e.message?.contains("FRONTEND_ESPEAK_PHONEME_EMPTY") == true ->
                            "前端失败：espeak 未产出音素。请检查文本/voice 设置。"
                        e.message?.contains("FRONTEND_TOKEN_MISS") == true ->
                            "前端失败：tokens 与音素集不匹配，未命中有效 token。"
                        e.message?.contains("ERR_VITS_RUN_EMPTY") == true ->
                            "推理失败：VITS 返回空音频。请检查模型、speaker_id 与输入 token。"
                        e.message?.contains("ERR_WRITE_WAVE") == true ->
                            "写文件失败：WAV 输出路径不可写。"
                        e.message?.contains("generate failed") == true ->
                            "合成失败：文本无法转成 token 或模型推理失败。请用 logcat -s SherpaTts 查看原因。"
                        e is UnsatisfiedLinkError ->
                            "未找到 native 库或未链接 ONNX Runtime，请按文档设置 ONNXRUNTIME_ROOT 后重新编译。"
                        else -> e.message
                    }
                    _lastError.value = msg ?: "生成失败"
                    _uiState.value = MainUiState.Error(msg ?: "生成失败")
                }
        }
    }

    private fun startProgressTicker(text: String) {
        val expectedMs = (text.length * 40L).coerceIn(600L, 6000L)
        val maxMs = 12000L
        progressJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= maxMs) {
                    _progress.value = 95
                    break
                }
                val pct = ((elapsed.toFloat() / expectedMs) * 90).toInt().coerceIn(0, 90)
                _progress.value = pct
                if (pct >= 90) break
                kotlinx.coroutines.delay(120)
            }
        }
    }

    fun setPlaying() {
        _uiState.value = MainUiState.Playing
    }

    fun stopPlayback() {
        _uiState.value = MainUiState.Stopped
    }

    override fun onCleared() {
        super.onCleared()
        ttsRepository.release()
    }
}
