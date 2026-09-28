package com.cloudit24.stillpoint.data

import com.cloudit24.stillpoint.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Project Hub: your own self-hosted server (to-dos, calendar, things). Optional and off by default.
 * Talks only to the address you enter, with the app key you enter. Nothing else goes anywhere.
 * Call from a background thread.
 */
class HubRepository {

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Failed(val reason: String) : Result<Nothing>
    }

    /** The one next thing, today's counts, next event and today's tasks, in one small call. */
    fun glance(url: String, key: String, skip: Set<String>): Result<String> {
        val q = if (skip.isEmpty()) "" else "?skip=" + URLEncoder.encode(skip.joinToString(","), "UTF-8")
        return call("GET", url, "/api/glance/$q", key, null)
    }

    fun addTask(url: String, key: String, text: String): Result<String> =
        call("POST", url, "/api/tasks/", key, JSONObject().put("text", text))

    fun setDone(url: String, key: String, id: String, done: Boolean): Result<String> =
        call("POST", url, "/api/tasks/${URLEncoder.encode(id, "UTF-8")}/done/", key, JSONObject().put("done", done))

    private fun call(method: String, base: String, path: String, key: String, body: JSONObject?): Result<String> {
        val conn = try {
            URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Failed("That address doesn't look right.")
        }
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("Authorization", "Token $key")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Stillpoint/${BuildConfig.VERSION_NAME}")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            when (val code = conn.responseCode) {
                in 200..299 -> Result.Ok(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> Result.Failed("The hub didn't accept the key. Connect again.")
                404 -> Result.Failed("No Project Hub at that address.")
                else -> Result.Failed("The hub answered with an error ($code).")
            }
        } catch (e: IOException) {
            Result.Failed("Can't reach the hub. Offline, or the address is wrong.")
        } finally {
            conn.disconnect()
        }
    }
}

data class HubNow(val kind: String, val id: String, val label: String, val title: String, val why: String, val project: String)

data class HubTask(val id: String, val text: String, val due: String?, val done: Boolean)

/** What home shows from the hub. [fetchedAt] is when the phone got it (shown when offline). */
data class HubGlance(
    val now: HubNow,
    val due: Int,
    val overdue: Int,
    val doneToday: Int,
    val nextEventTitle: String?,
    val nextEventStart: String?,
    val tasks: List<HubTask>,
    val lost: Int,
    val fetchedAt: Long,
) {
    companion object {
        fun parse(body: String, fetchedAt: Long): HubGlance? = runCatching {
            val o = JSONObject(body)
            val n = o.getJSONObject("now")
            val t = o.getJSONObject("today")
            val ev = o.optJSONObject("next_event")
            val arr = o.getJSONArray("tasks")
            HubGlance(
                now = HubNow(n.getString("kind"), n.optString("id"), n.optString("label"), n.optString("title"),
                    n.optString("why"), n.optString("project")),
                due = t.optInt("due"),
                overdue = t.optInt("overdue"),
                doneToday = t.optInt("done"),
                nextEventTitle = ev?.optString("title"),
                nextEventStart = ev?.optString("start"),
                tasks = List(arr.length()) { i ->
                    val x = arr.getJSONObject(i)
                    HubTask(x.getString("id"), x.getString("text"), x.optString("due").takeIf { it.isNotBlank() && it != "null" },
                        x.optBoolean("done"))
                },
                lost = o.optInt("lost"),
                fetchedAt = fetchedAt,
            )
        }.getOrNull()
    }
}
