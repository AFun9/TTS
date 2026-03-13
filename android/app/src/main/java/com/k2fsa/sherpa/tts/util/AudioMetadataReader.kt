package com.k2fsa.sherpa.tts.util

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 读取本地音频文件的基础元数据。
 *
 * 当前只暴露 UI 已使用的时长和文件大小；读取失败时统一返回 0，
 * 让调用方按“无元数据”处理即可。
 */
class AudioMetadataReader {

    fun getDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            retriever.release()
        }
    }

    fun getSizeBytes(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }
}
