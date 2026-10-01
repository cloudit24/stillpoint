package com.cloudit24.stillpoint.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.widget.LockNotification

/** A harmless notification from Stillpoint itself, to see the light, the signal dot and the app dot. */
object NotifyTest {
    const val CHANNEL = "light_test"

    fun send(context: Context) {
        if (!LockNotification.canPost(context)) {
            Toast.makeText(context, "Allow notifications for Stillpoint first (App info, Notifications).", Toast.LENGTH_LONG).show()
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Test", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The test from Settings, Notification."
        })
        nm.notify(4210, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setContentTitle("Stillpoint test")
            .setContentText("Go home to see the light. Swipe this away to turn it off.")
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)
            .build())
        Toast.makeText(context, "Sent. Go home to see it.", Toast.LENGTH_SHORT).show()
    }
}
