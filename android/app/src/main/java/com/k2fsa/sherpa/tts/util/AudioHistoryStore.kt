package com.k2fsa.sherpa.tts.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AudioHistoryItem(
    val path: String,
    val createdAt: Long,
    val favorite: Boolean = false
)

/**
 * 管理最近生成音频的持久化列表。
 *
 * 这里使用 SharedPreferences + JSON 保存轻量历史记录，并在超出上限时尽量保留
 * 收藏项，优先裁剪未收藏的旧记录。
 */
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
            val fav = obj.optBoolean("favorite", false)
            if (path.isNotBlank() && ts > 0L) {
                out.add(AudioHistoryItem(path, ts, fav))
            }
        }
        return out
    }

    fun addItem(context: Context, item: AudioHistoryItem): List<AudioHistoryItem> {
        val items = getItems(context).toMutableList()
        items.removeAll { it.path == item.path }
        items.add(0, item)
        val trimmed = trimPreserveOrder(items.distinctBy { it.path })
        save(context, trimmed)
        return trimmed
    }

    fun removeItem(context: Context, path: String): List<AudioHistoryItem> {
        val items = getItems(context).toMutableList()
        items.removeAll { it.path == path }
        save(context, items)
        return items
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<AudioHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("path", item.path)
            obj.put("createdAt", item.createdAt)
            obj.put("favorite", item.favorite)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, arr.toString())
            .apply()
    }

    fun toggleFavorite(context: Context, path: String): List<AudioHistoryItem> {
        val items = getItems(context).map {
            if (it.path == path) it.copy(favorite = !it.favorite) else it
        }.distinctBy { it.path }
        val trimmed = trimPreserveOrder(items)
        save(context, trimmed)
        return trimmed
    }

    private fun trimPreserveOrder(items: List<AudioHistoryItem>): List<AudioHistoryItem> {
        if (items.size <= MAX_ITEMS) return items
        val mutable = items.toMutableList()
        while (mutable.size > MAX_ITEMS) {
            val idx = mutable.indexOfLast { !it.favorite }
            if (idx >= 0) {
                mutable.removeAt(idx)
            } else {
                mutable.removeAt(mutable.size - 1)
            }
        }
        return mutable
    }
}
