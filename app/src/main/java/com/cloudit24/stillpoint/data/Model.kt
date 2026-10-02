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

/** [due] is a day (epoch day), or -1 for none. */
/** [due]: epoch day or -1. [remind]: minute of the day to ring on the due day, or -1 for no alert. */
data class TaskItem(val id: Long, val text: String, val done: Boolean, val due: Long = -1L, val remind: Int = -1)

data class AgendaItem(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** A user-made folder inside Favorites. [apps] holds [AppEntry.key]s. */
data class FavFolder(val id: Long, val name: String, val apps: List<String>)

enum class HomeMode { AUTO, PINNED }

enum class HomeStyle(val label: String) { LIST("List"), ICONS("Icons only") }

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
    CUSTOM("Your own source", "A web address you choose"),
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
    SEARCH("Search"),
    WIDGETS("Shelf"),
    PHONE("Phone"),
    CAMERA("Camera"),
    FOCUS("Focus"),
    SETTINGS("Settings"),
    DATA_USAGE("Data usage"),
    PRAYER("Prayer times and Qibla"),
    TOOLS("Tools"),
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
    SHORTCUT_MIDDLE("Bottom-middle shortcut"),
    SHORTCUT_RIGHT("Bottom-right shortcut"),
}

val DEFAULT_GESTURES: Map<GestureSlot, String> = mapOf(
    GestureSlot.SWIPE_LEFT to GestureTarget.action(HomeAction.APPS),
    GestureSlot.SWIPE_RIGHT to GestureTarget.action(HomeAction.WIDGETS),
    GestureSlot.SWIPE_UP to GestureTarget.action(HomeAction.APPS),
    GestureSlot.SWIPE_DOWN to GestureTarget.action(HomeAction.NONE),
    GestureSlot.DOUBLE_TAP to GestureTarget.action(HomeAction.NONE),
    GestureSlot.SHORTCUT_LEFT to GestureTarget.action(HomeAction.FOCUS),
    GestureSlot.SHORTCUT_MIDDLE to GestureTarget.action(HomeAction.SEARCH),
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
    val accentStyle: AccentStyle = AccentStyle.SOLID,
    /** Senior mode: big text, six tiles (app keys, "" for empty) and one person to call. */
    val seniorMode: Boolean = false,
    val seniorApps: List<String> = emptyList(),
    val seniorCallName: String = "",
    val seniorCallNumber: String = "",
    /** Favorites tile sizes by app key or "folder:<id>": 0 small, 1 medium, 2 wide. */
    val tileSizes: Map<String, Int> = emptyMap(),
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
    val sakaOn: Boolean = false,
    val malayalamOn: Boolean = false,
    // Lock screen notification (off by default). Gold is never shown there.
    val lockOn: Boolean = false,
    val lockPrayer: Boolean = true,
    val lockHijri: Boolean = true,
    val lockTamil: Boolean = false,
    // Prayer alerts, iqama minutes after each adhan, edge light and compass vibration.
    val adhanAlert: Boolean = false,
    val iqamaAlert: Boolean = false,
    val iqama: Map<Prayer, Int> = DEFAULT_IQAMA,
    val edgeStyle: EdgeStyle = EdgeStyle.FLAT,
    val edgeBright: Int = 2,
    val edgeWarnMin: Int = 15,
    val edgeMotion: Boolean = true,
    val edgeRight: Boolean = true,
    val iconTint: IconTint = IconTint.ORIGINAL,
    val font: AppFont = AppFont.SYSTEM,
    // Notification light (needs Notification access).
    val notifyLight: Boolean = true,
    val notifyStyle: NotifyStyle = NotifyStyle.BREATHE,
    val notifyDot: Boolean = true,
    val notifyAppDots: Boolean = true,
    val notifyOff: Set<String> = emptySet(),
    val notifyColors: Map<String, Long> = emptyMap(),
    val importantApps: Set<String> = emptySet(),
    val importantPeople: List<String> = emptyList(),
    val remindEvery: Int = 0,
    val infoPanels: Set<InfoPanel> = setOf(InfoPanel.PRAYER),
    val showRecent: Boolean = true,
    /** Cities for the world clock card, "Label|Zone", up to three. */
    val worldClocks: List<String> = emptyList(),
    val showProjects: Boolean = true,
    val dialMode: DialMode = DialMode.PRAYER,
    val compassHaptics: Boolean = true,
    /** Tools (device, security and text, network): off until turned on in Extras. */
    val toolsOn: Boolean = false,
    // Tasks, calendar and projects: where each comes from and how it syncs.
    val tasksSource: TaskSource = TaskSource.PHONE,
    val calendarSource: CalendarSource = CalendarSource.PHONE,
    val projectsSource: ProjectSource = ProjectSource.PHONE,
    val tasksSync: SyncConfig = SyncConfig(),
    val calendarSync: SyncConfig = SyncConfig(),
    val projectsSync: SyncConfig = SyncConfig(),
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

    fun iqamaMin(p: Prayer): Int = iqama[p] ?: DEFAULT_IQAMA[p] ?: 0

    fun sync(f: SyncFeature): SyncConfig = when (f) {
        SyncFeature.TASKS -> tasksSync
        SyncFeature.CALENDAR -> calendarSync
        SyncFeature.PROJECTS -> projectsSync
    }
}

