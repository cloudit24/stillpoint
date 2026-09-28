package com.cloudit24.stillpoint.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.PrayerTimes
import java.time.LocalDate
import java.util.Date

/**
 * Quiet notification for the lock screen: next prayer with a live countdown, Hijri and Tamil dates.
 * The countdown is drawn by Android itself, so nothing runs in between updates. Gold is never shown here.
 */
object LockNotification {
    private const val CHANNEL = "lock_screen_info"
    private const val ID = 4201

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun update(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val s = Prefs(context).loadSettings()
        if (!s.lockOn || !canPost(context)) {
            nm.cancel(ID)
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock screen info", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Next prayer and today's dates on the lock screen. Silent."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val city = s.city
        val next = if (s.lockPrayer && city != null) PrayerTimes.next(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi) else null
        val dates = listOfNotNull(
            if (s.lockHijri) Calendars.hijri(today, s.hijriAdjust) else null,
            if (s.lockTamil) Calendars.tamil(today) else null,
        ).joinToString("  ·  ")
        val title = next?.let { (p, at) -> "${p.label}  ${DateFormat.getTimeFormat(context).format(Date(at))}" }
            ?: dates.ifEmpty { "Stillpoint" }
        val text = if (next != null) dates else ""

        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(StillpointWidget.openApp(context))
            .apply {
                if (next != null) {
                    // Android counts down to the prayer on its own.
                    setWhen(next.second)
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
        nm.notify(ID, n)
    }
}
