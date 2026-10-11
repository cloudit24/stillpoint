package com.cloudit24.stillpoint.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

/** The battery as the status bar sees it. [percent] is -1 when the phone gives nothing. */
class BatteryNow(val percent: Int, val charging: Boolean, val full: Boolean, val tempC: Float)

/**
 * Reads the system's own battery broadcast (the one the status bar uses). The BatteryManager
 * shortcuts (BATTERY_PROPERTY_CAPACITY, isCharging) lag or lie on some phones: a stale percentage,
 * or "charging" while plugged in and full. Only if the broadcast is missing do we fall back to them.
 */
fun batteryNow(context: Context): BatteryNow {
    val b = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    if (b != null) {
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val percent = if (level < 0) -1 else (level * 100f / scale).roundToInt().coerceIn(0, 100)
        val full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged && percent >= 100)
        return BatteryNow(percent, charging = status == BatteryManager.BATTERY_STATUS_CHARGING && !full, full = full,
            tempC = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f)
    }
    val bm = context.getSystemService(BatteryManager::class.java) ?: return BatteryNow(-1, false, false, 0f)
    val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: -1
    return BatteryNow(percent, charging = bm.isCharging && percent < 100, full = percent >= 100, tempC = 0f)
}
