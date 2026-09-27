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
                    agenda = if (wantAgenda) calendarRepo.today() else emptyList(),
                )
            }
            apps = loaded.apps
            hasUsageAccess = loaded.usageAccess
            usage = loaded.usage
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
        val agenda: List<AgendaItem>,
    )

    private companion object {
        const val WIDGET_HOST_ID = 1024
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

    /** Favorites in the order they were added; respects hidden and focus. */
    fun favoriteApps(): List<AppEntry> {
        val visible = visibleApps()
        return settings.favorites.mapNotNull { k -> visible.find { it.key == k } }
    }

    fun launch(app: AppEntry) {
        if (isFocusActive() && app.key !in settings.focusAllowed) {
            blockedMessage = "${app.label} is blocked until ${formatClock(getApplication(), settings.focusEndsAt)}."
            return
        }
        if (!appsRepo.launch(app)) blockedMessage = "${app.label} could not be opened."
    }

    fun openAppInfo(app: AppEntry) = appsRepo.openAppInfo(app)
    fun uninstall(app: AppEntry) = appsRepo.uninstall(app)

    fun togglePin(app: AppEntry) = updateSettings { s ->
        s.copy(pinned = if (app.key in s.pinned) s.pinned - app.key else s.pinned + app.key)
    }

    fun toggleFavorite(app: AppEntry) = updateSettings { s ->
        s.copy(favorites = if (app.key in s.favorites) s.favorites - app.key else s.favorites + app.key)
    }

    fun hide(app: AppEntry) = updateSettings { s ->
        s.copy(hidden = s.hidden + app.key, pinned = s.pinned - app.key, favorites = s.favorites - app.key)
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
