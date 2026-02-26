package com.k2fsa.sherpa.tts.data

/**
 * 文本前端策略：
 * - Auto: 先走 lexicon，失败后回退到 espeak。
 * - LexiconFirst: 强制仅走 lexicon（无命中即失败）。
 * - EspeakOnly: 强制仅走 espeak（要求 dataDir 可用）。
 */
enum class FrontendMode {
    Auto,
    LexiconFirst,
    EspeakOnly
}

/**
 * 本项目定义的 TTS 配置，与本仓库 JNI/Native 参数一一对应。
 */
data class TTSConfig(
    val modelPath: String,
    val tokensPath: String,
    val dataDir: String = "",
    val lexiconPath: String = "",
    val frontendMode: FrontendMode = FrontendMode.Auto,
    val voice: String = "ru",
    val speakerId: Int = 0,
    val speed: Float = 1.0f,
    val numThreads: Int = 1,
    val debug: Boolean = false
)
