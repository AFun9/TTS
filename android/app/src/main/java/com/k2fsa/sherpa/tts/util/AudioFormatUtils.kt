package com.k2fsa.sherpa.tts.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 音频文件相关的轻量格式化工具，避免主界面和历史页重复实现。
 */
object AudioFormatUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatDate(timeMs: Long): String {
        return dateFormat.format(Date(timeMs))
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
