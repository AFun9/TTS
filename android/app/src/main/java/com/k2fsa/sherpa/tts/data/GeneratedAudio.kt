package com.k2fsa.sherpa.tts.data

/**
 * 本项目定义的生成结果：采样率 + WAV 文件路径（由 JNI 写入应用目录后返回路径）。
 */
data class GeneratedAudio(
    val sampleRate: Int,
    val wavFilePath: String
)
