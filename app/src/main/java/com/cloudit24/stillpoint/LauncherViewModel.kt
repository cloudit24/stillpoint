package com.cloudit24.stillpoint

import com.cloudit24.stillpoint.data.CalendarSource
import com.cloudit24.stillpoint.data.Ics
import com.cloudit24.stillpoint.data.LocalProject
import com.cloudit24.stillpoint.data.ProjectSource
import com.cloudit24.stillpoint.data.ProviderTasks
import com.cloudit24.stillpoint.data.SyncConfig
import com.cloudit24.stillpoint.data.SyncFeature
import com.cloudit24.stillpoint.data.TaskSource
import com.cloudit24.stillpoint.sync.Sync
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import com.cloudit24.stillpoint.widget.GoldWidget
import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudit24.stillpoint.data.AgendaItem
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.data.AppRepository
import com.cloudit24.stillpoint.data.AppDataUsage
import com.cloudit24.stillpoint.data.CalendarRepository
import com.cloudit24.stillpoint.data.DataUsageRepository
import com.cloudit24.stillpoint.data.City
import com.cloudit24.stillpoint.data.GoldQuote
import com.cloudit24.stillpoint.data.HubGlance
import com.cloudit24.stillpoint.data.HubRepository
import com.cloudit24.stillpoint.data.LiveRepository
import com.cloudit24.stillpoint.data.WeatherNow
import com.cloudit24.stillpoint.data.FavFolder
import com.cloudit24.stillpoint.data.GestureSlot
import com.cloudit24.stillpoint.data.HomeAction
import com.cloudit24.stillpoint.data.GestureTarget
import com.cloudit24.stillpoint.data.HomeMode
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.TaskItem
import com.cloudit24.stillpoint.data.UsageRepository
import com.cloudit24.stillpoint.ui.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, DRAWER, FOCUS, SETTINGS, WIDGETS, DATA, PRAYER }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val appsRepo = AppRepository(app)
    private val usageRepo = UsageRepository(app)
    private val calendarRepo = CalendarRepository(app)
    private val prefs = Prefs(app)
    private val live = LiveRepository()
    private val dataRepo = DataUsageRepository(app)
    private val hubRepo = HubRepository()

    var screen by mutableStateOf(Screen.HOME)
    var blockedMessage by mutableStateOf<String?>(null)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var usage by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    /** packageName -> foreground ms over the last 7 days. Drives the "Most used" tab. */
    var weekUsage by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var agenda by mutableStateOf<List<AgendaItem>>(emptyList())
        private set
    var tasks by mutableStateOf(prefs.loadTasks())
        private set
    var settings by mutableStateOf(prefs.loadSettings())
        private set
    var hasUsageAccess by mutableStateOf(false)
        private set
    /** Incremented on every resume so permission-dependent UI re-reads system state. */
    var resumeTick by mutableIntStateOf(0)
        private set

    val totalUsage: Long get() = usage.values.sum()

    // ---- Weather and gold (opt-in) ----

    var weather by mutableStateOf(prefs.loadWeather())
        private set
    var gold by mutableStateOf(prefs.loadGold())
        private set
    private var liveBusy = false

    /** Fetches only what is switched on and stale. [force] ignores the age check (tap to refresh, settings change). */
    fun refreshLive(force: Boolean = false) {
        if (liveBusy) return
        val s = settings
        val now = System.currentTimeMillis()
        val city = s.city
        val wantWeather = s.weatherOn && city != null &&
            (force || weather.let { it == null || now - it.fetchedAt > WEATHER_MAX_AGE })
        val wantGold = s.goldOn &&
            (force || gold.let {
                it == null || it.currency != s.goldCurrency || it.source != s.goldSource || now - it.fetchedAt > GOLD_MAX_AGE
            })
        if (!wantWeather && !wantGold) return
        liveBusy = true
        viewModelScope.launch {
            val w = if (wantWeather && city != null) withContext(Dispatchers.IO) { live.weather(city) } else null
            val g = if (wantGold) withContext(Dispatchers.IO) { live.gold(s.goldSource, s.goldCurrency) } else null
            if (w != null) { weather = w; prefs.saveWeather(w) }
            if (g != null) { gold = g; prefs.saveGold(g); runCatching { GoldWidget.updateAll(getApplication()) } }
            liveBusy = false
        }
    }

    // ---- Public IP (opt-in) ----

    var publicIp by mutableStateOf<String?>(null)
        private set
    /** Local address the public IP was looked up for; a new one means the network changed. */
    private var publicIpFor: String? = null
    private var publicIpTriedAt = 0L
    private var ipBusy = false

    /** Looks up the public IP only when the network changed, after a failure (once a minute), or on [force]. */
    fun refreshPublicIp(localAddress: String?, force: Boolean = false) {
        if (!settings.publicIpOn || ipBusy) return
        if (localAddress == null) { publicIp = null; publicIpFor = null; return }
        val now = System.currentTimeMillis()
        val changed = localAddress != publicIpFor
        if (!force && !changed && (publicIp != null || now - publicIpTriedAt < 60_000L)) return
        if (changed) publicIp = null
        ipBusy = true
        publicIpTriedAt = now
        viewModelScope.launch {
            val ip = withContext(Dispatchers.IO) { live.publicIp() }
            publicIpFor = localAddress
            if (ip != null || changed) publicIp = ip
            ipBusy = false
        }
    }

    // ---- Project Hub (opt-in, your own server) ----

    var hub by mutableStateOf(prefs.loadHubCache())
        private set
    var hubError by mutableStateOf<String?>(null)
        private set
    /** A stillpoint://hub link was opened (QR code on the hub's "Connect phone" page); waiting for the user's OK. */
    var pendingHubLink by mutableStateOf<Pair<String, String>?>(null)
    /** Tasks the user said "Not now" to on this phone. Forgotten when Stillpoint restarts. */
    private val hubSkipped = mutableSetOf<String>()
    private var hubBusy = false

    fun hubUrl(): String? = prefs.hubUrl()
    fun hubConnected(): Boolean = prefs.hubUrl() != null && prefs.hubKey() != null

    /** Asks the hub only when shown, connected, and the last answer is older than 2 minutes (or [force]). */
    fun refreshHub(force: Boolean = false) {
        val url = prefs.hubUrl() ?: return
        val key = prefs.hubKey() ?: return
        if (!usesHub() || hubBusy) return
        if (!force) {
            val every = hubAutoInterval() ?: return
            if (hub.let { it != null && System.currentTimeMillis() - it.fetchedAt < every }) return
        }
        hubBusy = true
        viewModelScope.launch {
            val skip = hubSkipped.toSet()
            when (val r = withContext(Dispatchers.IO) { hubRepo.glance(url, key, skip) }) {
                is HubRepository.Result.Ok -> {
                    val at = System.currentTimeMillis()
                    HubGlance.parse(r.value, at)?.let {
                        hub = it
                        prefs.saveHubCache(r.value, at)
                        prefs.markSync(Sync.HUB, null)
                        hubError = null
                        if (settings.calendarSource == CalendarSource.HUB && settings.showAgenda) agenda = hubAgenda()
                        syncTick++
                    } ?: run { hubError = "The hub sent something Stillpoint doesn't understand. Update both." }
                }
                is HubRepository.Result.Failed -> {
                    hubError = r.reason
                    prefs.markSync(Sync.HUB, r.reason)
                }
            }
            hubBusy = false
        }
    }

    fun hubDone(id: String, done: Boolean = true) = hubWrite { url, key -> hubRepo.setDone(url, key, id, done) }

    fun hubAdd(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) hubWrite { url, key -> hubRepo.addTask(url, key, t) }
    }

    fun hubNotNow(id: String) {
        hubSkipped += id
        refreshHub(force = true)
    }

    private fun hubWrite(action: (String, String) -> HubRepository.Result<String>) {
        val url = prefs.hubUrl() ?: return
        val key = prefs.hubKey() ?: return
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { action(url, key) }) {
                is HubRepository.Result.Ok -> refreshHub(force = true)
                is HubRepository.Result.Failed -> blockedMessage = r.reason
            }
        }
    }

    /** Checks the address and key with a real request before saving them. Returns null on success, else why not. */
    suspend fun connectHub(rawUrl: String, key: String): String? {
        var url = rawUrl.trim().trimEnd('/')
        if (url.isEmpty() || key.isBlank()) return "Enter the address and the app key."
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return when (val r = withContext(Dispatchers.IO) { hubRepo.glance(url, key.trim(), emptySet()) }) {
            is HubRepository.Result.Failed -> r.reason
            is HubRepository.Result.Ok -> {
                val at = System.currentTimeMillis()
                prefs.saveHub(url, key.trim())
                prefs.saveHubCache(r.value, at)
                hub = HubGlance.parse(r.value, at)
                hubError = null
                // A new hub is used for tasks and projects right away; calendar stays as chosen.
                updateSettings { it.copy(hubOn = true, tasksSource = TaskSource.HUB, projectsSource = ProjectSource.HUB) }
                prefs.markSync(Sync.HUB, null)
                Sync.schedule(getApplication())
                null
            }
        }
    }

    fun disconnectHub() {
        prefs.clearHub()
        hub = null
        hubError = null
        hubSkipped.clear()
        updateSettings {
            it.copy(
                hubOn = false,
                tasksSource = if (it.tasksSource == TaskSource.HUB) TaskSource.PHONE else it.tasksSource,
                calendarSource = if (it.calendarSource == CalendarSource.HUB) CalendarSource.PHONE else it.calendarSource,
                projectsSource = if (it.projectsSource == ProjectSource.HUB) ProjectSource.PHONE else it.projectsSource,
            )
        }
        Sync.schedule(getApplication())
    }

    // ---- Tasks, calendar and projects: where they come from and how they sync ----

    private val providerRepo = ProviderTasks(app)
    var providerTasks by mutableStateOf<List<TaskItem>>(emptyList())
        private set
    var providerError by mutableStateOf<String?>(null)
        private set
    var projects by mutableStateOf(prefs.loadProjects())
        private set
    /** Bumped after every sync so Settings re-reads the last-sync times. */
    var syncTick by mutableIntStateOf(0)
        private set
    var syncing by mutableStateOf(false)
        private set

    fun usesHub(): Boolean = settings.let {
        it.tasksSource == TaskSource.HUB || it.calendarSource == CalendarSource.HUB || it.projectsSource == ProjectSource.HUB
    }

    /** Shortest auto-sync interval among the pages using the hub, or null when none sync automatically. */
    private fun hubAutoInterval(): Long? = SyncFeature.entries
        .filter { Sync.sourceOf(settings, it) == Sync.HUB && settings.sync(it).auto }
        .minOfOrNull { settings.sync(it).everyMin * 60_000L }

    fun syncLast(source: String): Long = prefs.syncLast(source)
    fun syncError(source: String): String? = prefs.syncError(source)

    fun syncNow(feature: SyncFeature) {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            Sync.run(getApplication(), force = true, only = feature)
            hub = prefs.loadHubCache()
            hubError = prefs.syncError(Sync.HUB)
            if (settings.tasksSource.authority != null) loadProviderTasks()
            agenda = loadAgenda()
            syncing = false
            syncTick++
        }
    }

    fun setSync(feature: SyncFeature, transform: (SyncConfig) -> SyncConfig) {
        updateSettings { s ->
            when (feature) {
                SyncFeature.TASKS -> s.copy(tasksSync = transform(s.tasksSync))
                SyncFeature.CALENDAR -> s.copy(calendarSync = transform(s.calendarSync))
                SyncFeature.PROJECTS -> s.copy(projectsSync = transform(s.projectsSync))
            }
        }
        Sync.schedule(getApplication())
    }

    fun setTaskSource(src: TaskSource) {
        updateSettings { it.copy(tasksSource = src) }
        Sync.schedule(getApplication())
        if (src.authority != null) loadProviderTasks()
        if (src == TaskSource.HUB) refreshHub(force = true)
    }

    fun setCalendarSource(src: CalendarSource) {
        updateSettings { it.copy(calendarSource = src) }
        Sync.schedule(getApplication())
        if (src == CalendarSource.PHONE) refresh() else syncNow(SyncFeature.CALENDAR)
    }

    fun setProjectSource(src: ProjectSource) {
        updateSettings { it.copy(projectsSource = src) }
        Sync.schedule(getApplication())
        if (src == ProjectSource.HUB) refreshHub(force = true)
    }

    fun providerInstalled(src: TaskSource): Boolean = providerRepo.installed(src)
    fun providerPermitted(src: TaskSource): Boolean = providerRepo.hasPermission(src)

    fun loadProviderTasks() {
        val src = settings.tasksSource
        if (src.authority == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { providerRepo.open(src) } }
                .onSuccess { providerTasks = it; providerError = null }
                .onFailure { providerError = it.message ?: "Couldn't read ${src.label}." }
        }
    }

    fun providerDone(id: Long) = providerWrite { providerRepo.setDone(it, id, true) }

    fun providerAdd(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) providerWrite { providerRepo.add(it, t) }
    }

    private fun providerWrite(action: (TaskSource) -> Unit) {
        val src = settings.tasksSource
        if (src.authority == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { action(src) } }
                .onFailure { blockedMessage = "${src.label} didn't accept the change: ${it.message ?: "unknown reason"}" }
            loadProviderTasks()
        }
    }

    fun icsUrl(): String? = prefs.icsUrl()

    fun setIcsUrl(url: String) {
        val u = url.trim()
        prefs.saveIcsUrl(u.ifEmpty { null })
        if (u.isNotEmpty()) syncNow(SyncFeature.CALENDAR)
    }

    /** Today's remaining events from the chosen calendar source. */
    private suspend fun loadAgenda(): List<AgendaItem> {
        if (!settings.showAgenda) return emptyList()
        return when (settings.calendarSource) {
            CalendarSource.PHONE -> withContext(Dispatchers.IO) { calendarRepo.today() }
            CalendarSource.HUB -> hubAgenda()
            CalendarSource.ICS -> withContext(Dispatchers.IO) {
                val f = Sync.icsFile(getApplication())
                if (!f.exists()) emptyList() else runCatching {
                    val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Ics.events(f.readText(), System.currentTimeMillis(), end)
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun hubAgenda(): List<AgendaItem> {
        val g = hub ?: return emptyList()
        val title = g.nextEventTitle?.takeIf { it.isNotBlank() } ?: return emptyList()
        val ms = g.nextEventStart?.let { s ->
            runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        }
        return listOf(AgendaItem(title, ms ?: 0L, ms ?: 0L, allDay = ms == null))
    }

    fun addProject(name: String, next: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        projects = projects + LocalProject(System.currentTimeMillis(), n, next.trim())
        prefs.saveProjects(projects)
    }

    fun updateProject(p: LocalProject) {
        projects = projects.map { if (it.id == p.id) p.copy(name = p.name.trim(), next = p.next.trim()) else it }
        prefs.saveProjects(projects)
    }

    fun deleteProject(id: Long) {
        projects = projects.filter { it.id != id }
        prefs.saveProjects(projects)
    }

    /** Clears the next step; the project moves to the end until a new step is written. */
    fun projectStepDone(id: Long) {
        val p = projects.find { it.id == id } ?: return
        projects = projects.filter { it.id != id } + p.copy(next = "")
        prefs.saveProjects(projects)
    }

    fun projectLater(id: Long) {
        val p = projects.find { it.id == id } ?: return
        projects = projects.filter { it.id != id } + p
        prefs.saveProjects(projects)
    }

    suspend fun dataUsage(start: Long, end: Long): List<AppDataUsage> {
        val labels = apps.associate { it.packageName to it.label }
        return withContext(Dispatchers.IO) { dataRepo.query(start, end) { labels[it] } }
    }

    suspend fun searchCities(query: String): List<City> = withContext(Dispatchers.IO) { live.searchCity(query) }

    fun setCity(city: City, enableWeather: Boolean = true) {
        updateSettings { it.copy(city = city, weatherOn = it.weatherOn || enableWeather) }
        weather = null
        prefs.saveWeather(null)
        refreshLive(force = true)
    }

    // ---- Widgets ----

    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(app)
    val widgetHost = AppWidgetHost(app, WIDGET_HOST_ID)
    var widgetIds by mutableStateOf(prefs.loadWidgetIds())
        private set
    /** Id allocated while the bind / configure screens are open. Lives here so it survives activity recreation. */
    var pendingWidgetId = -1
        private set

    fun widgetProviders(): List<AppWidgetProviderInfo> {
        val pm = getApplication<Application>().packageManager
        return runCatching { widgetManager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(pm).lowercase() }
    }

    fun allocateWidgetId(): Int = widgetHost.allocateAppWidgetId().also { pendingWidgetId = it }

    fun commitPendingWidget() {
        if (pendingWidgetId != -1) {
            widgetIds = widgetIds + pendingWidgetId
            prefs.saveWidgetIds(widgetIds)
        }
        pendingWidgetId = -1
    }

    fun cancelPendingWidget() {
        if (pendingWidgetId != -1) runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
        pendingWidgetId = -1
    }

    fun removeWidget(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
        widgetIds = widgetIds - id
        prefs.saveWidgetIds(widgetIds)
    }

    // ---- Icons ----

    private val iconCache = HashMap<String, ImageBitmap>()

    fun cachedIcon(app: AppEntry): ImageBitmap? = iconCache[app.key]

    suspend fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? {
        iconCache[app.key]?.let { return it }
        val bmp = withContext(Dispatchers.IO) { appsRepo.loadIcon(app, sizePx)?.asImageBitmap() } ?: return null
        iconCache[app.key] = bmp
        return bmp
    }

    fun refresh() {
        resumeTick++
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    apps = appsRepo.loadApps(),
                    usageAccess = usageRepo.hasPermission(),
                    usage = usageRepo.todayUsage(),
                    weekUsage = usageRepo.weekUsage(),
                )
            }
            apps = loaded.apps
            hasUsageAccess = loaded.usageAccess
            usage = loaded.usage
            weekUsage = loaded.weekUsage
            agenda = loadAgenda()
            refreshLive()
            refreshHub()
            Sync.schedule(getApplication())
            if (settings.tasksSource.authority != null) loadProviderTasks()
            if (settings.calendarSource == CalendarSource.ICS) {
                Sync.run(getApplication(), force = false, only = SyncFeature.CALENDAR)
                agenda = loadAgenda()
                syncTick++
            }
            if (settings.focusEndsAt in 1..System.currentTimeMillis()) {
                updateSettings { it.copy(focusEndsAt = 0L) }
            }
        }
    }

    private data class Loaded(
        val apps: List<AppEntry>,
        val usageAccess: Boolean,
        val usage: Map<String, Long>,
        val weekUsage: Map<String, Long>,
    )

    private companion object {
        const val WIDGET_HOST_ID = 1024
        const val LIST_LIMIT = 30
        const val WEATHER_MAX_AGE = 30 * 60_000L
        const val GOLD_MAX_AGE = 15 * 60_000L
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        settings = transform(settings)
        prefs.saveSettings(settings)
    }

    // ---- App lists ----

    fun isFocusActive(now: Long = System.currentTimeMillis()): Boolean = settings.focusEndsAt > now

    /** Non-hidden apps; during focus, only allowed apps. */
    fun visibleApps(): List<AppEntry> {
        val base = apps.filter { it.key !in settings.hidden }
        return if (isFocusActive()) base.filter { it.key in settings.focusAllowed } else base
    }

    fun homeApps(): List<AppEntry> {
        val visible = visibleApps()
        val pinned = settings.pinned.mapNotNull { k -> visible.find { it.key == k } }
        if (settings.homeMode == HomeMode.PINNED) return pinned
        val auto = visible
            .filter { (usage[it.packageName] ?: 0L) > 0L }
            .sortedByDescending { usage[it.packageName] ?: 0L }
            .distinctBy { it.packageName }
            .take(settings.homeCount)
        return auto.ifEmpty { pinned }
    }

    fun mostUsedApps(): List<AppEntry> = visibleApps()
        .filter { (weekUsage[it.packageName] ?: 0L) > 0L }
        .sortedByDescending { weekUsage[it.packageName] ?: 0L }
        .distinctBy { it.packageName }
        .take(LIST_LIMIT)

    fun recentApps(): List<AppEntry> = visibleApps()
        .sortedByDescending { it.installedAt }
        .take(LIST_LIMIT)

    // ---- Favorites and folders ----

    private fun resolve(keys: List<String>): List<AppEntry> {
        val visible = visibleApps().associateBy { it.key }
        return keys.mapNotNull { visible[it] }
    }

    /** Favorites outside any folder, in the order they were added; respects hidden and focus. */
    fun favoriteApps(): List<AppEntry> = resolve(settings.favorites)

    fun folderApps(folder: FavFolder): List<AppEntry> = resolve(folder.apps)

    fun isFavorite(app: AppEntry): Boolean =
        app.key in settings.favorites || settings.folders.any { app.key in it.apps }

    fun addFavorite(app: AppEntry) {
        if (!isFavorite(app)) updateSettings { it.copy(favorites = it.favorites + app.key) }
    }

    fun removeFavorite(app: AppEntry) = updateSettings { s ->
        s.copy(favorites = s.favorites - app.key, folders = s.folders.map { it.copy(apps = it.apps - app.key) })
    }

    /** Moves [app] into a folder, or back to loose favorites when [folderId] is null. */
    fun moveToFolder(app: AppEntry, folderId: Long?) = updateSettings { s ->
        val cleared = s.folders.map { it.copy(apps = it.apps - app.key) }
        if (folderId == null) {
            s.copy(favorites = (s.favorites - app.key) + app.key, folders = cleared)
        } else {
            s.copy(
                favorites = s.favorites - app.key,
                folders = cleared.map { if (it.id == folderId) it.copy(apps = it.apps + app.key) else it },
            )
        }
    }

    fun createFolder(name: String): Long? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val id = System.currentTimeMillis()
        updateSettings { it.copy(folders = it.folders + FavFolder(id, n, emptyList())) }
        return id
    }

    fun renameFolder(id: Long, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        updateSettings { s -> s.copy(folders = s.folders.map { if (it.id == id) it.copy(name = n) else it }) }
    }

    /** Apps inside go back to loose favorites rather than disappearing. */
    fun deleteFolder(id: Long) = updateSettings { s ->
        val folder = s.folders.find { it.id == id } ?: return@updateSettings s
        s.copy(folders = s.folders - folder, favorites = s.favorites + folder.apps.filter { it !in s.favorites })
    }

    fun launch(app: AppEntry) {
        if (isFocusActive() && app.key !in settings.focusAllowed) {
            blockedMessage = "${app.label} is blocked until ${formatClock(getApplication(), settings.focusEndsAt)}."
            return
        }
        if (!appsRepo.launch(app)) blockedMessage = "${app.label} could not be opened."
    }

    // ---- Gestures and shortcuts ----

    fun setGesture(slot: GestureSlot, target: String) = updateSettings { it.copy(gestures = it.gestures + (slot to target)) }

    /** Display name for a target, or null for "Nothing" / an app that is gone. */
    fun targetLabel(target: String): String? {
        GestureTarget.actionOf(target)?.let { return if (it == HomeAction.NONE) null else it.label }
        val key = GestureTarget.appKeyOf(target) ?: return null
        return apps.find { it.key == key }?.label
    }

    /** Screen changes happen here; intents and the gesture service are handled by the UI (needs a Context). */
    fun appForTarget(target: String): AppEntry? = GestureTarget.appKeyOf(target)?.let { k -> apps.find { it.key == k } }

    fun openAppInfo(app: AppEntry) = appsRepo.openAppInfo(app)
    fun uninstall(app: AppEntry) = appsRepo.uninstall(app)

    fun togglePin(app: AppEntry) = updateSettings { s ->
        s.copy(pinned = if (app.key in s.pinned) s.pinned - app.key else s.pinned + app.key)
    }

    fun hide(app: AppEntry) = updateSettings { s ->
        s.copy(
            hidden = s.hidden + app.key,
            pinned = s.pinned - app.key,
            favorites = s.favorites - app.key,
            folders = s.folders.map { it.copy(apps = it.apps - app.key) },
        )
    }

    fun unhide(key: String) = updateSettings { it.copy(hidden = it.hidden - key) }

    // ---- Focus ----

    fun toggleFocusAllowed(app: AppEntry) = updateSettings { s ->
        s.copy(focusAllowed = if (app.key in s.focusAllowed) s.focusAllowed - app.key else s.focusAllowed + app.key)
    }

    fun startFocus(minutes: Int) = updateSettings {
        it.copy(focusEndsAt = System.currentTimeMillis() + minutes * 60_000L)
    }

    fun endFocus() = updateSettings { it.copy(focusEndsAt = 0L) }

    // ---- Tasks ----

    fun addTask(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        tasks = tasks + TaskItem(System.currentTimeMillis(), t, false)
        prefs.saveTasks(tasks)
    }

    fun toggleTask(id: Long) {
        tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it }
        prefs.saveTasks(tasks)
    }

    fun deleteTask(id: Long) {
        tasks = tasks.filterNot { it.id == id }
        prefs.saveTasks(tasks)
    }
}
