package com.cloudit24.stillpoint.data

import android.os.UserHandle

data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val user: UserHandle,
    val userSerial: Long,
    val category: Int,
) {
    val key: String get() = "$packageName/$className#$userSerial"
}

data class TaskItem(val id: Long, val text: String, val done: Boolean)

data class AgendaItem(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

enum class HomeMode { AUTO, PINNED }

data class LauncherSettings(
    val homeMode: HomeMode = HomeMode.AUTO,
    val homeCount: Int = 6,
    val showUsage: Boolean = true,
    val showAgenda: Boolean = false,
    val showTasks: Boolean = true,
    val groupDrawer: Boolean = true,
    val showIcons: Boolean = false,
    val doubleTapLock: Boolean = false,
    val swipeDownNotifications: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val pinned: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val focusAllowed: Set<String> = emptySet(),
    val focusEndsAt: Long = 0L,
)
