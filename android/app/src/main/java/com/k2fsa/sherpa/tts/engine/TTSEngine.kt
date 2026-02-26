package com.k2fsa.sherpa.tts.engine

import com.k2fsa.sherpa.tts.data.GeneratedAudio
import com.k2fsa.sherpa.tts.data.TTSConfig

/**
 * 本项目的 TTS 引擎：通过 JNI 调用本仓库 C++ 实现（C++ 内对接 sherpa-onnx）。
 * 不依赖 sherpa-onnx 的 Java 代码。
 */
class TTSEngine(config: TTSConfig) {

    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeCreate(
            config.modelPath,
            config.tokensPath,
            config.dataDir,
            config.lexiconPath,
            config.frontendMode.ordinal,
            config.voice,
            config.speakerId,
            config.speed,
            config.numThreads,
            config.debug
        )
        if (nativeHandle == 0L) {
            throw IllegalStateException("TTSEngine nativeCreate failed. Check model/tokens paths and JNI lib.")
        }
    }

    /**
     * 生成语音，返回 WAV 文件路径（由 native 写入 outputDir 后返回绝对路径）。
     * @param text 输入文本
     * @param speed 语速
     * @param outputWavPath 输出 WAV 的完整路径，由调用方指定（如 filesDir/generated.wav）
     */
    fun generate(text: String, speed: Float, outputWavPath: String): GeneratedAudio {
        val sampleRate = nativeGenerate(nativeHandle, text, speed, outputWavPath)
        if (sampleRate <= 0) {
            throw IllegalStateException(explainGenerateError(sampleRate))
        }
        return GeneratedAudio(sampleRate = sampleRate, wavFilePath = outputWavPath)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun nativeCreate(
        modelPath: String,
        tokensPath: String,
        dataDir: String,
        lexiconPath: String,
        frontendMode: Int,
        voice: String,
        speakerId: Int,
        speed: Float,
        numThreads: Int,
        debug: Boolean
    ): Long

    private external fun nativeGenerate(
        handle: Long,
        text: String,
        speed: Float,
        outputWavPath: String
    ): Int

    private external fun nativeRelease(handle: Long)

    companion object {
        private const val FRONTEND_INVALID_ARGS = -1
        private const val FRONTEND_LEXICON_MISS = -2
        private const val FRONTEND_ESPEAK_DATA_MISSING = -3
        private const val FRONTEND_ESPEAK_DISABLED = -4
        private const val FRONTEND_ESPEAK_INIT_FAILED = -5
        private const val FRONTEND_ESPEAK_PHONEME_EMPTY = -6
        private const val FRONTEND_TOKEN_MISS = -7
        private const val ERR_INVALID_HANDLE = -100
        private const val ERR_INVALID_INPUT = -101
        private const val ERR_VITS_RUN_EMPTY = -102
        private const val ERR_WRITE_WAVE = -103

        private fun explainGenerateError(code: Int): String {
            val reason = when (code) {
                FRONTEND_INVALID_ARGS -> "FRONTEND_INVALID_ARGS"
                FRONTEND_LEXICON_MISS -> "FRONTEND_LEXICON_MISS"
                FRONTEND_ESPEAK_DATA_MISSING -> "FRONTEND_ESPEAK_DATA_MISSING"
                FRONTEND_ESPEAK_DISABLED -> "FRONTEND_ESPEAK_DISABLED"
                FRONTEND_ESPEAK_INIT_FAILED -> "FRONTEND_ESPEAK_INIT_FAILED"
                FRONTEND_ESPEAK_PHONEME_EMPTY -> "FRONTEND_ESPEAK_PHONEME_EMPTY"
                FRONTEND_TOKEN_MISS -> "FRONTEND_TOKEN_MISS"
                ERR_INVALID_HANDLE -> "ERR_INVALID_HANDLE"
                ERR_INVALID_INPUT -> "ERR_INVALID_INPUT"
                ERR_VITS_RUN_EMPTY -> "ERR_VITS_RUN_EMPTY"
                ERR_WRITE_WAVE -> "ERR_WRITE_WAVE"
                else -> "UNKNOWN_ERROR"
            }
            return "TTSEngine generate failed: $reason (code=$code)"
        }

        init {
            System.loadLibrary("sherpa-tts-jni")
        }
    }
}
