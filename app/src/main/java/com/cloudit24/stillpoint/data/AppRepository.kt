package com.cloudit24.stillpoint.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Launchable activities across the main profile and any work profile. */
    fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            runCatching {
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user).map { info ->
                    val base = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName }
                    AppEntry(
                        packageName = info.componentName.packageName,
                        className = info.componentName.className,
                        label = if (user == me) base else "$base (work)",
                        user = user,
                        userSerial = serial,
                        category = info.applicationInfo.category,
                        installedAt = info.firstInstallTime,
                    )
                }
            }.getOrDefault(emptyList())
        }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Badged icon (work profile badge included), rendered at [sizePx]. */
    fun loadIcon(app: AppEntry, sizePx: Int): Bitmap? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
        launcherApps.resolveActivity(intent, app.user)?.getBadgedIcon(0)?.toBitmap(sizePx, sizePx)
    }.getOrNull()

    fun launch(app: AppEntry): Boolean = runCatching {
        launcherApps.startMainActivity(ComponentName(app.packageName, app.className), app.user, null, null)
    }.isSuccess

    fun openAppInfo(app: AppEntry) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), app.user, null, null)
        }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
    }
}
