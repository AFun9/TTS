package com.k2fsa.sherpa.tts.ui.main

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.k2fsa.sherpa.tts.R
import com.k2fsa.sherpa.tts.util.AudioHistoryStore
import java.io.File

/**
 * 封装「最新一条音频」的播放、分享、导出、重命名；持有 MediaPlayer，需在 Activity onDestroy 时调用 [release]。
 */
class LatestAudioHandler(private val activity: AppCompatActivity) {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * 播放 WAV；播放结束调用 [onCompletion]。会先 release 之前的 MediaPlayer。
     */
    fun play(path: String, playbackSpeed: Float, onCompletion: () -> Unit) {
        val file = File(path)
        if (!file.exists()) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            applyPlaybackSpeed(playbackSpeed)
            setOnCompletionListener {
                onCompletion()
            }
            start()
        }
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val params = mediaPlayer?.playbackParams?.setSpeed(speed)
                if (params != null) mediaPlayer?.playbackParams = params
            } catch (_: Throwable) { }
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun share(path: String, onMissing: (() -> Unit)? = null) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(activity, activity.getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            onMissing?.invoke()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_audio)))
        } catch (_: Throwable) {
            Toast.makeText(activity, activity.getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun export(srcPath: String, destUri: Uri, onMissing: (() -> Unit)? = null) {
        val src = File(srcPath)
        if (!src.exists()) {
            Toast.makeText(activity, activity.getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            onMissing?.invoke()
            return
        }
        try {
            activity.contentResolver.openOutputStream(destUri)?.use { output ->
                src.inputStream().use { input -> input.copyTo(output) }
            }
            Toast.makeText(activity, activity.getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(activity, activity.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 弹出重命名对话框；若文件不存在则调用 [onMissing]；重命名成功后调用 [onRenamed] 传入新路径，由调用方更新 Store 与 UI。
     */
    fun rename(path: String, onMissing: () -> Unit, onRenamed: (newPath: String, createdAt: Long, favorite: Boolean) -> Unit) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(activity, activity.getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            onMissing()
            return
        }
        val input = android.widget.EditText(activity).apply { setText(file.nameWithoutExtension) }
        val origin = AudioHistoryStore.getItems(activity).firstOrNull { it.path == path }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.rename))
            .setView(input)
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) return@setPositiveButton
                val target = File(file.parentFile, "$newName.wav")
                if (target.exists()) {
                    Toast.makeText(activity, activity.getString(R.string.toast_name_exists), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (file.renameTo(target)) {
                    onRenamed(target.absolutePath, origin?.createdAt ?: System.currentTimeMillis(), origin?.favorite ?: false)
                } else {
                    Toast.makeText(activity, activity.getString(R.string.toast_rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
