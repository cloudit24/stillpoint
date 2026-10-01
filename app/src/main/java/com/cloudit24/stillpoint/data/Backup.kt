package com.cloudit24.stillpoint.data

import android.content.Context
import com.cloudit24.stillpoint.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Everything you set up, in one file you keep: settings, notes, tasks, projects, pinned apps, favorites,
 * gestures, iqama times, your own sources. Widgets are left out (they belong to this phone and are added again).
 */
object Backup {
    private const val PREFS = "stillpoint"

    /** Things tied to this phone, quickly fetched again, or secret (keys, tokens, private calendar links). */
    private fun skip(key: String) = key.startsWith("widget_") || key.endsWith("_cache") ||
        key.startsWith("secret_") || key == "hub_key" || key == "ics_url"

    fun export(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefs = JSONObject()
        for ((k, v) in sp.all) {
            if (skip(k) || v == null) continue
            val e = JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v)
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            prefs.put(k, e)
        }
        return JSONObject()
            .put("app", "stillpoint")
            .put("format", 1)
            .put("version", BuildConfig.VERSION_NAME)
            .put("saved", Instant.now().toString())
            .put("prefs", prefs)
            .toString(2)
    }

    /** Writes a backup's settings over the current ones. Returns how many were restored. */
    fun import(context: Context, text: String): Int {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "stillpoint") { "That file isn't a Stillpoint backup." }
        val prefs = root.getJSONObject("prefs")
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        var n = 0
        for (k in prefs.keys()) {
            if (skip(k)) continue
            val e = prefs.getJSONObject(k)
            when (e.getString("t")) {
                "s" -> ed.putString(k, e.getString("v"))
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "ss" -> e.getJSONArray("v").let { a -> ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                else -> continue
            }
            n++
        }
        ed.commit()
        return n
    }
}
