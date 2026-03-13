package com.k2fsa.sherpa.tts.util

import android.media.MediaPlayer
import java.io.File

/**
 * 统一封装单音频播放器的生命周期与基础控制。
 *
 * 它只负责 MediaPlayer 的创建、切换、暂停恢复、seek 和语速调整；
 * 具体的 UI 刷新、轮询进度和缺文件提示由调用方决定。
 */
class AudioPlaybackController {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null
    private var onCompletion: (() -> Unit)? = null

    val playingPath: String?
        get() = currentPath

    val currentPosition: Int
        get() = mediaPlayer?.currentPosition ?: 0

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    fun play(path: String, playbackSpeed: Float, onCompletion: (() -> Unit)? = null) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
        }
        currentPath = path
        this.onCompletion = onCompletion
        applyPlaybackSpeed(playbackSpeed)
        mediaPlayer?.setOnCompletionListener {
            stop()
            this.onCompletion?.invoke()
        }
    }

    fun toggle(path: String, playbackSpeed: Float, onCompletion: (() -> Unit)? = null): PlaybackSnapshot {
        if (currentPath == path && mediaPlayer != null) {
            val player = mediaPlayer!!
            if (player.isPlaying) {
                player.pause()
            } else {
                player.start()
            }
            this.onCompletion = onCompletion
            applyPlaybackSpeed(playbackSpeed)
            return snapshot()
        }

        play(path, playbackSpeed, onCompletion)
        return snapshot()
    }

    fun updatePlaybackSpeed(speed: Float) {
        applyPlaybackSpeed(speed)
    }

    fun seekTo(positionMs: Int): PlaybackSnapshot {
        mediaPlayer?.seekTo(positionMs)
        return snapshot()
    }

    fun snapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            path = currentPath,
            positionMs = currentPosition,
            isPlaying = isPlaying
        )
    }

    fun stop() {
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        currentPath = null
        onCompletion = null
    }

    fun canPlay(path: String): Boolean = File(path).exists()

    private fun applyPlaybackSpeed(speed: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val params = mediaPlayer?.playbackParams?.setSpeed(speed)
                if (params != null) mediaPlayer?.playbackParams = params
            } catch (_: Throwable) {
            }
        }
    }
}

data class PlaybackSnapshot(
    val path: String?,
    val positionMs: Int,
    val isPlaying: Boolean
)
