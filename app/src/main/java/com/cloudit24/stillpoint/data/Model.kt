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

enum class ClockStyle(val label: String) {
    MINIMAL("Minimal"),
    BOLD("Bold"),
    SERIF("Classic serif"),
    FLIP("Retro flip"),
    LCD("Retro LCD"),
    ANALOG("Analog"),
}

/** A place picked by name; coordinates are rounded before any request. */
data class City(val name: String, val country: String, val lat: Double, val lon: Double)

data class WeatherNow(val tempC: Double, val code: Int, val isDay: Boolean, val fetchedAt: Long)

enum class GoldSource(val label: String, val detail: String) {
    DUBAI("Dubai shop rate", "Dubai Gold & Jewellery Group board rate, as published by Dubai City of Gold"),
    SPOT("World market (spot)", "International spot price from Swissquote, converted to your karat"),
}

/**
 * Gold prices as fetched. [dubaiAedPerGram] is karat -> AED per gram (empty if not fetched or unavailable);
 * [usdPerOz] is the spot price (null if not fetched). [fxRate] converts USD to [currency].
 */
data class GoldQuote(
    val source: GoldSource,
    val dubaiAedPerGram: Map<Int, Double>,
    val usdPerOz: Double?,
    val currency: String,
    val fxRate: Double,
    val fetchedAt: Long,
)

/** Currencies offered for the gold price. AED and SAR are fixed USD pegs; the rest use ECB rates. */
val GOLD_CURRENCIES = listOf("AED", "USD", "EUR", "GBP", "INR", "SAR", "PHP", "CHF", "JPY", "CNY", "CAD", "AUD")

/** Built-in things a gesture or bottom shortcut can do. */
enum class HomeAction(val label: String, val needsGestureService: Boolean = false) {
    NONE("Nothing"),
    APPS("All apps"),
    WIDGETS("Widgets"),
    PHONE("Phone"),
    CAMERA("Camera"),
    FOCUS("Focus"),
    SETTINGS("Settings"),
    DATA_USAGE("Data usage"),
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
    val clockStyle: ClockStyle = ClockStyle.MINIMAL,
    /** Live network speed and RAM line under the date. */
    val showStats: Boolean = false,
    /** Phone's address on Wi-Fi / mobile. Read locally, never sent anywhere. */
    val showLocalIp: Boolean = false,
    /** Internet-facing address, asked from api.ipify.org when the network changes. */
    val publicIpOn: Boolean = false,
    // Live data: all off by default. Nothing goes online unless one of these is on.
    val weatherOn: Boolean = false,
    val city: City? = null,
    val fahrenheit: Boolean = false,
    val animateWeather: Boolean = true,
    val goldOn: Boolean = false,
    val goldCurrency: String = "AED",
    val goldKarat: Int = 24,
    val goldPerGram: Boolean = true,
    val goldSource: GoldSource = GoldSource.DUBAI,
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
