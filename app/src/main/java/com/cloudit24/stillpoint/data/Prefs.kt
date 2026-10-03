package com.cloudit24.stillpoint.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All state is local SharedPreferences. No analytics. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("stillpoint", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings {
        if (!sp.getBoolean(K_HEADLINE_INTRO, false)) {
            sp.edit().putBoolean(K_HEADLINE_INTRO, true).putString(K_CLOCK, ClockStyle.HEADLINE.name).apply()
        }
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
            drawerStart = runCatching { DrawerTab.valueOf(sp.getString(K_DRAWER_START, d.drawerStart.name)!!) }
                .getOrDefault(d.drawerStart),
            accent = sp.getLong(K_ACCENT, d.accent),
            clockStyle = ClockStyle.HEADLINE,
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
            sakaOn = sp.getBoolean("saka_on", d.sakaOn),
            malayalamOn = sp.getBoolean("malayalam_on", d.malayalamOn),
            lockOn = sp.getBoolean(K_LOCK_ON, d.lockOn),
            lockPrayer = sp.getBoolean(K_LOCK_PRAYER, d.lockPrayer),
            lockHijri = sp.getBoolean(K_LOCK_HIJRI, d.lockHijri),
            lockTamil = sp.getBoolean(K_LOCK_TAMIL, d.lockTamil),
            adhanAlert = sp.getBoolean(K_ADHAN_ALERT, d.adhanAlert),
            iqamaAlert = sp.getBoolean(K_IQAMA_ALERT, d.iqamaAlert),
            iqama = iqamaFrom(sp.getString(K_IQAMA, null)),
            prayerPopup = sp.getBoolean(K_PRAYER_POPUP, d.prayerPopup),
            remindBefore = sp.getInt(K_REMIND_BEFORE, d.remindBefore),
            jumuah = sp.getInt(K_JUMUAH, d.jumuah),
            // Before 0.13 the light was on/off; curved Motorola Edge phones start on the curved style.
            edgeStyle = runCatching { EdgeStyle.valueOf(sp.getString(K_EDGE_STYLE, null)!!) }.getOrElse {
                when {
                    !sp.getBoolean(K_EDGE_LIGHT, true) -> EdgeStyle.OFF
                    android.os.Build.MODEL.contains("edge", ignoreCase = true) -> EdgeStyle.CURVED
                    else -> EdgeStyle.FLAT
                }
            },
            edgeBright = sp.getInt(K_EDGE_BRIGHT, d.edgeBright),
            edgeWarnMin = sp.getInt(K_EDGE_WARN, d.edgeWarnMin),
            edgeMotion = sp.getBoolean(K_EDGE_MOTION, d.edgeMotion),
            edgeRight = sp.getBoolean(K_EDGE_RIGHT, d.edgeRight),
            iconTint = runCatching { IconTint.valueOf(sp.getString(K_ICON_TINT, null)!!) }.getOrDefault(d.iconTint),
            seniorMode = sp.getBoolean("senior_mode", d.seniorMode),
            seniorApps = (sp.getString("senior_apps", "") ?: "").split("\n").let { l -> if (l.all { it.isEmpty() }) emptyList() else l },
            seniorCallName = sp.getString("senior_call_name", "") ?: "",
            seniorCallNumber = sp.getString("senior_call_number", "") ?: "",
            accentStyle = runCatching { AccentStyle.valueOf(sp.getString("accent_style", null)!!) }.getOrDefault(d.accentStyle),
            tileSizes = (sp.getString("tile_sizes", "") ?: "").split(";").mapNotNull { e ->
                val (k, v) = e.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toIntOrNull()?.let { k to it }
            }.toMap(),
            font = runCatching { AppFont.valueOf(sp.getString("app_font", null)!!) }.getOrDefault(d.font),
            notifyLight = sp.getBoolean("notify_light", d.notifyLight),
            notifyStyle = runCatching { NotifyStyle.valueOf(sp.getString("notify_style", null)!!) }.getOrDefault(d.notifyStyle),
            notifyDot = sp.getBoolean("notify_dot", d.notifyDot),
            notifyAppDots = sp.getBoolean("notify_app_dots", d.notifyAppDots),
            notifyOff = sp.getStringSet("notify_off", null)?.toSet() ?: d.notifyOff,
            notifyColors = (sp.getString("notify_colors", "") ?: "").split(";").mapNotNull { e ->
                val (k, v) = e.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toLongOrNull()?.let { k to it }
            }.toMap(),
            importantApps = sp.getStringSet("important_apps", null)?.toSet() ?: d.importantApps,
            importantPeople = (sp.getString("important_people", "") ?: "").split("\n").filter { it.isNotBlank() },
            worldClocks = (sp.getString("world_clocks", "") ?: "").split("\n").filter { it.isNotBlank() },
            showProjects = sp.getBoolean("show_projects", d.showProjects),
            remindEvery = sp.getInt("remind_every", d.remindEvery),
            // Before 0.17 only one could be chosen (key "info_panel").
            infoPanels = (sp.getString(K_INFO_PANELS, null) ?: sp.getString(K_INFO_PANEL, null))
                ?.split(",")?.mapNotNull { n -> runCatching { InfoPanel.valueOf(n) }.getOrNull() }?.toSet()
                ?: d.infoPanels,
            showRecent = sp.getBoolean(K_SHOW_RECENT, d.showRecent),
            dialMode = runCatching { DialMode.valueOf(sp.getString(K_DIAL, null)!!) }.getOrDefault(d.dialMode),
            compassHaptics = sp.getBoolean(K_COMPASS_HAPTICS, d.compassHaptics),
            toolsOn = sp.getBoolean("tools_on", d.toolsOn),
            // Before 0.11, a connected hub was used for tasks and the home card: keep that on upgrade.
            tasksSource = runCatching { TaskSource.valueOf(sp.getString(K_TASKS_SRC, null)!!) }
                .getOrDefault(if (sp.getBoolean(K_HUB_ON, false)) TaskSource.HUB else TaskSource.PHONE),
            calendarSource = runCatching { CalendarSource.valueOf(sp.getString(K_CAL_SRC, null)!!) }.getOrDefault(d.calendarSource),
            projectsSource = runCatching { ProjectSource.valueOf(sp.getString(K_PROJ_SRC, null)!!) }
                .getOrDefault(if (sp.getBoolean(K_HUB_ON, false)) ProjectSource.HUB else ProjectSource.PHONE),
            tasksSync = loadSync(SyncFeature.TASKS),
            calendarSync = loadSync(SyncFeature.CALENDAR),
            projectsSync = loadSync(SyncFeature.PROJECTS),
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
            hubOn = sp.getBoolean(K_HUB_ON, d.hubOn),
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
            .putString(K_DRAWER_START, s.drawerStart.name)
            .putLong(K_ACCENT, s.accent)
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
            .putBoolean("saka_on", s.sakaOn)
            .putBoolean("malayalam_on", s.malayalamOn)
            .putBoolean(K_LOCK_ON, s.lockOn)
            .putBoolean(K_LOCK_PRAYER, s.lockPrayer)
            .putBoolean(K_LOCK_HIJRI, s.lockHijri)
            .putBoolean(K_LOCK_TAMIL, s.lockTamil)
            .putBoolean(K_ADHAN_ALERT, s.adhanAlert)
            .putBoolean(K_IQAMA_ALERT, s.iqamaAlert)
            .putString(K_IQAMA, iqamaText(s.iqama))
            .putBoolean(K_PRAYER_POPUP, s.prayerPopup)
            .putInt(K_REMIND_BEFORE, s.remindBefore)
            .putInt(K_JUMUAH, s.jumuah)
            .putString(K_EDGE_STYLE, s.edgeStyle.name)
            .putInt(K_EDGE_BRIGHT, s.edgeBright)
            .putInt(K_EDGE_WARN, s.edgeWarnMin)
            .putBoolean(K_EDGE_MOTION, s.edgeMotion)
            .putBoolean(K_EDGE_RIGHT, s.edgeRight)
            .putString(K_ICON_TINT, s.iconTint.name)
            .putString("accent_style", s.accentStyle.name)
            .putBoolean("senior_mode", s.seniorMode)
            .putString("senior_apps", s.seniorApps.joinToString("\n"))
            .putString("senior_call_name", s.seniorCallName)
            .putString("senior_call_number", s.seniorCallNumber)
            .putString("tile_sizes", s.tileSizes.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putString("app_font", s.font.name)
            .putBoolean("notify_light", s.notifyLight)
            .putString("notify_style", s.notifyStyle.name)
            .putBoolean("notify_dot", s.notifyDot)
            .putBoolean("notify_app_dots", s.notifyAppDots)
            .putStringSet("notify_off", s.notifyOff)
            .putString("notify_colors", s.notifyColors.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putStringSet("important_apps", s.importantApps)
            .putString("important_people", s.importantPeople.joinToString("\n"))
            .putString("world_clocks", s.worldClocks.joinToString("\n"))
            .putBoolean("show_projects", s.showProjects)
            .putInt("remind_every", s.remindEvery)
            .putString(K_INFO_PANELS, s.infoPanels.joinToString(",") { it.name })
            .putBoolean(K_SHOW_RECENT, s.showRecent)
            .putString(K_DIAL, s.dialMode.name)
            .putBoolean(K_COMPASS_HAPTICS, s.compassHaptics)
            .putBoolean("tools_on", s.toolsOn)
            .putString(K_TASKS_SRC, s.tasksSource.name)
            .putString(K_CAL_SRC, s.calendarSource.name)
            .putString(K_PROJ_SRC, s.projectsSource.name)
            .apply {
                SyncFeature.entries.forEach { f ->
                    val c = s.sync(f)
                    putBoolean("sync_${f.name}_auto", c.auto)
                    putInt("sync_${f.name}_every", c.everyMin)
                    putBoolean("sync_${f.name}_wifi", c.wifiOnly)
                }
            }
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
            .putBoolean(K_HUB_ON, s.hubOn)
            .apply { GestureSlot.entries.forEach { putString(K_GESTURE + it.name, s.gesture(it)) } }
            .putStringSet(K_HIDDEN, HashSet(s.hidden))
            .putString(K_PINNED, JSONArray(s.pinned).toString())
            .putString(K_FAVORITES, JSONArray(s.favorites).toString())
            .putString(K_FOLDERS, writeFolders(s.folders))
            .putStringSet(K_FOCUS_ALLOWED, HashSet(s.focusAllowed))
            .putLong(K_FOCUS_ENDS, s.focusEndsAt)
            .apply()
    }

    private fun loadSync(f: SyncFeature) = SyncConfig(
        auto = sp.getBoolean("sync_${f.name}_auto", true),
        everyMin = sp.getInt("sync_${f.name}_every", 60),
        wifiOnly = sp.getBoolean("sync_${f.name}_wifi", false),
    )

    fun loadProjects(): List<LocalProject> = sp.getString(K_PROJECTS, null)?.let { raw ->
        runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val steps = o.optJSONArray("steps")
                LocalProject(o.getLong("id"), o.getString("name"), o.optString("next"),
                    steps?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(), o.optInt("done"))
            }
        }.getOrNull()
    } ?: emptyList()

    fun saveProjects(list: List<LocalProject>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("next", it.next)
                .put("steps", JSONArray(it.steps)).put("done", it.done))
        }
        sp.edit().putString(K_PROJECTS, a.toString()).apply()
    }

    /** Private calendar address; kept here only (backups are off). */
    fun icsUrl(): String? = sp.getString(K_ICS_URL, null)
    fun saveIcsUrl(url: String?) = sp.edit().putString(K_ICS_URL, url).apply()

    /** Last successful sync and last error, per outside source ("HUB", "ICS"). */
    fun syncLast(source: String): Long = sp.getLong("sync_last_$source", 0L)
    fun syncError(source: String): String? = sp.getString("sync_err_$source", null)
    fun markSync(source: String, error: String?) {
        val e = sp.edit().putString("sync_err_$source", error)
        if (error == null) e.putLong("sync_last_$source", System.currentTimeMillis())
        e.apply()
    }

    fun loadTasks(): List<TaskItem> {
        val raw = sp.getString(K_TASKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TaskItem(o.getLong("id"), o.getString("text"), o.getBoolean("done"), o.optLong("due", -1L), o.optInt("remind", -1))
            }
        }.getOrDefault(emptyList())
    }

    fun saveTasks(tasks: List<TaskItem>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("due", it.due).put("remind", it.remind)) }
        sp.edit().putString(K_TASKS, arr.toString()).apply()
    }

    /** Widget ids in page order. Ids belong to the system widget host, so they live apart from settings. */
    /** Addresses you replaced (Settings, System, Online sources) and your own gold source. */
    fun loadSources(): SourceConfig = SourceConfig(
        urls = SourceKey.entries.mapNotNull { k -> sp.getString("src_${k.name}", null)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap(),
        gold = CustomGold(
            url = sp.getString("gold_custom_url", "") ?: "",
            path = sp.getString("gold_custom_path", "") ?: "",
            perGram = sp.getBoolean("gold_custom_gram", false),
            currency = sp.getString("gold_custom_cur", "USD") ?: "USD",
        ),
    )

    fun saveSource(k: SourceKey, url: String?) {
        sp.edit().putString("src_${k.name}", url?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }

    fun saveCustomGold(g: CustomGold) {
        sp.edit().putString("gold_custom_url", g.url.trim()).putString("gold_custom_path", g.path.trim())
            .putBoolean("gold_custom_gram", g.perGram).putString("gold_custom_cur", g.currency).apply()
    }

    /** Widget id -> look, stored as "id:style:corners:alpha,...". */
    fun loadWidgetLooks(): Map<Int, WidgetLook> = (sp.getString("widget_looks", null) ?: "").split(",").mapNotNull { part ->
        val f = part.split(":").map { it.toIntOrNull() }
        if (f.size != 4 || f.any { it == null }) null else f[0]!! to WidgetLook(f[1]!!, f[2]!!, f[3]!!)
    }.toMap()

    fun saveWidgetLooks(looks: Map<Int, WidgetLook>) {
        sp.edit().putString("widget_looks",
            looks.entries.joinToString(",") { (id, l) -> "$id:${l.style}:${l.corners}:${l.alpha}" }).apply()
    }

    /** Apps that have shown a notification since the light was turned on (for its settings list). */
    fun notifySeen(): Set<String> = sp.getStringSet("notify_seen", null)?.toSet() ?: emptySet()

    fun addNotifySeen(pkgs: Set<String>) {
        val cur = notifySeen()
        if (!cur.containsAll(pkgs)) sp.edit().putStringSet("notify_seen", cur + pkgs).apply()
    }

    fun loadNotes(): List<Note> = runCatching {
        val a = JSONArray(sp.getString("notes", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Note(it.getLong("id"), it.getString("text"), it.optInt("color", 0)) } }
    }.getOrDefault(emptyList())

    fun saveNotes(notes: List<Note>) {
        val a = JSONArray()
        notes.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("color", it.color)) }
        sp.edit().putString("notes", a.toString()).apply()
    }

    /**
     * The shelves. Widget ids belong to this phone's widget host, so the key starts with "widget_" and is left out of backups.
     * The first time (0.32), notes, tasks and projects become cards on the first shelf, followed by its widgets.
     */
    fun loadShelfPages(): List<ShelfPage> {
        sp.getString("widget_pages", null)?.let { raw ->
            runCatching {
                val a = JSONArray(raw)
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    val items = o.getJSONArray("items")
                    ShelfPage(o.getInt("id"), o.getString("name"), List(items.length()) { j -> items.getInt(j) })
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { pages ->
                val named = pages.mapIndexed { idx, pg ->
                    // Shelves still called "Shelf" or "Shelf 2" take their own short name.
                    val plain = pg.name == "Shelf" || Regex("""Shelf \d+""").matches(pg.name)
                    pg.copy(name = if (plain) SHELF_NAMES.getOrElse(idx) { pg.name } else pg.name,
                        items = pg.items.filter { i -> BuiltIn.of(i)?.retired != true })
                }
                // Three shelves at most. Setups from before the limit: a fourth shelf's things move onto the third.
                val max = SHELF_NAMES.size
                return if (named.size <= max) named else named.take(max).mapIndexed { i, pg ->
                    if (i == max - 1) pg.copy(items = (pg.items + named.drop(max).flatMap { it.items }).distinct()) else pg
                }
            }
        }
        val s = loadSettings()
        val cards = buildList {
            add(BuiltIn.NOTES.id(0))
            if (s.showTasks) add(BuiltIn.TASKS.id(0))
        }
        return listOf(ShelfPage(0, SHELF_NAMES[0], cards + loadWidgetIds()))
    }

    /** Apps opened from search, newest first (app keys). */
    fun searchHistory(): List<String> = sp.getString("search_history", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun saveSearchHistory(keys: List<String>) {
        sp.edit().putString("search_history", keys.joinToString("\n")).apply()
    }

    fun saveShelfPages(pages: List<ShelfPage>) {
        val a = JSONArray()
        pages.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("items", JSONArray(it.items))) }
        sp.edit().putString("widget_pages", a.toString()).apply()
    }

    fun shelfIndex(): Int = sp.getInt("widget_page_index", 0)
    fun saveShelfIndex(i: Int) = sp.edit().putInt("widget_page_index", i).apply()

    /** Countdown cards: card id -> (what for, epoch day). */
    fun loadCountdowns(): Map<Int, Pair<String, Long>> = (sp.getString("countdowns", "") ?: "").split("\n").mapNotNull { line ->
        val p = line.split("|", limit = 3)
        if (p.size < 3) null else {
            val id = p[0].toIntOrNull()
            val day = p[1].toLongOrNull()
            if (id == null || day == null) null else id to (p[2] to day)
        }
    }.toMap()

    fun saveCountdowns(map: Map<Int, Pair<String, Long>>) {
        sp.edit().putString("countdowns", map.entries.joinToString("\n") {
            "${it.key}|${it.value.second}|${it.value.first.replace("\n", " ")}"
        }).apply()
    }

    fun loadSelfHosted(): SelfHostedConfig = SelfHostedConfig(
        haUrl = sp.getString("sh_ha_url", "") ?: "",
        haToken = sp.getString("secret_ha_token", "") ?: "",
        haEntities = (sp.getString("sh_ha_entities", "") ?: "").split("\n").filter { it.isNotBlank() },
        kumaUrl = sp.getString("sh_kuma_url", "") ?: "",
        kumaSlug = sp.getString("sh_kuma_slug", "") ?: "",
        msgKind = runCatching { MsgKind.valueOf(sp.getString("sh_msg_kind", null)!!) }.getOrDefault(MsgKind.OFF),
        msgUrl = sp.getString("sh_msg_url", "") ?: "",
        msgTopic = sp.getString("sh_msg_topic", "") ?: "",
        msgToken = sp.getString("secret_msg_token", "") ?: "",
    )

    fun saveSelfHosted(c: SelfHostedConfig) {
        sp.edit()
            .putString("sh_ha_url", c.haUrl).putString("secret_ha_token", c.haToken)
            .putString("sh_ha_entities", c.haEntities.joinToString("\n"))
            .putString("sh_kuma_url", c.kumaUrl).putString("sh_kuma_slug", c.kumaSlug)
            .putString("sh_msg_kind", c.msgKind.name).putString("sh_msg_url", c.msgUrl)
            .putString("sh_msg_topic", c.msgTopic).putString("secret_msg_token", c.msgToken)
            .apply()
    }

    fun loadWidgetIds(): List<Int> =
        readStringList(sp.getString(K_WIDGET_IDS, null)).mapNotNull { it.toIntOrNull() }

    fun saveWidgetIds(ids: List<Int>) {
        sp.edit().putString(K_WIDGET_IDS, JSONArray(ids.map { it.toString() }).toString()).apply()
    }

    /**
     * Widget id -> (height in dp, width) as chosen in Edit. Width: 0 = full, -1 = the widget's own, more = dp.
     * Stored as "id:height:width"; the 0.23 key held 1/0 for full/own width.
     */
    fun loadWidgetSizes(): Map<Int, Pair<Int, Int>> {
        val v2 = sp.getString("widget_sizes2", null)
        val raw = v2 ?: sp.getString("widget_sizes", null) ?: ""
        return raw.split(",").mapNotNull { part ->
            val f = part.split(":")
            val id = f.getOrNull(0)?.toIntOrNull()
            val h = f.getOrNull(1)?.toIntOrNull()
            val w = f.getOrNull(2)?.toIntOrNull()
            if (f.size != 3 || id == null || h == null || w == null) null
            else id to (h to if (v2 != null) w else if (w == 1) 0 else -1)
        }.toMap()
    }

    fun saveWidgetSizes(sizes: Map<Int, Pair<Int, Int>>) {
        sp.edit().putString("widget_sizes2",
            sizes.entries.joinToString(",") { (id, v) -> "$id:${v.first}:${v.second}" }).apply()
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

    // ---- Project Hub: address + app key live only here (backups are off in the manifest) ----

    fun hubUrl(): String? = sp.getString(K_HUB_URL, null)
    fun hubKey(): String? = sp.getString(K_HUB_KEY, null)

    fun saveHub(url: String, key: String) {
        sp.edit().putString(K_HUB_URL, url).putString(K_HUB_KEY, key).apply()
    }

    fun clearHub() {
        sp.edit().remove(K_HUB_URL).remove(K_HUB_KEY).remove(K_HUB_CACHE).remove(K_HUB_CACHE_AT).apply()
    }

    /** Last answer from the hub, so home shows something instantly and while offline. */
    fun loadHubCache(): HubGlance? {
        val body = sp.getString(K_HUB_CACHE, null) ?: return null
        return HubGlance.parse(body, sp.getLong(K_HUB_CACHE_AT, 0L))
    }

    fun saveHubCache(body: String, at: Long) {
        sp.edit().putString(K_HUB_CACHE, body).putLong(K_HUB_CACHE_AT, at).apply()
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
        const val K_DRAWER_START = "drawer_start"
        const val K_ACCENT = "accent"
        const val K_WIDGET_IDS = "widget_ids"
        const val K_GESTURE = "gesture_"
        const val K_CLOCK = "clock_style"
        const val K_HEADLINE_INTRO = "headline_intro_090"
        const val K_SHOW_STATS = "show_stats"
        const val K_LOCAL_IP = "show_local_ip"
        const val K_PUBLIC_IP = "public_ip_on"
        const val K_PRAYER_ON = "prayer_on"
        const val K_PRAYER_METHOD = "prayer_method"
        const val K_ASR_HANAFI = "prayer_asr_hanafi"
        const val K_HIJRI_ON = "hijri_on"
        const val K_HIJRI_ADJUST = "hijri_adjust"
        const val K_TAMIL_ON = "tamil_on"
        const val K_LOCK_ON = "lock_on"
        const val K_LOCK_PRAYER = "lock_prayer"
        const val K_LOCK_HIJRI = "lock_hijri"
        const val K_LOCK_TAMIL = "lock_tamil"
        const val K_ADHAN_ALERT = "adhan_alert"
        const val K_IQAMA_ALERT = "iqama_alert"
        const val K_IQAMA = "iqama_minutes"
        const val K_PRAYER_POPUP = "prayer_popup"
        const val K_REMIND_BEFORE = "prayer_remind_before"
        const val K_JUMUAH = "prayer_jumuah_uae"
        const val K_EDGE_LIGHT = "edge_light"
        const val K_EDGE_STYLE = "edge_style"
        const val K_EDGE_BRIGHT = "edge_bright"
        const val K_EDGE_WARN = "edge_warn"
        const val K_EDGE_MOTION = "edge_motion"
        const val K_EDGE_RIGHT = "edge_right"
        const val K_ICON_TINT = "icon_tint"
        const val K_INFO_PANEL = "info_panel"
        const val K_INFO_PANELS = "info_panels"
        const val K_SHOW_RECENT = "show_recent"
        const val K_DIAL = "dial_mode"
        const val K_COMPASS_HAPTICS = "compass_haptics"
        const val K_TASKS_SRC = "tasks_source"
        const val K_CAL_SRC = "calendar_source"
        const val K_PROJ_SRC = "projects_source"
        const val K_PROJECTS = "projects"
        const val K_ICS_URL = "ics_url"
        const val K_WEATHER_ON = "weather_card_on"
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
        const val K_HUB_ON = "hub_on"
        const val K_HUB_URL = "hub_url"
        const val K_HUB_KEY = "hub_key"
        const val K_HUB_CACHE = "hub_cache"
        const val K_HUB_CACHE_AT = "hub_cache_at"
    }
}
