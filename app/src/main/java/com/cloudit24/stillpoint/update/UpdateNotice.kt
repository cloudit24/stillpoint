package com.cloudit24.stillpoint.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cloudit24.stillpoint.BuildConfig
import com.cloudit24.stillpoint.MainActivity
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.widget.LockNotification

/**
 * Quiet update checks: at most every 6 hours, when the launcher comes to the front or Settings opens.
 * A new version shows at the top of Settings and as one silent notification (once per version).
 */
object UpdateNotice {
    private const val EVERY = 6 * 3_600_000L
    const val EXTRA_OPEN_SETTINGS = "open_settings"

    @Volatile
    private var last: Pair<Long, UpdateCheck.Available?>? = null

    fun cached(): UpdateCheck.Available? = last?.takeIf { System.currentTimeMillis() - it.first < EVERY }?.second

    /** The newer version, if there is one. Asks GitHub only when the last answer is older than 6 hours. */
    suspend fun check(): UpdateCheck.Available? {
        if (!Updater.AVAILABLE) return null
        last?.let { if (System.currentTimeMillis() - it.first < EVERY) return it.second }
        val r = Updater.check(BuildConfig.VERSION_NAME)
        if (r is UpdateCheck.Failed) return null
        val a = r as? UpdateCheck.Available
        last = System.currentTimeMillis() to a
        return a
    }

    fun notifyOnce(context: Context, a: UpdateCheck.Available) {
        val sp = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        if (sp.getString("notified", null) == a.version || !LockNotification.canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("updates", "Updates", NotificationManager.IMPORTANCE_LOW).apply {
            description = "When a new version of Stillpoint Launcher is ready."
        })
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_SETTINGS, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(4203, Notification.Builder(context, "updates")
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setContentTitle("Stillpoint Launcher ${a.version} is ready")
            .setContentText("You have ${BuildConfig.VERSION_NAME}. Open Settings to install.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
        sp.edit().putString("notified", a.version).apply()
    }
}
