package com.cloudit24.stillpoint.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cloudit24.stillpoint.data.CalendarSource
import com.cloudit24.stillpoint.data.Ics
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.SyncFeature
import com.cloudit24.stillpoint.widget.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Syncs the calendar link on its own schedule.
 * Android's WorkManager wakes it at most every 15 minutes, only with a network, and batches it with other apps.
 * The phone calendar syncs itself, so it is not handled here.
 */
object Sync {
    const val ICS = "ICS"
    private const val WORK = "stillpoint-sync"

    fun icsFile(context: Context) = File(context.filesDir, "calendar.ics")

    /** The outside source a page uses, or null when it stays on the phone. */
    fun sourceOf(s: LauncherSettings, f: SyncFeature): String? = when (f) {
        SyncFeature.CALENDAR -> when (s.calendarSource) {
            CalendarSource.ICS -> ICS
            CalendarSource.PHONE -> null
        }
    }

    /** [force] = Sync now: ignores the schedule, auto sync and Wi-Fi settings. [only] limits it to one page. */
    suspend fun run(context: Context, force: Boolean, only: SyncFeature? = null) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()
        val now = System.currentTimeMillis()
        val wifi = onWifi(context)
        val due = HashSet<String>()
        for (f in SyncFeature.entries) {
            if (only != null && f != only) continue
            val src = sourceOf(s, f) ?: continue
            val cfg = s.sync(f)
            val ok = force || (cfg.auto && (!cfg.wifiOnly || wifi) && now - prefs.syncLast(src) >= cfg.everyMin * 60_000L - 60_000L)
            if (ok) due += src
        }
        if (ICS in due) syncIcs(context, prefs)
        if (due.isNotEmpty()) runCatching { Refresh.all(context) }
    }

    private fun syncIcs(context: Context, prefs: Prefs) {
        val url = prefs.icsUrl()
        if (url == null) {
            prefs.markSync(ICS, "No calendar link set.")
            return
        }
        try {
            icsFile(context).writeText(Ics.fetch(url))
            prefs.markSync(ICS, null)
        } catch (e: Exception) {
            prefs.markSync(ICS, e.message ?: "Couldn't reach the calendar link.")
        }
    }

    /** Starts or stops the background job to match the settings. Cheap to call often. */
    fun schedule(context: Context) {
        val s = Prefs(context).loadSettings()
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val needed = SyncFeature.entries.any { sourceOf(s, it) != null && s.sync(it).auto }
        if (needed) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    private fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Sync.run(applicationContext, force = false)
        return Result.success()
    }
}
