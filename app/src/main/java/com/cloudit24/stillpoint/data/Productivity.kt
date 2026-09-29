package com.cloudit24.stillpoint.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.cloudit24.stillpoint.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---- Where tasks, calendar and projects come from ----

/** [authority] and permissions are for task apps that share their list through the OpenTasks provider. */
enum class TaskSource(
    val label: String,
    val detail: String,
    val authority: String? = null,
    val readPermission: String? = null,
    val writePermission: String? = null,
    val appPackage: String? = null,
) {
    PHONE("On this phone", "Stored only in Stillpoint"),
    HUB("Project Hub", "Today's and overdue tasks from your hub. Done and new tasks go back to it."),
    TASKS_ORG(
        "Tasks.org", "Open-source tasks app. Syncs with Nextcloud, CalDAV and more.",
        "org.tasks.opentasks", "org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS", "org.tasks",
    ),
    OPENTASKS(
        "OpenTasks", "Open-source tasks app. Syncs with CalDAV through DAVx5.",
        "org.dmfs.tasks", "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS", "org.dmfs.tasks",
    ),
}

enum class CalendarSource(val label: String, val detail: String) {
    PHONE("Phone calendar", "Android's calendar: Google, Outlook, work and other accounts on this phone"),
    HUB("Project Hub", "Your next event from the hub"),
    ICS("Calendar link (.ics)", "A private calendar address, for example from Outlook or Google. Read-only."),
}

enum class ProjectSource(val label: String, val detail: String) {
    PHONE("On this phone", "A simple list of projects, each with its next step"),
    HUB("Project Hub", "The one next thing to do and why, from your hub"),
}

enum class SyncFeature(val label: String) { TASKS("Tasks"), CALENDAR("Calendar"), PROJECTS("Projects") }

/** How an outside source is kept up to date. */
data class SyncConfig(val auto: Boolean = true, val everyMin: Int = 60, val wifiOnly: Boolean = false)

val SYNC_INTERVALS = listOf(15, 30, 60, 180, 360, 720, 1440)

fun intervalLabel(min: Int): String = when {
    min < 60 -> "$min minutes"
    min == 60 -> "1 hour"
    else -> "${min / 60} hours"
}

data class LocalProject(val id: Long, val name: String, val next: String)

// ---- Task apps (Tasks.org, OpenTasks) through their shared task provider ----

/** Reads and writes the task list of an installed task app. The app does its own syncing. Call off the main thread. */
class ProviderTasks(private val context: Context) {

    fun installed(src: TaskSource): Boolean = src.appPackage?.let { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    } ?: false

    fun hasPermission(src: TaskSource): Boolean = src.readPermission?.let {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    } ?: false

    private fun tasksUri(src: TaskSource) = Uri.parse("content://${src.authority}/tasks")

    /** Open tasks, soonest due first. Throws with a readable message when the app refuses. */
    fun open(src: TaskSource, limit: Int = 40): List<TaskItem> {
        val out = ArrayList<TaskItem>()
        val c = context.contentResolver.query(
            tasksUri(src), arrayOf("_id", "title", "status"), "(status IS NULL OR status < 2)", null, "due IS NULL, due ASC",
        ) ?: throw IOException("${src.label} didn't answer. Open it once, then try again.")
        c.use {
            while (it.moveToNext() && out.size < limit) out += TaskItem(it.getLong(0), it.getString(1) ?: "(no title)", false)
        }
        return out
    }

    fun setDone(src: TaskSource, id: Long, done: Boolean) {
        val v = ContentValues().apply {
            put("status", if (done) 2 else 0)
            if (done) put("completed", System.currentTimeMillis()) else putNull("completed")
        }
        context.contentResolver.update(ContentUris.withAppendedId(tasksUri(src), id), v, null, null)
    }

    /** Adds to the app's first list. */
    fun add(src: TaskSource, title: String) {
        val listId = context.contentResolver.query(
            Uri.parse("content://${src.authority}/tasklists"), arrayOf("_id"), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: throw IOException("${src.label} has no task list yet. Create one there first.")
        context.contentResolver.insert(tasksUri(src), ContentValues().apply {
            put("list_id", listId)
            put("title", title)
        })
    }
}

// ---- Calendar link (.ics) ----

/** Downloads and reads an iCalendar file. Handles time zones, all-day events and common repeat rules. */
object Ics {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )

