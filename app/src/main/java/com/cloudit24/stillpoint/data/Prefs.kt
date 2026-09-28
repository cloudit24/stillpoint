package com.cloudit24.stillpoint.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All state is local SharedPreferences. No analytics. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("stillpoint", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            homeMode = runCatching { HomeMode.valueOf(sp.getString(K_HOME_MODE, d.homeMode.name)!!) }
                .getOrDefault(d.homeMode),
            homeCount = sp.getInt(K_HOME_COUNT, d.homeCount),
            homeSize = sp.getInt(K_HOME_SIZE, d.homeSize),
            homeStyle = runCatching { HomeStyle.valueOf(sp.getString(K_HOME_STYLE, d.homeStyle.name)!!) }
                .getOrDefault(d.homeStyle),
            showUsage = sp.getBoolean(K_SHOW_USAGE, d.showUsage),
            showAgenda = sp.getBoolean(K_SHOW_AGENDA, d.showAgenda),
            showTasks = sp.getBoolean(K_SHOW_TASKS, d.showTasks),
            showIcons = sp.getBoolean(K_SHOW_ICONS, d.showIcons),
            clockStyle = runCatching { ClockStyle.valueOf(sp.getString(K_CLOCK, d.clockStyle.name)!!) }
                .getOrDefault(d.clockStyle),
            showStats = sp.getBoolean(K_SHOW_STATS, d.showStats),
            showLocalIp = sp.getBoolean(K_LOCAL_IP, d.showLocalIp),
            publicIpOn = sp.getBoolean(K_PUBLIC_IP, d.publicIpOn),
            prayerOn = sp.getBoolean(K_PRAYER_ON, d.prayerOn),
            prayerMethod = runCatching { PrayerMethod.valueOf(sp.getString(K_PRAYER_METHOD, d.prayerMethod.name)!!) }
                .getOrDefault(d.prayerMethod),
            asrHanafi = sp.getBoolean(K_ASR_HANAFI, d.asrHanafi),
            hijriOn = sp.getBoolean(K_HIJRI_ON, d.hijriOn),
            hijriAdjust = sp.getInt(K_HIJRI_ADJUST, d.hijriAdjust),
            tamilOn = sp.getBoolean(K_TAMIL_ON, d.tamilOn),
            weatherOn = sp.getBoolean(K_WEATHER_ON, d.weatherOn),
            city = sp.getString(K_CITY, null)?.let { raw ->
                runCatching {
                    val o = JSONObject(raw)
                    City(o.getString("name"), o.optString("country"), o.getDouble("lat"), o.getDouble("lon"))
                }.getOrNull()
            },
            fahrenheit = sp.getBoolean(K_FAHRENHEIT, d.fahrenheit),
            animateWeather = sp.getBoolean(K_ANIMATE_WEATHER, d.animateWeather),
            goldOn = sp.getBoolean(K_GOLD_ON, d.goldOn),
            goldCurrency = sp.getString(K_GOLD_CURRENCY, d.goldCurrency) ?: d.goldCurrency,
            goldKarat = sp.getInt(K_GOLD_KARAT, d.goldKarat),
            goldPerGram = sp.getBoolean(K_GOLD_PER_GRAM, d.goldPerGram),
            goldSource = runCatching { GoldSource.valueOf(sp.getString(K_GOLD_SOURCE, d.goldSource.name)!!) }
                .getOrDefault(d.goldSource),
            gestures = loadGestures(),
            hidden = sp.getStringSet(K_HIDDEN, null)?.toSet() ?: emptySet(),
            pinned = readStringList(sp.getString(K_PINNED, null)),
            favorites = readStringList(sp.getString(K_FAVORITES, null)),
            folders = readFolders(sp.getString(K_FOLDERS, null)),
            focusAllowed = sp.getStringSet(K_FOCUS_ALLOWED, null)?.toSet() ?: emptySet(),
            focusEndsAt = sp.getLong(K_FOCUS_ENDS, 0L),
        )
    }

    fun saveSettings(s: LauncherSettings) {
        sp.edit()
            .putString(K_HOME_MODE, s.homeMode.name)
            .putInt(K_HOME_COUNT, s.homeCount)
            .putInt(K_HOME_SIZE, s.homeSize)
            .putString(K_HOME_STYLE, s.homeStyle.name)
            .putBoolean(K_SHOW_USAGE, s.showUsage)
            .putBoolean(K_SHOW_AGENDA, s.showAgenda)
            .putBoolean(K_SHOW_TASKS, s.showTasks)
            .putBoolean(K_SHOW_ICONS, s.showIcons)
            .putString(K_CLOCK, s.clockStyle.name)
            .putBoolean(K_SHOW_STATS, s.showStats)
            .putBoolean(K_LOCAL_IP, s.showLocalIp)
            .putBoolean(K_PUBLIC_IP, s.publicIpOn)
            .putBoolean(K_PRAYER_ON, s.prayerOn)
            .putString(K_PRAYER_METHOD, s.prayerMethod.name)
            .putBoolean(K_ASR_HANAFI, s.asrHanafi)
            .putBoolean(K_HIJRI_ON, s.hijriOn)
            .putInt(K_HIJRI_ADJUST, s.hijriAdjust)
            .putBoolean(K_TAMIL_ON, s.tamilOn)
            .putBoolean(K_WEATHER_ON, s.weatherOn)
            .putString(K_CITY, s.city?.let {
                JSONObject().put("name", it.name).put("country", it.country).put("lat", it.lat).put("lon", it.lon).toString()
            })
            .putBoolean(K_FAHRENHEIT, s.fahrenheit)
            .putBoolean(K_ANIMATE_WEATHER, s.animateWeather)
            .putBoolean(K_GOLD_ON, s.goldOn)
            .putString(K_GOLD_CURRENCY, s.goldCurrency)
            .putInt(K_GOLD_KARAT, s.goldKarat)
            .putBoolean(K_GOLD_PER_GRAM, s.goldPerGram)
            .putString(K_GOLD_SOURCE, s.goldSource.name)
            .apply { GestureSlot.entries.forEach { putString(K_GESTURE + it.name, s.gesture(it)) } }
            .putStringSet(K_HIDDEN, HashSet(s.hidden))
            .putString(K_PINNED, JSONArray(s.pinned).toString())
            .putString(K_FAVORITES, JSONArray(s.favorites).toString())
            .putString(K_FOLDERS, writeFolders(s.folders))
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

    /** Last fetched values, so home shows something instantly after a restart. */
    fun loadWeather(): WeatherNow? = sp.getString(K_WEATHER_CACHE, null)?.let { raw ->
        runCatching {
            val o = JSONObject(raw)
            WeatherNow(o.getDouble("t"), o.getInt("c"), o.getBoolean("d"), o.getLong("at"))
        }.getOrNull()
    }

    fun saveWeather(w: WeatherNow?) {
        sp.edit().putString(K_WEATHER_CACHE, w?.let {
            JSONObject().put("t", it.tempC).put("c", it.code).put("d", it.isDay).put("at", it.fetchedAt).toString()
        }).apply()
    }

    fun loadGold(): GoldQuote? = sp.getString(K_GOLD_CACHE, null)?.let { raw ->
        runCatching {
            val o = JSONObject(raw)
            val dubai = o.getJSONObject("dubai")
            GoldQuote(
                source = GoldSource.valueOf(o.getString("src")),
                dubaiAedPerGram = dubai.keys().asSequence().associate { it.toInt() to dubai.getDouble(it) },
                usdPerOz = if (o.has("usd")) o.getDouble("usd") else null,
                currency = o.getString("cur"),
                fxRate = o.getDouble("fx"),
                fetchedAt = o.getLong("at"),
            )
        }.getOrNull() // 0.3.0 cache has another shape: ignored, refetched.
    }

    fun saveGold(g: GoldQuote?) {
        sp.edit().putString(K_GOLD_CACHE, g?.let {
            val dubai = JSONObject().apply { it.dubaiAedPerGram.forEach { (k, v) -> put(k.toString(), v) } }
            JSONObject().put("src", it.source.name).put("dubai", dubai).put("usd", it.usdPerOz)
                .put("cur", it.currency).put("fx", it.fxRate).put("at", it.fetchedAt).toString()
        }).apply()
    }

    private fun loadGestures(): Map<GestureSlot, String> {
        // 0.1.x had two on/off switches; carry them over the first time.
        val legacy = mapOf(
            GestureSlot.DOUBLE_TAP to sp.getBoolean(K_DOUBLE_TAP, false).let {
                GestureTarget.action(if (it) HomeAction.LOCK else HomeAction.NONE)
            },
            GestureSlot.SWIPE_DOWN to sp.getBoolean(K_SWIPE_DOWN, false).let {
                GestureTarget.action(if (it) HomeAction.NOTIFICATIONS else HomeAction.NONE)
            },
        )
        return GestureSlot.entries.associateWith { slot ->
            sp.getString(K_GESTURE + slot.name, null) ?: legacy[slot] ?: DEFAULT_GESTURES.getValue(slot)
        }
    }

    private fun readStringList(raw: String?): List<String> =
        if (raw == null) emptyList()
        else runCatching { val a = JSONArray(raw); List(a.length()) { a.getString(it) } }.getOrDefault(emptyList())

    private fun readFolders(raw: String?): List<FavFolder> =
        if (raw == null) emptyList()
        else runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                FavFolder(o.getLong("id"), o.getString("name"), readStringList(o.getJSONArray("apps").toString()))
            }
        }.getOrDefault(emptyList())

    private fun writeFolders(folders: List<FavFolder>): String {
        val arr = JSONArray()
        folders.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps))) }
        return arr.toString()
    }

    private companion object {
        const val K_HOME_MODE = "home_mode"
        const val K_HOME_COUNT = "home_count"
        const val K_HOME_SIZE = "home_size"
        const val K_HOME_STYLE = "home_style"
        const val K_SHOW_USAGE = "show_usage"
        const val K_SHOW_AGENDA = "show_agenda"
        const val K_SHOW_TASKS = "show_tasks"
        const val K_SHOW_ICONS = "show_icons"
        const val K_WIDGET_IDS = "widget_ids"
        const val K_GESTURE = "gesture_"
        const val K_CLOCK = "clock_style"
        const val K_SHOW_STATS = "show_stats"
        const val K_LOCAL_IP = "show_local_ip"
        const val K_PUBLIC_IP = "public_ip_on"
        const val K_PRAYER_ON = "prayer_on"
        const val K_PRAYER_METHOD = "prayer_method"
        const val K_ASR_HANAFI = "prayer_asr_hanafi"
        const val K_HIJRI_ON = "hijri_on"
        const val K_HIJRI_ADJUST = "hijri_adjust"
        const val K_TAMIL_ON = "tamil_on"
        const val K_WEATHER_ON = "weather_on"
        const val K_CITY = "weather_city"
        const val K_FAHRENHEIT = "weather_fahrenheit"
        const val K_ANIMATE_WEATHER = "weather_animate"
        const val K_WEATHER_CACHE = "weather_cache"
        const val K_GOLD_ON = "gold_on"
        const val K_GOLD_CURRENCY = "gold_currency"
        const val K_GOLD_KARAT = "gold_karat"
        const val K_GOLD_PER_GRAM = "gold_per_gram"
        const val K_GOLD_SOURCE = "gold_source"
        const val K_GOLD_CACHE = "gold_cache"
        const val K_DOUBLE_TAP = "double_tap_lock"
        const val K_SWIPE_DOWN = "swipe_down_notifications"
        const val K_HIDDEN = "hidden"
        const val K_PINNED = "pinned"
        const val K_FAVORITES = "favorites"
        const val K_FOLDERS = "favorite_folders"
        const val K_FOCUS_ALLOWED = "focus_allowed"
        const val K_FOCUS_ENDS = "focus_ends_at"
        const val K_TASKS = "tasks"
    }
}
