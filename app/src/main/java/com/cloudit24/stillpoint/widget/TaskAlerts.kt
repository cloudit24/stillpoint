package com.cloudit24.stillpoint.widget

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.TaskItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * Task alerts: a task with its bell on rings at its time on its due day. Bell off, no alert.
 * One exact alarm at a time, set for the next alert only, like the prayer alerts. No background service.
 */
object TaskAlerts {
    private const val CH = "task_alert"
    private const val EXTRA_AT = "at"

    /** When a task should ring, or -1 when it shouldn't. */
    fun at(t: TaskItem): Long =
        if (t.done || t.due < 0 || t.remind < 0) -1L
        else LocalDate.ofEpochDay(t.due).atTime(t.remind / 60, t.remind % 60)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun pending(context: Context, intent: Intent) =
        PendingIntent.getBroadcast(context, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Sets the alarm for the next task alert, or clears it. Cheap to call after every change. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val next = Prefs(context).loadTasks().map { at(it) }.filter { it > now }.minOrNull()
        val intent = Intent(context, TaskAlertReceiver::class.java)
        if (next == null) {
            am.cancel(pending(context, intent))
            return
        }
        val pi = pending(context, intent.putExtra(EXTRA_AT, next))
        runCatching {
            if (PrayerAlerts.exact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, next, 60_000L, pi)
        }
    }

    fun fire(context: Context, intent: Intent) {
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        if (System.currentTimeMillis() - at > 2 * 3_600_000L) return // The phone was off; too late to be useful.
        val due = Prefs(context).loadTasks().filter { at(it) == at }
        if (due.isEmpty() || !LockNotification.canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH, "Task alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a task with its bell on is due."
        })
        due.forEach { t ->
            val n = Notification.Builder(context, CH)
                .setSmallIcon(R.drawable.ic_stat_stillpoint)
                .setContentTitle(t.text)
                .setContentText("Due now")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(StillpointWidget.openApp(context))
                .setAutoCancel(true)
                .build()
            nm.notify(7000 + (t.id % 100_000).toInt(), n)
        }
    }
}

/** Rings for the tasks due now, then sets the next alert. */
class TaskAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { TaskAlerts.fire(context, intent) }
        runCatching { TaskAlerts.schedule(context) }
    }
}
