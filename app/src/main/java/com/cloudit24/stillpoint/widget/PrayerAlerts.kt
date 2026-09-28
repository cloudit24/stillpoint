package com.cloudit24.stillpoint.widget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.Prefs
import java.time.LocalDate
import java.util.Date

/**
 * Prayer time and iqama alerts. One exact alarm at a time, set for the next alert only;
 * when it fires, the alert is shown and the following one is set. No background service.
 */
object PrayerAlerts {
    private const val CH_PRAYER = "prayer_alert"
    private const val CH_IQAMA = "iqama_alert"
    private const val ID = 4202
    private const val EXTRA_PRAYER = "prayer"
    private const val EXTRA_IQAMA = "iqama"
    private const val EXTRA_AT = "at"
    private const val EXTRA_PRAYER_AT = "prayer_at"

    private data class Alert(val prayer: Prayer, val iqama: Boolean, val at: Long, val prayerAt: Long)

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_PRAYER, "Prayer time", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "At each prayer time. Change the sound here, for example to an adhan recording."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_IQAMA, "Iqama", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When the iqama is due."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        ))
    }

    /** Android's own page for the prayer alert's sound and vibration. */
    fun soundSettings(context: Context): Intent {
        channels(context)
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CH_PRAYER)
    }

    private fun next(s: LauncherSettings, now: Long): Alert? {
        val city = s.city ?: return null
        if (!s.adhanAlert && !s.iqamaAlert) return null
        val today = LocalDate.now()
        return (-1L..1L).flatMap { d ->
            PrayerTimes.forDate(today.plusDays(d), city.lat, city.lon, s.prayerMethod, s.asrHanafi).entries
                .filter { it.key.isPrayer }
                .flatMap { (p, t) ->
                    listOfNotNull(
                        if (s.adhanAlert) Alert(p, false, t, t) else null,
                        if (s.iqamaAlert) Alert(p, true, t + s.iqamaMin(p) * 60_000L, t) else null,
                    )
                }
        }.filter { it.at > now }.minByOrNull { it.at }
    }

    /** Sets the alarm for the next alert, or clears it when alerts are off. Cheap to call often. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, PrayerAlertReceiver::class.java)
        val a = next(Prefs(context).loadSettings(), System.currentTimeMillis())
        if (a == null) {
            am.cancel(PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return
        }
        intent.putExtra(EXTRA_PRAYER, a.prayer.name).putExtra(EXTRA_IQAMA, a.iqama)
            .putExtra(EXTRA_AT, a.at).putExtra(EXTRA_PRAYER_AT, a.prayerAt)
        val pi = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            if (exact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, a.at, 60_000L, pi)
        }
    }

    fun exact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun fire(context: Context, intent: Intent) {
        val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(EXTRA_PRAYER)!!) }.getOrNull() ?: return
        val iqama = intent.getBooleanExtra(EXTRA_IQAMA, false)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val prayerAt = intent.getLongExtra(EXTRA_PRAYER_AT, at)
        val now = System.currentTimeMillis()
        if (now - at > 20 * 60_000L) return // The phone was off; too late to be useful.
        val s = Prefs(context).loadSettings()
        if (if (iqama) !s.iqamaAlert else !s.adhanAlert) return
        if (!LockNotification.canPost(context)) return
        channels(context)

        val time = DateFormat.getTimeFormat(context).format(Date(prayerAt))
        val iqamaAt = prayerAt + s.iqamaMin(prayer) * 60_000L
        val b = Notification.Builder(context, if (iqama) CH_IQAMA else CH_PRAYER)
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(StillpointWidget.openApp(context))
            .setAutoCancel(true)
        if (iqama) {
            b.setContentTitle("Iqama · ${prayer.label}")
                .setContentText("${prayer.arabic} · the prayer is starting")
                .setTimeoutAfter(20 * 60_000L)
        } else if (s.iqamaAlert) {
            // Android counts down to the iqama on its own.
            b.setContentTitle("${prayer.label} · $time")
                .setContentText("${prayer.arabic} · iqama in ${s.iqamaMin(prayer)} min")
                .setWhen(iqamaAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setTimeoutAfter((iqamaAt - now).coerceAtLeast(60_000L))
        } else {
            b.setContentTitle("${prayer.label} · $time")
                .setContentText("${prayer.arabic} · it's time to pray")
                .setTimeoutAfter(30 * 60_000L)
        }
        context.getSystemService(NotificationManager::class.java)?.notify(ID, b.build())
    }
}

/** Fires at each prayer time and iqama the user asked to be alerted for. */
class PrayerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { PrayerAlerts.fire(context, intent) }
        Refresh.all(context)
    }
}
