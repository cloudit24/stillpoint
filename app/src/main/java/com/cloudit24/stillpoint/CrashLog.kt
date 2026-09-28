package com.cloudit24.stillpoint

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Date

/**
 * Keeps the last crash on the phone so it can be shared by hand. Nothing is sent anywhere.
 * After saving, Android's own handler runs as usual and restarts the home screen.
 */
object CrashLog {
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    "Stillpoint ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "${Date()} · thread ${thread.name}\n\n" + error.stackTraceToString(),
                )
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(UNSEEN, true).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    /** Time and text of the last crash, or null. */
    fun last(context: Context): Pair<Long, String>? =
        file(context).takeIf { it.exists() }?.let { f -> runCatching { f.lastModified() to f.readText() }.getOrNull() }

    /** True once after a crash, so the app can mention it on the next start. */
    fun takeUnseen(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val unseen = sp.getBoolean(UNSEEN, false)
        if (unseen) sp.edit().putBoolean(UNSEEN, false).apply()
        return unseen
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private const val PREFS = "crash"
    private const val UNSEEN = "unseen"
}
