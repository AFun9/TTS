package com.k2fsa.sherpa.tts.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 封装 TTS 相关偏好：模型/tokens/lexicon 路径、自动播放、播放速度。
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

    companion object {
        private const val PREFS_NAME = "tts_prefs"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_TOKENS_PATH = "tokens_path"
        private const val KEY_LEXICON_PATH = "lexicon_path"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
    }
}
