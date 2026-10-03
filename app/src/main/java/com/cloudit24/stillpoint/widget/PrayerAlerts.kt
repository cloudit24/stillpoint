package com.cloudit24.stillpoint.widget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.ui.PrayerPopupActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Prayer alerts: a heads-up before, the adhan, and the iqama. One exact alarm at a time, set for the
 * next alert only; when it fires, the alert is shown and the following one is set. No background service.
 * The adhan and iqama can open a full-screen popup over the lock screen, like an alarm clock.
 */
object PrayerAlerts {
    private const val CH_PRAYER = "prayer_alert"
    private const val CH_IQAMA = "iqama_alert"
    private const val CH_BEFORE = "prayer_before"
    const val ID = 4202
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_KIND = "kind"
    const val EXTRA_AT = "at"
    const val EXTRA_PRAYER_AT = "prayer_at"
    private const val ACTION_SNOOZE = "com.cloudit24.stillpoint.PRAYER_SNOOZE"
    private const val ACTION_UNSILENCE = "com.cloudit24.stillpoint.PRAYER_UNSILENCE"
    private const val K_SILENCED = "prayer_silenced"
    const val SNOOZE_MIN = 5

    enum class Kind { BEFORE, ADHAN, IQAMA }

    private data class Alert(val prayer: Prayer, val kind: Kind, val at: Long, val prayerAt: Long)

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
            NotificationChannel(CH_BEFORE, "Before prayer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A few minutes before each prayer time."
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

    /** Android 14+ asks the user before an app may show full-screen alerts. */
    fun canPopup(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    fun popupSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The five prayers for [date]; on Fridays in the UAE the fixed Jumu'ah time replaces Dhuhr. */
    private fun times(s: LauncherSettings, date: LocalDate): Map<Prayer, Long> {
        val city = s.city ?: return emptyMap()
        val t = PrayerTimes.forDate(date, city.lat, city.lon, s.prayerMethod, s.asrHanafi).filterKeys { it.isPrayer }
        val jumuah = s.jumuahAt
        if (jumuah == null || date.dayOfWeek != DayOfWeek.FRIDAY) return t
        val at = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + jumuah * 60_000L
        return t + (Prayer.DHUHR to at)
    }

    fun iqamaAt(s: LauncherSettings, prayer: Prayer, prayerAt: Long): Long = prayerAt + s.iqamaMin(prayer) * 60_000L

    /** "Remind in 5 min" only while the iqama is more than 5 minutes away, so it can't make you miss it. */
    fun canSnooze(s: LauncherSettings, prayer: Prayer, prayerAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        iqamaAt(s, prayer, prayerAt) - now > (SNOOZE_MIN + 1) * 60_000L

    private fun next(s: LauncherSettings, now: Long): Alert? {
        if (s.city == null) return null
        if (!s.adhanAlert && !s.iqamaAlert && s.remindBefore <= 0) return null
        val today = LocalDate.now()
        return (-1L..1L).flatMap { d ->
            times(s, today.plusDays(d)).flatMap { (p, t) ->
                listOfNotNull(
                    if (s.remindBefore > 0) Alert(p, Kind.BEFORE, t - s.remindBefore * 60_000L, t) else null,
                    if (s.adhanAlert) Alert(p, Kind.ADHAN, t, t) else null,
                    if (s.iqamaAlert) Alert(p, Kind.IQAMA, iqamaAt(s, p, t), t) else null,
                )
            }
        }.filter { it.at > now }.minByOrNull { it.at }
    }

    private fun pending(context: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            if (exact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
        }
    }

    /** Sets the alarm for the next alert, or clears it when alerts are off. Cheap to call often. */
    fun schedule(context: Context) {
        val intent = Intent(context, PrayerAlertReceiver::class.java)
        val a = next(Prefs(context).loadSettings(), System.currentTimeMillis())
        if (a == null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, 2, intent))
            return
        }
        intent.putExtra(EXTRA_PRAYER, a.prayer.name).putExtra(EXTRA_KIND, a.kind.name)
            .putExtra(EXTRA_AT, a.at).putExtra(EXTRA_PRAYER_AT, a.prayerAt)
        setAlarm(context, a.at, pending(context, 2, intent))
    }

    fun exact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Shows the adhan alert again in 5 minutes, unless the iqama is too near by then. */
    fun snooze(context: Context, prayer: Prayer, prayerAt: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
        val s = Prefs(context).loadSettings()
        if (!canSnooze(s, prayer, prayerAt)) return
        val at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        val intent = Intent(context, PrayerAlertReceiver::class.java)
            .putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_KIND, Kind.ADHAN.name)
            .putExtra(EXTRA_AT, at).putExtra(EXTRA_PRAYER_AT, prayerAt)
        setAlarm(context, at, pending(context, 5, intent))
    }

