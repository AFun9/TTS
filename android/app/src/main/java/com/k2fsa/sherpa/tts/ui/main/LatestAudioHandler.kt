package com.k2fsa.sherpa.tts.ui.main

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.tts.util.AudioFileActions
import com.k2fsa.sherpa.tts.util.AudioPlaybackController

/**
 * 处理主界面“最新生成音频”的交互动作。
 *
 * 这里集中维护 MediaPlayer 与文件级操作，避免 MainActivity 同时承担
 * 播放控制、分享导出和重命名对话框等多种职责。
 */
class LatestAudioHandler(private val activity: AppCompatActivity) {

    private val playbackController = AudioPlaybackController()
    private val fileActions = AudioFileActions(activity)

    /**
     * 播放 WAV；播放结束调用 [onCompletion]。会先 release 之前的 MediaPlayer。
     */
    fun play(path: String, playbackSpeed: Float, onCompletion: () -> Unit) {
        if (!playbackController.canPlay(path)) return
        playbackController.play(path, playbackSpeed, onCompletion)
    }

    /** 更新当前播放的语速（如用户调节滑块时）。 */
    fun updatePlaybackSpeed(speed: Float) {
        playbackController.updatePlaybackSpeed(speed)
    }

    fun stop() {
        playbackController.stop()
    }

    fun release() {
        playbackController.stop()
    }

    fun share(path: String, onMissing: (() -> Unit)? = null) {
        fileActions.share(path, onMissing)
    }

    fun export(srcPath: String, destUri: Uri, onMissing: (() -> Unit)? = null) {
        fileActions.export(srcPath, destUri, onMissing)
    }

    /**
     * 弹出重命名对话框；若文件不存在则调用 [onMissing]；重命名成功后调用 [onRenamed] 传入新路径，由调用方更新 Store 与 UI。
     */
    fun rename(path: String, onMissing: () -> Unit, onRenamed: (newPath: String, createdAt: Long, favorite: Boolean) -> Unit) {
        fileActions.rename(path, onMissing, onRenamed)
    }
}
