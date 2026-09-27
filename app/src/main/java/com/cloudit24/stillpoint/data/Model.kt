package com.cloudit24.stillpoint.data

import android.os.UserHandle

data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val user: UserHandle,
    val userSerial: Long,
    val category: Int,
    val installedAt: Long,
) {
    val key: String get() = "$packageName/$className#$userSerial"
}

data class TaskItem(val id: Long, val text: String, val done: Boolean)

data class AgendaItem(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** A user-made folder inside Favorites. [apps] holds [AppEntry.key]s. */
data class FavFolder(val id: Long, val name: String, val apps: List<String>)

enum class HomeMode { AUTO, PINNED }

enum class HomeStyle { LIST, ICONS }

/** Built-in things a gesture or bottom shortcut can do. */
enum class HomeAction(val label: String, val needsGestureService: Boolean = false) {
    NONE("Nothing"),
    APPS("All apps"),
    WIDGETS("Widgets"),
    PHONE("Phone"),
    CAMERA("Camera"),
    FOCUS("Focus"),
    SETTINGS("Settings"),
    NOTIFICATIONS("Notifications", needsGestureService = true),
    LOCK("Lock screen", needsGestureService = true),
}

/**
 * What a gesture or shortcut opens, stored as a string:
 * "action:WIDGETS" for a [HomeAction], or "app:<AppEntry.key>" for any app.
 */
object GestureTarget {
    fun action(a: HomeAction) = "action:${a.name}"
    fun app(key: String) = "app:$key"
    fun actionOf(t: String): HomeAction? =
        if (t.startsWith("action:")) runCatching { HomeAction.valueOf(t.removePrefix("action:")) }.getOrNull() else null
    fun appKeyOf(t: String): String? = if (t.startsWith("app:")) t.removePrefix("app:") else null
}

/** The customizable gesture and shortcut slots on the home screen. */
enum class GestureSlot(val label: String) {
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    DOUBLE_TAP("Double-tap"),
    SHORTCUT_LEFT("Bottom-left shortcut"),
    SHORTCUT_RIGHT("Bottom-right shortcut"),
}

val DEFAULT_GESTURES: Map<GestureSlot, String> = mapOf(
    GestureSlot.SWIPE_LEFT to GestureTarget.action(HomeAction.APPS),
    GestureSlot.SWIPE_RIGHT to GestureTarget.action(HomeAction.WIDGETS),
    GestureSlot.SWIPE_UP to GestureTarget.action(HomeAction.APPS),
    GestureSlot.SWIPE_DOWN to GestureTarget.action(HomeAction.NONE),
    GestureSlot.DOUBLE_TAP to GestureTarget.action(HomeAction.NONE),
    GestureSlot.SHORTCUT_LEFT to GestureTarget.action(HomeAction.FOCUS),
    GestureSlot.SHORTCUT_RIGHT to GestureTarget.action(HomeAction.PHONE),
)

data class LauncherSettings(
    val homeMode: HomeMode = HomeMode.AUTO,
    val homeCount: Int = 6,
    /** Home app label size in sp; icons scale with it. */
    val homeSize: Int = 24,
    val homeStyle: HomeStyle = HomeStyle.LIST,
    val showUsage: Boolean = true,
    val showAgenda: Boolean = false,
    val showTasks: Boolean = true,
    val showIcons: Boolean = false,
    /** GestureSlot -> GestureTarget string. Missing slots fall back to [DEFAULT_GESTURES]. */
    val gestures: Map<GestureSlot, String> = DEFAULT_GESTURES,
    val hidden: Set<String> = emptySet(),
    val pinned: List<String> = emptyList(),
    /** Favorites not in any folder. */
    val favorites: List<String> = emptyList(),
    val folders: List<FavFolder> = emptyList(),
    val focusAllowed: Set<String> = emptySet(),
    val focusEndsAt: Long = 0L,
) {
    fun gesture(slot: GestureSlot): String = gestures[slot] ?: DEFAULT_GESTURES.getValue(slot)
}
