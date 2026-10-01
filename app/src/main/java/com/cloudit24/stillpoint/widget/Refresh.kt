package com.cloudit24.stillpoint.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.PrayerTimes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps everything outside the launcher current: the Stillpoint and Gold widgets and the lock screen notification.
 * One inexact alarm, set for the next prayer time or midnight (whichever is first). No background service.
 */
object Refresh {
    fun all(context: Context) {
        runCatching { StillpointWidget.updateAll(context) }
        runCatching { GoldWidget.updateAll(context) }
        runCatching { PrayerWidget.updateAll(context) }
        runCatching { LockNotification.update(context) }
        runCatching { PrayerAlerts.schedule(context) }
        runCatching { TaskAlerts.schedule(context) }
        schedule(context)
    }

    fun schedule(context: Context) {
        val s = Prefs(context).loadSettings()
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nextPrayer = s.city?.let { PrayerTimes.next(now, it.lat, it.lon, s.prayerMethod, s.asrHanafi)?.second }
        val at = minOf(nextPrayer ?: midnight, midnight) + 30_000L
        val intent = PendingIntent.getBroadcast(
            context, 1, Intent(context, RefreshReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at, intent) }
    }
}

/** Fires at prayer times and midnight, and after a reboot or an app update. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Refresh.all(context)
    }
}
