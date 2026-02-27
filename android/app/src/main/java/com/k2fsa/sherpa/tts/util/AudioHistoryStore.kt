package com.k2fsa.sherpa.tts.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AudioHistoryItem(
    val path: String,
    val createdAt: Long
)

object AudioHistoryStore {
    private const val PREFS = "tts_prefs"
    private const val KEY_HISTORY = "audio_history"
    private const val MAX_ITEMS = 10

    fun getItems(context: Context): List<AudioHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val out = ArrayList<AudioHistoryItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val path = obj.optString("path", "")
            val ts = obj.optLong("createdAt", 0L)
            if (path.isNotBlank() && ts > 0L) {
                out.add(AudioHistoryItem(path, ts))
            }
        }
        return out
    }

    fun addItem(context: Context, item: AudioHistoryItem): List<AudioHistoryItem> {
        val items = getItems(context).toMutableList()
        items.removeAll { it.path == item.path }
        items.add(0, item)
        val trimmed = items.take(MAX_ITEMS)
        save(context, trimmed)
        return trimmed
    }

    private fun save(context: Context, items: List<AudioHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("path", item.path)
            obj.put("createdAt", item.createdAt)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, arr.toString())
            .apply()
    }
}
