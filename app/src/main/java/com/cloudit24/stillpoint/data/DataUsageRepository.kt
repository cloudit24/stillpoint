package com.cloudit24.stillpoint.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process

/** Data used by one app (or system group) in a period, in bytes. */
data class AppDataUsage(val packageName: String?, val label: String, val wifi: Long, val mobile: Long) {
    val total: Long get() = wifi + mobile
}

/**
 * Reads the per-app traffic Android already records for its own Data usage screen.
 * Nothing is tracked by Stillpoint; this is a one-off query when the screen opens.
 * Needs usage access (the same permission as screen time). Call from a background thread.
 */
class DataUsageRepository(private val context: Context) {
    private val nsm = context.getSystemService(NetworkStatsManager::class.java)

    /** [labelFor] maps a package name to a display name (null = unknown). */
    fun query(start: Long, end: Long, labelFor: (String) -> String?): List<AppDataUsage> {
        val byUid = HashMap<Int, LongArray>() // [wifi, mobile]
        @Suppress("DEPRECATION")
        collect(ConnectivityManager.TYPE_WIFI, 0, start, end, byUid)
        @Suppress("DEPRECATION")
        collect(ConnectivityManager.TYPE_MOBILE, 1, start, end, byUid)

        val pm = context.packageManager
        return byUid.map { (uid, bytes) ->
            val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
            val label = when (uid) {
                NetworkStats.Bucket.UID_REMOVED -> "Removed apps"
                NetworkStats.Bucket.UID_TETHERING -> "Hotspot and tethering"
                Process.SYSTEM_UID, 0 -> "Android system"
                else -> pkg?.let { labelFor(it) } ?: pkg ?: "Other (uid $uid)"
            }
            AppDataUsage(pkg, label, bytes[0], bytes[1])
        }
            // Several system uids share one name; merge them.
            .groupBy { it.label }
            .map { (label, rows) ->
                AppDataUsage(rows.first().packageName, label, rows.sumOf { it.wifi }, rows.sumOf { it.mobile })
            }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }
    }

    private fun collect(type: Int, slot: Int, start: Long, end: Long, into: HashMap<Int, LongArray>) {
        val stats = runCatching { nsm.querySummary(type, null, start, end) }.getOrNull() ?: return
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                into.getOrPut(bucket.uid) { LongArray(2) }[slot] += bucket.rxBytes + bucket.txBytes
            }
        } finally {
            stats.close()
        }
    }
}
