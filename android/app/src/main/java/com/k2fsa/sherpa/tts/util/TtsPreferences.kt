package com.k2fsa.sherpa.tts.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 统一管理主界面会持久化的轻量设置。
 *
 * 当前仅保存模型路径、词典路径，以及最近播放相关开关；
 * 不承担运行时状态缓存和复杂配置迁移。
 */
class TtsPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var modelPath: String
        get() = prefs.getString(KEY_MODEL_PATH, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL_PATH, value).apply()

    var tokensPath: String
        get() = prefs.getString(KEY_TOKENS_PATH, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKENS_PATH, value).apply()

    var lexiconPath: String
        get() = prefs.getString(KEY_LEXICON_PATH, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_LEXICON_PATH, value).apply()

    var autoPlay: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY, value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    /** 合成语速 (0.5–2.0)，仅持久化，由设置页写入。 */
    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    /** 合成音量 (0–100)，仅持久化，由设置页写入。 */
    var volume: Int
        get() = prefs.getInt(KEY_VOLUME, 80)
        set(value) = prefs.edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()

    companion object {
        private const val PREFS_NAME = "tts_prefs"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_TOKENS_PATH = "tokens_path"
        private const val KEY_LEXICON_PATH = "lexicon_path"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_VOLUME = "volume"
    }
}
