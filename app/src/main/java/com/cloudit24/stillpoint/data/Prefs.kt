package com.cloudit24.stillpoint.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All state is local SharedPreferences. No network, no analytics. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("stillpoint", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            homeMode = runCatching { HomeMode.valueOf(sp.getString(K_HOME_MODE, d.homeMode.name)!!) }
                .getOrDefault(d.homeMode),
            homeCount = sp.getInt(K_HOME_COUNT, d.homeCount),
            showUsage = sp.getBoolean(K_SHOW_USAGE, d.showUsage),
            showAgenda = sp.getBoolean(K_SHOW_AGENDA, d.showAgenda),
            showTasks = sp.getBoolean(K_SHOW_TASKS, d.showTasks),
            groupDrawer = sp.getBoolean(K_GROUP_DRAWER, d.groupDrawer),
            showIcons = sp.getBoolean(K_SHOW_ICONS, d.showIcons),
            doubleTapLock = sp.getBoolean(K_DOUBLE_TAP, d.doubleTapLock),
            swipeDownNotifications = sp.getBoolean(K_SWIPE_DOWN, d.swipeDownNotifications),
            hidden = sp.getStringSet(K_HIDDEN, null)?.toSet() ?: emptySet(),
            pinned = readStringList(sp.getString(K_PINNED, null)),
            favorites = readStringList(sp.getString(K_FAVORITES, null)),
            focusAllowed = sp.getStringSet(K_FOCUS_ALLOWED, null)?.toSet() ?: emptySet(),
            focusEndsAt = sp.getLong(K_FOCUS_ENDS, 0L),
        )
    }

    fun saveSettings(s: LauncherSettings) {
        sp.edit()
            .putString(K_HOME_MODE, s.homeMode.name)
            .putInt(K_HOME_COUNT, s.homeCount)
            .putBoolean(K_SHOW_USAGE, s.showUsage)
            .putBoolean(K_SHOW_AGENDA, s.showAgenda)
            .putBoolean(K_SHOW_TASKS, s.showTasks)
            .putBoolean(K_GROUP_DRAWER, s.groupDrawer)
            .putBoolean(K_SHOW_ICONS, s.showIcons)
            .putBoolean(K_DOUBLE_TAP, s.doubleTapLock)
            .putBoolean(K_SWIPE_DOWN, s.swipeDownNotifications)
            .putStringSet(K_HIDDEN, HashSet(s.hidden))
            .putString(K_PINNED, JSONArray(s.pinned).toString())
            .putString(K_FAVORITES, JSONArray(s.favorites).toString())
            .putStringSet(K_FOCUS_ALLOWED, HashSet(s.focusAllowed))
            .putLong(K_FOCUS_ENDS, s.focusEndsAt)
            .apply()
    }

    fun loadTasks(): List<TaskItem> {
        val raw = sp.getString(K_TASKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TaskItem(o.getLong("id"), o.getString("text"), o.getBoolean("done"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveTasks(tasks: List<TaskItem>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        sp.edit().putString(K_TASKS, arr.toString()).apply()
    }

    /** Widget ids in page order. Ids belong to the system widget host, so they live apart from settings. */
    fun loadWidgetIds(): List<Int> =
        readStringList(sp.getString(K_WIDGET_IDS, null)).mapNotNull { it.toIntOrNull() }

    fun saveWidgetIds(ids: List<Int>) {
        sp.edit().putString(K_WIDGET_IDS, JSONArray(ids.map { it.toString() }).toString()).apply()
    }

    private fun readStringList(raw: String?): List<String> =
        if (raw == null) emptyList()
        else runCatching { val a = JSONArray(raw); List(a.length()) { a.getString(it) } }.getOrDefault(emptyList())

    private companion object {
        const val K_HOME_MODE = "home_mode"
        const val K_HOME_COUNT = "home_count"
        const val K_SHOW_USAGE = "show_usage"
        const val K_SHOW_AGENDA = "show_agenda"
        const val K_SHOW_TASKS = "show_tasks"
        const val K_GROUP_DRAWER = "group_drawer"
        const val K_SHOW_ICONS = "show_icons"
        const val K_WIDGET_IDS = "widget_ids"
        const val K_DOUBLE_TAP = "double_tap_lock"
        const val K_SWIPE_DOWN = "swipe_down_notifications"
        const val K_HIDDEN = "hidden"
        const val K_PINNED = "pinned"
        const val K_FAVORITES = "favorites"
        const val K_FOCUS_ALLOWED = "focus_allowed"
        const val K_FOCUS_ENDS = "focus_ends_at"
        const val K_TASKS = "tasks"
    }
}
