package com.k2fsa.sherpa.tts.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 负责把系统文档选择器返回的文件复制到应用私有目录。
 *
 * UI 层只关心输入 Uri 和目标目录，不直接处理 ContentResolver 细节。
 */
class DocumentCopyHelper(private val context: Context) {

    private val contentResolver get() = context.contentResolver

    /**
     * 从 [uri] 复制到 [destDir]，文件名取自 DISPLAY_NAME 或 uri 最后一段。
     * @return 目标文件绝对路径，失败返回 null
     */
    suspend fun copyToAppDir(uri: Uri, destDir: File): String? = withContext(Dispatchers.IO) {
        val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val dest = File(destDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
            dest.absolutePath
        }
    }
}
