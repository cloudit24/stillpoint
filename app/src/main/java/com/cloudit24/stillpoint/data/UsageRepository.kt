package com.cloudit24.stillpoint.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/**
 * Foreground time since local midnight, computed from raw usage events
 * (resume/pause pairs) rather than daily buckets, which can straddle midnight.
 */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** packageName -> foreground milliseconds over the last 7 days (daily buckets; approximate). */
    fun weekUsage(): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val end = System.currentTimeMillis()
        val stats = runCatching { usm.queryAndAggregateUsageStats(end - 7 * 24 * 3_600_000L, end) }
            .getOrNull() ?: return emptyMap()
        return stats.mapValues { it.value.totalTimeInForeground }
            .filter { it.key != context.packageName && it.value > 0 }
    }

    /** packageName -> foreground milliseconds today. Excludes this launcher. */
    @Suppress("DEPRECATION")
    fun todayUsage(): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()

        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()   // key: package/activity
        val seen = HashSet<String>()
        val totals = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val key = "$pkg/${event.className}"
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumedAt[key] = event.timeStamp
                    seen += key
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = resumedAt.remove(key) ?: if (key !in seen) start else null
                    seen += key
                    if (from != null) totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - from)
                }
            }
        }
        // Still in foreground (normally none while the launcher is visible).
        resumedAt.forEach { (key, from) ->
            val pkg = key.substringBefore('/')
            totals[pkg] = (totals[pkg] ?: 0L) + (end - from)
        }
        totals.remove(context.packageName)
        return totals.filterValues { it > 0 }
    }

    /** Today's opens per app (switching to it from another app) and unlocks (Android 9+; -1 when unknown). */
    @Suppress("DEPRECATION")
    fun todayCounts(): Pair<Map<String, Int>, Int> {
        if (!hasPermission()) return emptyMap<String, Int>() to -1
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = runCatching { usm.queryEvents(start, System.currentTimeMillis()) }.getOrNull() ?: return emptyMap<String, Int>() to -1
        val event = UsageEvents.Event()
        val opens = HashMap<String, Int>()
        var unlocks = 0
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val pkg = event.packageName ?: continue
                    if (pkg != last) opens[pkg] = (opens[pkg] ?: 0) + 1
                    last = pkg
                }
                KEYGUARD_HIDDEN -> unlocks++
            }
        }
        return opens to if (Build.VERSION.SDK_INT >= 28) unlocks else -1
    }

    private companion object {
        /** UsageEvents.Event.KEYGUARD_HIDDEN, Android 9+. */
        const val KEYGUARD_HIDDEN = 18
    }

    /** packageName -> when it was last opened, for apps used in the last 24 hours. Excludes this launcher. */
    @Suppress("DEPRECATION")
    fun lastUsed24h(): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val end = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(end - 24 * 3_600_000L, end) }.getOrNull() ?: return emptyMap()
        val event = UsageEvents.Event()
        val last = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last[pkg] = event.timeStamp
        }
        last.remove(context.packageName)
        return last
    }
}
