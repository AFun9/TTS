package com.k2fsa.sherpa.tts.repository

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.tts.data.FrontendMode
import com.k2fsa.sherpa.tts.data.GeneratedAudio
import com.k2fsa.sherpa.tts.data.TTSConfig
import com.k2fsa.sherpa.tts.engine.TTSEngine
import com.k2fsa.sherpa.tts.util.EspeakDataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本项目 TTS 仓库：使用本项目的 TTSEngine（JNI）生成语音。
 * espeak-ng-data 路径写死在应用内，由 [EspeakDataHelper] 从 assets 解压到 filesDir 后使用。
 */
class TTSRepository(
    private val context: Context,
    private val outputDir: File
) {
    companion object {
        private const val TAG = "SherpaTtsRepo"
    }

    private var engine: TTSEngine? = null
    private var currentConfig: TTSConfig? = null

    /** 应用内固定的 espeak-ng-data 路径（与 sherpa-onnx --vits-data-dir 对应）。 */
    private val espeakDataDir: String
        get() = EspeakDataHelper.ensure(context)

    /**
     * 使用新配置创建引擎；dataDir 固定为应用内 espeak-ng-data 路径。
     */
    @Synchronized
    fun getOrCreateEngine(config: TTSConfig): Result<TTSEngine> {
        val fullConfig = config.copy(
            dataDir = if (config.dataDir.isBlank()) espeakDataDir else config.dataDir,
            voice = if (config.voice.isBlank()) "ru" else config.voice
        )
        if (fullConfig.frontendMode == FrontendMode.EspeakOnly && fullConfig.dataDir.isBlank()) {
            return Result.failure(
                IllegalStateException("espeakOnly 模式需要可用的 dataDir（espeak-ng-data）")
            )
        }
        Log.i(
            TAG,
            "createEngine: mode=${fullConfig.frontendMode} voice=${fullConfig.voice} dataDir=${fullConfig.dataDir}"
        )
        return try {
            if (engine == null || currentConfig != fullConfig) {
                engine?.release()
                engine = TTSEngine(fullConfig)
                currentConfig = fullConfig
            }
            Result.success(engine!!)
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(IllegalStateException("JNI 库未加载，请将 sherpa-tts-jni 的 .so 放入 jniLibs。", e))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun generateSpeech(config: TTSConfig, text: String, speed: Float): Result<GeneratedAudio> =
        withContext(Dispatchers.IO) {
            getOrCreateEngine(config).fold(
                onSuccess = { eng ->
                    try {
                        val wavFile = File(outputDir, "generated_${System.currentTimeMillis()}.wav")
                        val result = eng.generate(text, speed, wavFile.absolutePath)
                        Result.success(result)
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }

    @Synchronized
    fun release() {
        engine?.release()
        engine = null
        currentConfig = null
    }
}
