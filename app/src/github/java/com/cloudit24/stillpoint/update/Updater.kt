package com.cloudit24.stillpoint.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub build: checks the latest GitHub Release and installs its APK through PackageInstaller.
 * Network is used only when the user taps Check / Install in Settings.
 */
object Updater {
    const val AVAILABLE = true
    private const val REPO = "cloudit24/stillpoint"

    suspend fun check(current: String): UpdateCheck = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open("https://api.github.com/repos/$REPO/releases/latest")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (conn.responseCode) {
                    200 -> Unit
                    404 -> return@runCatching UpdateCheck.Failed("No releases published yet.")
                    else -> return@runCatching UpdateCheck.Failed("GitHub replied ${conn.responseCode}. Try again later.")
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val version = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?: return@runCatching UpdateCheck.Failed("The latest release has no APK attached.")
                if (isNewerVersion(version, current)) UpdateCheck.Available(version, apk.getString("browser_download_url"))
                else UpdateCheck.UpToDate(current)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { UpdateCheck.Failed("Could not reach GitHub. Check your connection.") }
    }

    /** Streams the APK straight into an install session. Returns null on hand-off, or an error message. */
    suspend fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Int) -> Unit): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = open(apkUrl) // GitHub redirects to its file host; same-protocol redirects are followed.
                    try {
                        val total = conn.contentLengthLong
                        conn.inputStream.use { input ->
                            session.openWrite("stillpoint.apk", 0, total.takeIf { it > 0 } ?: -1).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                var lastPct = -1
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            withContext(Dispatchers.Main) { onProgress(pct) }
                                        }
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(context, InstallReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
                null
            }.getOrElse { "Download failed: ${it.message ?: "unknown error"}" }
        }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Stillpoint-Updater")
        }
}
