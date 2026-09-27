package com.cloudit24.stillpoint

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
import com.cloudit24.stillpoint.data.CalendarRepository
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

enum class Screen { HOME, DRAWER, FOCUS, SETTINGS, WIDGETS }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val appsRepo = AppRepository(app)
    private val usageRepo = UsageRepository(app)
    private val calendarRepo = CalendarRepository(app)
    private val prefs = Prefs(app)

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
        val wantAgenda = settings.showAgenda
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Loaded(
                    apps = appsRepo.loadApps(),
                    usageAccess = usageRepo.hasPermission(),
                    usage = usageRepo.todayUsage(),
                    weekUsage = usageRepo.weekUsage(),
                    agenda = if (wantAgenda) calendarRepo.today() else emptyList(),
                )
            }
            apps = loaded.apps
            hasUsageAccess = loaded.usageAccess
            usage = loaded.usage
            weekUsage = loaded.weekUsage
            agenda = loaded.agenda
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
        val agenda: List<AgendaItem>,
    )

    private companion object {
        const val WIDGET_HOST_ID = 1024
        const val LIST_LIMIT = 30
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