/** Minutes from the adhan to the iqama; common UAE mosque times. */
val DEFAULT_IQAMA = mapOf(Prayer.FAJR to 25, Prayer.DHUHR to 20, Prayer.ASR to 20, Prayer.MAGHRIB to 5, Prayer.ISHA to 20)

fun iqamaFrom(text: String?): Map<Prayer, Int> = DEFAULT_IQAMA + (text ?: "").split(",").mapNotNull { part ->
    runCatching { val (k, v) = part.split(":"); Prayer.valueOf(k) to v.toInt() }.getOrNull()
}

fun iqamaText(m: Map<Prayer, Int>): String = m.entries.joinToString(",") { "${it.key.name}:${it.value}" }

/** Where the prayer light is drawn. Curved phones get the very edge; flat screens get lines inside it or a bottom bar. */
enum class EdgeStyle(val label: String, val detail: String) {
    OFF("Off", "No light on the screen."),
    CURVED("Curved sides", "Lines on the very edge of the screen, made for curved-edge phones like the Motorola Edge."),
    FLAT("Inner sides", "Lines just inside both sides, clear of the rounded corners. For flat screens."),
    BOTTOM("Bottom bar", "A line along the bottom of the screen. Works on any phone."),
}

/** How the accent fills tiles and squares: flat, or one of three soft gradients. */
enum class AccentStyle(val label: String) { SOLID("Solid"), SOFT("Soft"), DUO("Duo"), GLOW("Glow") }

enum class IconTint(val label: String) {
    ORIGINAL("Original colours"),
    GREY("Grey"),
    DIM("Dim grey"),
    ACCENT("Accent colour"),
}

/** What the important-info slot under the headline shows. */
enum class InfoPanel(val label: String) {
    PRAYER("Prayer times"),
    AGENDA("Next on your calendar"),
    DAY("Day and battery"),
    WEEK("This week, with your calendars"),
    TASKS("Tasks left"),
    WEATHER("Weather now"),
    WORLD("World clock"),
}

/** What the ring beside the headline shows. */
enum class DialMode(val label: String) {
    PRAYER("Next prayer"),
    BATTERY("Battery"),
    DAY("Time left today"),
    OFF("Off"),
}

/** The app's typeface. The bundled ones are open-source (docs/FONTS.md). */
enum class AppFont(val label: String) {
    SYSTEM("Phone's font"),
    INTER("Inter"),
    MANROPE("Manrope"),
    SPACE("Space Grotesk"),
    LORA("Lora (serif)"),
}

/** A line kept on the Shelf. */
data class Note(val id: Long, val text: String, val color: Int = 0)

/** How one widget on the Shelf looks. style: 0 plain, 1 glass, 2 glow. corners: 0 square, 1 soft, 2 round. */
data class WidgetLook(val style: Int = 0, val corners: Int = 1, val alpha: Int = 100)

enum class NotifyStyle(val label: String) {
    BREATHE("Breathe"),
    SWEEP("Sweep"),
    BLINK("Blink, like BlackBerry"),
}

/** Names for the three shelves, in order. Each can be renamed with a long-press. */
val SHELF_NAMES = listOf("Today", "Work", "Home")

/** One shelf: its name and what's on it, in order (widget ids, and Stillpoint cards as negative ids). */
data class ShelfPage(val id: Int, val name: String, val items: List<Int>)

/**
 * Stillpoint's own cards for the Shelf. On a shelf each has the id -(code * 100 + shelf id),
 * so it never collides with a widget id (always positive) and can appear once per shelf.
 */
/** [retired]: no longer offered or shown; kept so old ids still mean the same thing. */
enum class BuiltIn(val code: Int, val title: String, val summary: String, val canHalf: Boolean = false, val retired: Boolean = false) {
    NOTES(1, "Notes", "A scribble board"),
    TASKS(2, "Tasks", "Round ticks and days; syncs with Project Hub or your task app"),
    PROJECTS(3, "Projects", "Steps and how far along you are", retired = true),
    CALENDAR(10, "Calendar", "This month, with your calendars"),
    CLOCKS(11, "World clock", "Your cities at a glance"),
    BATTERY(12, "Battery and storage", "Charge, temperature, space and memory", canHalf = true),
    WEATHER(13, "Weather", "Now, with when it was fetched"),
    PRAYER(14, "Prayer and moon", "Today's five times and tonight's moon"),
    COUNTDOWN(15, "Countdown", "Days to a date you choose", canHalf = true),
    HOME_ASSISTANT(20, "Home Assistant", "Your lights, switches, scenes and sensors"),
    UPTIME(21, "Uptime Kuma", "Which of your services are up"),
    MESSAGES(22, "ntfy or Gotify", "The latest messages from your server");

    fun id(shelf: Int): Int = -(code * 100 + shelf)

    companion object {
        fun of(id: Int): BuiltIn? = if (id >= 0) null else entries.firstOrNull { it.code == (-id) / 100 }
    }
}
