package com.k2fsa.sherpa.tts.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.k2fsa.sherpa.tts.R
import java.io.File

/**
 * 封装音频文件级操作，供主界面与历史页复用。
 *
 * 这里只处理“分享/导出/重命名”本身，以及文件缺失时的统一提示；
 * 历史列表、最新一条状态等业务更新仍由调用方负责。
 */
class AudioFileActions(private val activity: AppCompatActivity) {

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
            val out = activity.contentResolver.openOutputStream(destUri)
            if (out == null) {
                Toast.makeText(activity, activity.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                return
            }
            out.use { output ->
                src.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(activity, activity.getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(activity, activity.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun rename(
        path: String,
        onMissing: () -> Unit,
        onRenamed: (newPath: String, createdAt: Long, favorite: Boolean) -> Unit
    ) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(activity, activity.getString(R.string.toast_audio_missing), Toast.LENGTH_SHORT).show()
            onMissing()
            return
        }
        val input = android.widget.EditText(activity).apply {
            setText(file.nameWithoutExtension)
        }
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
                    onRenamed(
                        target.absolutePath,
                        origin?.createdAt ?: System.currentTimeMillis(),
                        origin?.favorite ?: false
                    )
                } else {
                    Toast.makeText(activity, activity.getString(R.string.toast_rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
