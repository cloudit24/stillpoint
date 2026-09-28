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

enum class HomeStyle(val label: String) { LIST("List"), ICONS("Icons only"), TILES("Tiles (Windows Phone)") }

/** App list tabs, in pivot order. */
enum class DrawerTab(val label: String) { MOST("most used"), RECENT("recent"), ALL("all"), FAVORITES("favorites") }

data class AccentColor(val name: String, val argb: Long)

/** The Windows Phone accent palette, plus Stillpoint's original slate. */
val ACCENTS = listOf(
    AccentColor("Cyan", 0xFF1BA1E2), AccentColor("Cobalt", 0xFF0050EF), AccentColor("Indigo", 0xFF6A00FF),
    AccentColor("Violet", 0xFFAA00FF), AccentColor("Pink", 0xFFF472D0), AccentColor("Magenta", 0xFFD80073),
    AccentColor("Crimson", 0xFFA20025), AccentColor("Red", 0xFFE51400), AccentColor("Orange", 0xFFFA6800),
    AccentColor("Amber", 0xFFF0A30A), AccentColor("Yellow", 0xFFE3C800), AccentColor("Brown", 0xFF825A2C),
    AccentColor("Olive", 0xFF6D8764), AccentColor("Lime", 0xFFA4C400), AccentColor("Green", 0xFF60A917),
    AccentColor("Emerald", 0xFF008A00), AccentColor("Teal", 0xFF00ABA9), AccentColor("Steel", 0xFF647687),
    AccentColor("Mauve", 0xFF76608A), AccentColor("Taupe", 0xFF87794E), AccentColor("Slate", 0xFF9FB4C7),
)

enum class ClockStyle(val label: String) {
    MINIMAL("Minimal"),
    BOLD("Bold"),
    SERIF("Classic serif"),
    FLIP("Retro flip"),
    LCD("Retro LCD"),
    ANALOG("Analog"),
    HEADLINE("No clock: flipping headline"),
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
    PRAYER("Prayer times and Qibla"),
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
    val drawerStart: DrawerTab = DrawerTab.ALL,
    /** ARGB accent colour, see [ACCENTS]. */
    val accent: Long = 0xFF1BA1E2,
    val clockStyle: ClockStyle = ClockStyle.MINIMAL,
    /** Live network speed and RAM line under the date. */
    val showStats: Boolean = false,
    /** Phone's address on Wi-Fi / mobile. Read locally, never sent anywhere. */
    val showLocalIp: Boolean = false,
    /** Internet-facing address, asked from api.ipify.org when the network changes. */
    val publicIpOn: Boolean = false,
    // Prayer and calendars: calculated on the phone, nothing goes online.
    val prayerOn: Boolean = false,
    val prayerMethod: PrayerMethod = PrayerMethod.UAE,
    val asrHanafi: Boolean = false,
    val hijriOn: Boolean = false,
    /** Days added to the Hijri date for local moon sighting, -2..2. */
    val hijriAdjust: Int = 0,
    val tamilOn: Boolean = false,
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
    /** Project Hub card on home. Address and key are stored separately, see [Prefs.hubUrl]. */
    val hubOn: Boolean = false,
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