    /** v0.41.0 could turn on Do Not Disturb at the iqama; this undoes one still pending after updating. */
    private fun unsilence(context: Context) {
        val sp = context.getSharedPreferences("stillpoint", Context.MODE_PRIVATE)
        if (!sp.getBoolean(K_SILENCED, false)) return
        sp.edit().remove(K_SILENCED).apply()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }

    fun handle(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UNSILENCE -> unsilence(context)
            ACTION_SNOOZE -> {
                val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(EXTRA_PRAYER)!!) }.getOrNull() ?: return
                snooze(context, prayer, intent.getLongExtra(EXTRA_PRAYER_AT, 0L))
            }
            else -> fire(context, intent)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(EXTRA_PRAYER)!!) }.getOrNull() ?: return
        val kind = runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND)!!) }.getOrDefault(Kind.ADHAN)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val prayerAt = intent.getLongExtra(EXTRA_PRAYER_AT, at)
        val now = System.currentTimeMillis()
        if (now - at > 20 * 60_000L) return // The phone was off; too late to be useful.
        val s = Prefs(context).loadSettings()
        when (kind) {
            Kind.BEFORE -> if (s.remindBefore <= 0) return
            Kind.ADHAN -> if (!s.adhanAlert) return
            Kind.IQAMA -> if (!s.iqamaAlert) return
        }
        if (!LockNotification.canPost(context)) return
        channels(context)

        val name = if (prayer == Prayer.DHUHR && s.jumuahAt != null &&
            LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY) "Jumu'ah" else prayer.label
        val time = DateFormat.getTimeFormat(context).format(Date(prayerAt))
        val iqamaAt = iqamaAt(s, prayer, prayerAt)
        val channel = when (kind) { Kind.BEFORE -> CH_BEFORE; Kind.ADHAN -> CH_PRAYER; Kind.IQAMA -> CH_IQAMA }
        val b = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setCategory(if (kind == Kind.BEFORE) Notification.CATEGORY_REMINDER else Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(StillpointWidget.openApp(context))
            .setAutoCancel(true)
        when {
            kind == Kind.BEFORE -> b.setContentTitle("$name · $time")
                .setContentText("${prayer.arabic} · in ${s.remindBefore} min")
                .setTimeoutAfter((prayerAt - now).coerceAtLeast(60_000L))
            kind == Kind.IQAMA -> b.setContentTitle("Iqama · $name")
                .setContentText("${prayer.arabic} · the prayer is starting")
                .setTimeoutAfter(20 * 60_000L)
            s.iqamaAlert -> b.setContentTitle("$name · $time")
                // Android counts down to the iqama on its own.
                .setContentText("${prayer.arabic} · iqama in ${s.iqamaMin(prayer)} min")
                .setWhen(iqamaAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setTimeoutAfter((iqamaAt - now).coerceAtLeast(60_000L))
            else -> b.setContentTitle("$name · $time")
                .setContentText("${prayer.arabic} · it's time to pray")
                .setTimeoutAfter(30 * 60_000L)
        }
        if (kind == Kind.ADHAN && canSnooze(s, prayer, prayerAt, now)) {
            val snooze = Intent(context, PrayerAlertReceiver::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_PRAYER_AT, prayerAt)
            b.addAction(Notification.Action.Builder(null, "Remind in $SNOOZE_MIN min", pending(context, 8, snooze)).build())
        }
        if (kind != Kind.BEFORE && s.prayerPopup) {
            val popup = Intent(context, PrayerPopupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_PRAYER_AT, prayerAt)
            val pi = PendingIntent.getActivity(context, 7, popup, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.setFullScreenIntent(pi, true).setContentIntent(pi)
        }
        context.getSystemService(NotificationManager::class.java)?.notify(ID, b.build())
    }
}

/** Fires at each prayer alert, and for "Remind in 5 min" and to undo a silence set by v0.41.0. */
class PrayerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { PrayerAlerts.handle(context, intent) }
        Refresh.all(context)
    }
}
