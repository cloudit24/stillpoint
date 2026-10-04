package com.cloudit24.stillpoint.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.widget.LockNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One unread notification, as the launcher needs it: which app, when, and whether it matters to you. */
data class NotifyItem(val key: String, val pkg: String, val postTime: Long, val important: Boolean, val special: Boolean = false)

/**
 * The notifications waiting right now, shared with the home screen (edge light, signal dot, dots on apps).
 * Read on the phone only. Titles are used for a moment to match your important people and are never kept.
 */
object NotifyHub {
    private val _items = MutableStateFlow<List<NotifyItem>>(emptyList())
    val items: StateFlow<List<NotifyItem>> = _items

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, NotifyListener::class.java).flattenToString()
        return enabled.split(":").any { it == me }
    }

    internal fun publish(context: Context, list: List<NotifyItem>) {
        _items.value = list
        Prefs(context).addNotifySeen(list.map { it.pkg }.toSet())
    }

    /** Your important apps, or a notification whose sender or chat title contains one of your important people. */
    internal fun isImportant(s: LauncherSettings, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in s.importantApps) return true
        if (s.importantPeople.isEmpty()) return false
        val who = who(sbn)
        return s.importantPeople.any { it.isNotBlank() && who.contains(it.trim().lowercase()) }
    }

    /** From your special person: their name in the sender or chat title. */
    internal fun isSpecial(s: LauncherSettings, sbn: StatusBarNotification): Boolean =
        s.specialPerson.isNotBlank() && who(sbn).contains(s.specialPerson.trim().lowercase())

    private fun who(sbn: StatusBarNotification): String {
        val ex = sbn.notification.extras
        return listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_TITLE),
            ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            ex.getCharSequence(Notification.EXTRA_TITLE_BIG),
        ).joinToString(" ").lowercase()
    }
}

/** Android hands this service each notification (once you allow Notification access). Nothing leaves the phone. */
class NotifyListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val reminders = HashMap<String, Int>()

    override fun onListenerConnected() = publish()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publish()
        maybeRemind(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        reminders.remove(sbn.key)
        publish()
    }

    private fun shown(sbn: StatusBarNotification): Boolean =
        sbn.isClearable && !sbn.isOngoing &&
            (sbn.packageName != packageName || sbn.notification.channelId == NotifyTest.CHANNEL) &&
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0

    private fun publish() {
        val s = Prefs(this).loadSettings()
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter(::shown)
            .map { NotifyItem(it.key, it.packageName, it.postTime, NotifyHub.isImportant(s, it), NotifyHub.isSpecial(s, it)) }
        NotifyHub.publish(this, list)
    }

    // ---- Never miss: remind again while an important notification is still unread (up to 3 times) ----

    private fun maybeRemind(sbn: StatusBarNotification) {
        val s = Prefs(this).loadSettings()
        if (s.remindEvery <= 0 || !shown(sbn) || !NotifyHub.isImportant(s, sbn) || reminders.containsKey(sbn.key)) return
        reminders[sbn.key] = 0
        handler.postDelayed({ remind(sbn.key) }, s.remindEvery * 60_000L)
    }

    private fun remind(key: String) {
        val count = reminders[key] ?: return
        val still = runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key }
        if (still == null || !LockNotification.canPost(this)) {
            reminders.remove(key)
            return
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("never_miss", "Never miss", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminds you of unread notifications from your important apps and people."
        })
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(still.packageName, 0)) }
            .getOrDefault(still.packageName)
        val title = still.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        nm.notify(key.hashCode(), Notification.Builder(this, "never_miss")
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setContentTitle("Still unread · $app")
            .setContentText(title ?: "Tap to open")
            .setContentIntent(still.notification.contentIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build())
        val s = Prefs(this).loadSettings()
        if (count + 1 < 3 && s.remindEvery > 0) {
            reminders[key] = count + 1
            handler.postDelayed({ remind(key) }, s.remindEvery * 60_000L)
        } else {
            reminders.remove(key)
        }
    }
}
