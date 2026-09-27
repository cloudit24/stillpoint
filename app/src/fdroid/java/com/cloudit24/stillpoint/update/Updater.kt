package com.cloudit24.stillpoint.update

import android.content.Context

/** F-Droid build: no network code at all. F-Droid delivers updates. */
object Updater {
    const val AVAILABLE = false

    suspend fun check(current: String): UpdateCheck = UpdateCheck.UpToDate(current)

    suspend fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Int) -> Unit): String? =
        "Updates come from F-Droid."
}
