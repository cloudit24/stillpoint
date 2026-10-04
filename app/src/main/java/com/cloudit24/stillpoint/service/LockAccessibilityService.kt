package com.cloudit24.stillpoint.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.cloudit24.stillpoint.data.focusAt

/**
 * Cannot read window content. Calls two global actions: lock screen and open notifications
 * (GLOBAL_ACTION_LOCK_SCREEN keeps fingerprint unlock working; DevicePolicyManager.lockNow does not).
 * It hears only which app comes to the front, and acts on it only when Stronger guard is on (see [Guard]).
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    private var lastPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPkg) return
        lastPkg = pkg
        runCatching { Guard.check(this, pkg) }
    }
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        fun lockScreen(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            else false

        fun openNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cn = ComponentName(context, LockAccessibilityService::class.java)
            val names = setOf(cn.flattenToString(), cn.flattenToShortString())
            return enabled.split(':').any { entry -> names.any { it.equals(entry, ignoreCase = true) } }
        }
    }
}

/**
 * Stronger guard (Settings, Wellbeing; off by default): when an app comes to the front some other way than
 * the launcher (a notification, recent apps), it gets the same treatment: held back during focus, or the pause
 * for hooked and limited apps. Always-allowed apps, the keyboard and system screens are left alone.
 */
object Guard {
    const val EXTRA_PKG = "guard_pkg"
    const val EXTRA_BLOCK = "guard_block"
    private var cache: com.cloudit24.stillpoint.data.LauncherSettings? = null
    private var cachedAt = 0L

    fun check(context: Context, pkg: String) {
        if (pkg == context.packageName || pkg == "com.android.systemui" || pkg == "android") return
        val now = System.currentTimeMillis()
        val prefs = com.cloudit24.stillpoint.data.Prefs(context)
        val s = cache?.takeIf { now - cachedAt < 10_000L } ?: prefs.loadSettings().also { cache = it; cachedAt = now }
        if (!s.guardAll) return
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(pkg) == null) return // Keyboards, dialogs and other non-apps.
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        if (pkg == home) return
        val mine = { keys: Collection<String> -> keys.any { it.startsWith("$pkg/") } }
        if (mine(s.alwaysAllowed)) return
        if (now - prefs.guardPassedAt(pkg) < 3 * 60_000L) return
        val focus = s.focusAt(now)
        val block = focus != null && !mine(focus.apps)
        val pause = !block && (mine(s.hookedApps) || mine(s.appLimits.keys))
        if (!block && !pause) return
        context.startActivity(
            Intent(context, com.cloudit24.stillpoint.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_BLOCK, block),
        )
    }
}