    /** Call off the main thread. Throws IOException with a message fit to show. */
    fun fetch(raw: String): String {
        val url = raw.trim().replaceFirst(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) throw IOException("Use a web (https) link.")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Stillpoint/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("The calendar link answered $code.")
            val body = conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > 5_000_000) throw IOException("The calendar is too large (over 5 MB).")
                }
                sb.toString()
            }
            if (!body.contains("BEGIN:VCALENDAR")) throw IOException("That link isn't a calendar (.ics) file.")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** Events overlapping [from, to), earliest first. */
    fun events(body: String, from: Long, to: Long, limit: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<AgendaItem> {
        val lines = body.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        val out = ArrayList<AgendaItem>()
        var current: MutableList<Pair<String, String>>? = null
        var depth = 0
        for (line in lines) {
            val ev = current
            when {
                line == "BEGIN:VEVENT" -> current = ArrayList()
                line == "END:VEVENT" -> {
                    if (ev != null) out += occurrences(ev, from, to, zone)
                    current = null
                }
                ev == null -> Unit
                line.startsWith("BEGIN:") -> depth++ // alarms inside an event
                line.startsWith("END:") -> depth--
                depth == 0 -> {
                    val i = line.indexOf(':')
                    if (i > 0) ev.add(line.substring(0, i) to line.substring(i + 1))
                }
            }
        }
        return out.sortedBy { it.begin }.take(limit)
    }

    private fun occurrences(props: List<Pair<String, String>>, from: Long, to: Long, zone: ZoneId): List<AgendaItem> {
        fun prop(name: String) = props.firstOrNull { it.first == name || it.first.startsWith("$name;") }
        if (prop("STATUS")?.second == "CANCELLED") return emptyList()
        if (prop("RECURRENCE-ID") != null) return emptyList() // moved single instances: the series still shows
        val title = unescape(prop("SUMMARY")?.second ?: "(no title)")
        val (startName, startValue) = prop("DTSTART") ?: return emptyList()
        val allDay = startValue.trim().length == 8
        val start = parseTime(startName, startValue, zone) ?: return emptyList()
        val end = prop("DTEND")?.let { parseTime(it.first, it.second, zone) }
            ?: prop("DURATION")?.let { runCatching { start.plus(Duration.parse(it.second.trim())) }.getOrNull() }
            ?: if (allDay) start.plusDays(1) else start.plusHours(1)
        val lengthMs = Duration.between(start, end).toMillis().coerceAtLeast(0)
        val skipped = props.filter { it.first == "EXDATE" || it.first.startsWith("EXDATE;") }
            .flatMap { (n, v) -> v.split(',').mapNotNull { parseTime(n, it, zone)?.toInstant()?.toEpochMilli() } }
            .toSet()

        val out = ArrayList<AgendaItem>()
        fun consider(s: ZonedDateTime) {
            val b = s.toInstant().toEpochMilli()
            val e = b + lengthMs
            if (b !in skipped && e > from && b < to) out += AgendaItem(title, b, e, allDay)
        }

        val rule = prop("RRULE")?.second?.split(';')?.associate { it.substringBefore('=') to it.substringAfter('=') }
        val freq = rule?.get("FREQ")
        if (rule == null || freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) {
            consider(start)
            return out
        }
        val interval = rule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = rule["COUNT"]?.toIntOrNull()
        val untilRaw = rule["UNTIL"]
        val until = untilRaw?.let { parseTime("UNTIL", it, zone)?.toInstant()?.toEpochMilli() }
            ?.let { if (untilRaw.length == 8) it + 86_399_999L else it }
        val byDay = rule["BYDAY"]?.split(',')?.mapNotNull { DAYS[it.takeLast(2)] }?.sorted()

        // Without a COUNT, jump close to the window instead of walking from the first occurrence.
        val fromZ = java.time.Instant.ofEpochMilli(from).atZone(zone)
        var k = if (count != null) 0L else {
            val unit = when (freq) {
                "DAILY" -> ChronoUnit.DAYS
                "WEEKLY" -> ChronoUnit.WEEKS
                "MONTHLY" -> ChronoUnit.MONTHS
                else -> ChronoUnit.YEARS
            }
            (unit.between(start, fromZ) / interval - 1).coerceAtLeast(0)
        }
        var n = 0
        var guard = 0
        while (guard++ < 2000) {
            val step = k * interval
            val candidates = when (freq) {
                "DAILY" -> listOf(start.plusDays(step))
                "WEEKLY" -> {
                    val week = start.plusWeeks(step)
                    if (byDay.isNullOrEmpty()) listOf(week) else byDay.map { week.with(it) }.filter { !it.isBefore(start) }
                }
                "MONTHLY" -> listOf(start.plusMonths(step))
                else -> listOf(start.plusYears(step))
            }
            for (c in candidates) {
                val ms = c.toInstant().toEpochMilli()
                if ((until != null && ms > until) || (count != null && n >= count) || ms >= to) return out
                n++
                consider(c)
            }
            k++
        }
        return out
    }

    private fun parseTime(name: String, value: String, zone: ZoneId): ZonedDateTime? = runCatching {
        val v = value.trim()
        when {
            v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay(zone)
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATETIME).atZone(ZoneOffset.UTC).withZoneSameInstant(zone)
            else -> {
                val tz = Regex("TZID=\"?([^;:\"]+)").find(name)?.groupValues?.get(1)
                val z = tz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                LocalDateTime.parse(v, DATETIME).atZone(z).withZoneSameInstant(zone)
            }
        }
    }.getOrNull()

    private fun unescape(s: String) = s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\N", " ")
        .replace("\\\\", "\\")
}
