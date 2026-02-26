package com.k2fsa.sherpa.tts.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 将应用内打包的 espeak-ng-data（assets）解压到 filesDir，并返回固定路径。
 * 与 sherpa-onnx 的 --vits-data-dir 对应，必须包含 phontab、phonindex、phondata、intonations。
 */
object EspeakDataHelper {
    private const val TAG = "EspeakDataHelper"

    /** 写死在应用内的 espeak-ng 数据目录名（相对 filesDir）。 */
    private const val ESEPEAK_NG_DATA_DIR = "espeak-ng-data"

    /** assets 中的目录名（与 sherpa-tts-wrapper/models/espeak-ng-data 打包一致）。 */
    private const val ASSET_ESPEAK_NG_DATA = "espeak-ng-data"
    private val REQUIRED_FILES = listOf("phontab", "phonindex", "phondata", "intonations", "ru_dict")

    /**
     * 确保 filesDir/espeak-ng-data 存在且包含 phontab；若不存在则从 assets 复制。
     * 返回绝对路径，供 JNI 作为 data_dir 使用。
     */
    fun ensure(context: Context): String {
        val targetDir = File(context.filesDir, ESEPEAK_NG_DATA_DIR)
        if (isDataDirValid(targetDir)) {
            Log.i(TAG, "reuse espeak-ng-data: ${targetDir.absolutePath}")
            return targetDir.absolutePath
        }
        targetDir.mkdirs()
        copyAssetDir(context, ASSET_ESPEAK_NG_DATA, targetDir)
        if (!isDataDirValid(targetDir)) {
            val missing = REQUIRED_FILES.filter { !File(targetDir, it).exists() }
            throw IllegalStateException(
                "espeak-ng-data 不完整，缺失: ${missing.joinToString(",")} path=${targetDir.absolutePath}"
            )
        }
        Log.i(TAG, "prepared espeak-ng-data: ${targetDir.absolutePath}")
        return targetDir.absolutePath
    }

    private fun isDataDirValid(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return REQUIRED_FILES.all { File(dir, it).exists() }
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        val list = context.assets.list(assetPath) ?: return
        if (list.isEmpty()) {
            copyAssetFile(context, assetPath, File(targetDir, File(assetPath).name))
            return
        }
        for (name in list) {
            val subAsset = "$assetPath/$name"
            val subFile = File(targetDir, name)
            val subList = context.assets.list(subAsset)
            if (subList.isNullOrEmpty()) {
                copyAssetFile(context, subAsset, subFile)
            } else {
                subFile.mkdirs()
                copyAssetDir(context, subAsset, subFile)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
