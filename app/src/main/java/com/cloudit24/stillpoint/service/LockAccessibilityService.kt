package com.cloudit24.stillpoint.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Receives no events and cannot read window content.
 * Exists only to call two global actions: lock screen and open notifications.
 * GLOBAL_ACTION_LOCK_SCREEN keeps fingerprint unlock working (DevicePolicyManager.lockNow does not).
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
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
