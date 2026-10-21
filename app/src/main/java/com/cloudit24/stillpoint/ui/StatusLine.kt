package com.cloudit24.stillpoint.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.StatusStyle
import com.cloudit24.stillpoint.data.StatusTopic
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.drawscope.scale
import com.cloudit24.stillpoint.update.UpdateNotice
import com.cloudit24.stillpoint.widget.PrayerAlerts
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** One thing Stillpoint has to say. Higher [rank] comes first; [onTap] acts on it. */
private class StatusMsg(val text: String, val rank: Int, val onTap: (() -> Unit)? = null)

/**
 * The display under the headline: what matters now, as plain lines in your font, most pressing first.
 * Prayer, calls, battery, updates, focus, calendar and setup tips; a greeting when there is nothing else.
 * Nothing types, blinks or scrolls: a line changes only when the fact does. Tap a line to act on it.
 */
@Composable
fun StatusLine(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val msgs = statusMessages(vm, s, now, context)
    when (s.statusStyle) {
        StatusStyle.STACK -> StackStyle(msgs, modifier, onAsk = if (s.assistantOn) ({ vm.assistOpen = true }) else null)
        StatusStyle.QUIET -> QuietStyle(msgs, modifier)
    }
}

/** Up to four lines; only the first carries the accent. The greeting shows only when there is nothing else. */
@Composable
private fun StackStyle(msgs: List<StatusMsg>, modifier: Modifier, onAsk: (() -> Unit)?) {
    val accent = Accent
    val shown = msgs.filter { it.rank > 0 }.ifEmpty { msgs }.take(4)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        shown.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(m.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(if (i == 0) accent else Muted.copy(alpha = 0.35f)))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(m.text.replaceFirstChar { it.uppercase() }, color = if (i == 0) Ink else Ink.copy(alpha = 0.78f), fontSize = 16.sp, lineHeight = 22.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val topic = topicOf(m.rank)
                    if (topic.isNotEmpty()) Text(topic.lowercase(), color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (onAsk != null) Text("Ask Stillpoint", color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(start = 18.dp).clip(RoundedCornerShape(50)).clickable(onClick = onAsk).padding(vertical = 4.dp))
    }
}

private fun minutesWord(ms: Long): String {
    val m = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    return if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"
}

/** The same things the terminal display says, as plain lines, for the assistant's facts. */
internal fun statusFacts(vm: LauncherViewModel, s: LauncherSettings, now: Long, context: Context): List<String> =
    listOf("Now: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL, java.text.DateFormat.SHORT).format(java.util.Date(now))) +
        statusMessages(vm, s, now, context).map { it.text }

private fun statusMessages(vm: LauncherViewModel, s: LauncherSettings, now: Long, context: Context): List<StatusMsg> {
    val on = { t: StatusTopic -> t !in s.statusOff }
    val out = ArrayList<StatusMsg>()

    // Prayer: iqama coming, the next prayer near, or simply when it is.
    val city = s.city
    if (on(StatusTopic.PRAYER) && s.prayerOn && city != null) {
        val span = PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
        val open = { vm.screen = Screen.PRAYER }
        if (span != null) {
            val cur = span.current
            val friday = cur == com.cloudit24.stillpoint.data.Prayer.DHUHR && PrayerAlerts.jumuahOn(s, span.currentAt) != null
            val iqama = if (cur.isPrayer) PrayerAlerts.iqamaAt(s, cur, span.currentAt) else 0L
            when {
                cur.isPrayer && now < iqama ->
                    out += StatusMsg("${if (friday) "jumu'ah" else "iqama ${cur.label.lowercase()}"} in ${minutesWord(iqama - now)} · ${formatClock(context, iqama)}", 100, open)
                span.nextAt - now <= 60 * 60_000L ->
                    out += StatusMsg("${span.next.label.lowercase()} in ${minutesWord(span.nextAt - now)} · ${formatClock(context, span.nextAt)}", 80, open)
                else -> out += StatusMsg("next: ${span.next.label.lowercase()} at ${formatClock(context, span.nextAt)}", 15, open)
            }
        }
    }

    // Calls and messages: unread notifications, the phone app first.
    if (on(StatusTopic.CALLS)) {
        vm.litNotifications().groupBy { it.pkg }.forEach { (pkg, items) ->
            val app = vm.apps.firstOrNull { it.packageName == pkg }
            val label = app?.label ?: return@forEach
            val phone = "dialer" in pkg || "telecom" in pkg || pkg.endsWith(".phone") || "contacts" in pkg
            val tap = { vm.launch(app) }
            when {
                phone -> out += StatusMsg("missed call · tap to open $label".lowercase(), 90, tap)
                items.any { it.special } -> out += StatusMsg("\u2665 message from ${s.specialPerson.trim()}", 92, tap)
                items.any { it.important } -> out += StatusMsg("${items.size} new from ${label.lowercase()}", 70, tap)
                else -> out += StatusMsg("${items.size} new from ${label.lowercase()}", 25, tap)
            }
        }
    }

    // Battery running low.
    if (on(StatusTopic.BATTERY)) {
        val b = com.cloudit24.stillpoint.data.batteryNow(context)
        val level = b.percent
        if (level in 0..20 && !b.charging && !b.full) out += StatusMsg("battery $level% · charge soon", if (level <= 10) 95 else 85)
    }

    // Focus.
    vm.activeFocus(now)?.takeIf { on(StatusTopic.FOCUS) }?.let { p ->
        out += StatusMsg("${p.name.lowercase()} focus · until ${formatClock(context, vm.focusUntil(now))} · ${vm.focusAllowedNow().size} apps", 75) {
            vm.screen = Screen.FOCUS
        }
    }

    // Wellbeing: unlocks, the screen-time goal, limits used up, and on Fridays the week.
    if (on(StatusTopic.WELLBEING)) {
        if (vm.unlocksToday > 0) out += StatusMsg("${vm.unlocksToday} unlocks today", 12)
        val total = vm.totalUsage
        if (s.screenGoal > 0) {
            val goal = s.screenGoal * 60_000L
            out += if (total >= goal) StatusMsg("over your ${formatDuration(goal)} screen goal · ${formatDuration(total)}", 60)
            else StatusMsg("screen time ${formatDuration(total)} of ${formatDuration(goal)}", 18)
        }
        s.appLimits.forEach { (key, minutes) ->
            val app = vm.apps.firstOrNull { it.key == key } ?: return@forEach
            if ((vm.usage[app.packageName] ?: 0L) >= minutes * 60_000L) out += StatusMsg("${app.label.lowercase()}: daily limit reached", 50)
        }
        if (java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY && vm.weekUsage.isNotEmpty()) {
            val top = vm.weekUsage.maxByOrNull { it.value }?.key?.let { k -> vm.apps.firstOrNull { it.packageName == k }?.label }
            out += StatusMsg("this week: ${formatDuration(vm.weekUsage.values.sum() / 7)} a day" + (top?.let { " · most: ${it.lowercase()}" } ?: ""), 20)
        }
    }

    // Calendar: the next event within two hours.
    if (on(StatusTopic.CALENDAR)) {
        vm.agenda.firstOrNull { !it.allDay && it.begin > now && it.begin - now <= 2 * 3_600_000L }?.let { e ->
            out += StatusMsg("${e.title.lowercase()} at ${formatClock(context, e.begin)}", 65)
        }
    }

    // A new version.
    if (s.carOn && vm.carConnected && vm.screen != com.cloudit24.stillpoint.Screen.CAR)
        out += StatusMsg("car connected · tap for car mode", 78) { vm.screen = com.cloudit24.stillpoint.Screen.CAR }
    // A line a rule or Tasker asked for ("Laundry done"). Tap to clear it.
    com.cloudit24.stillpoint.data.Prefs(context).autoLine()?.let { line ->
        out += StatusMsg(line.lowercase(), 72) { com.cloudit24.stillpoint.data.Prefs(context).clearAutoLine() }
    }
    if (on(StatusTopic.UPDATE)) {
        (vm.updatePopup ?: UpdateNotice.cached()?.version)?.let { v ->
            out += StatusMsg("update $v ready · tap to install", 55) { vm.screen = Screen.SETTINGS }
        }
    }

    // Setup tips, until they're done.
    if (on(StatusTopic.SETUP)) {
        if (s.showUsage && !vm.hasUsageAccess) {
            out += StatusMsg("allow usage access for screen time · tap", 30) {
                context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        if (vm.homeApps().isEmpty()) out += StatusMsg("swipe left for apps, right for the shelf", 30)
    }

    // The latest note, quietly.
    if (on(StatusTopic.NOTES)) {
        vm.notes.lastOrNull()?.let { n ->
            out += StatusMsg("note: ${n.text.lineSequence().first().lowercase()}", 5) { vm.screen = Screen.WIDGETS }
        }
    }

    // Always there: a greeting. The weather stays in the headline, so it isn't repeated here.
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val hello = when (hour) {
        in 5..11 -> "good morning"
        in 12..16 -> "good afternoon"
        in 17..20 -> "good evening"
        else -> "good night"
    }
    out += StatusMsg(hello, 0)

    return out.sortedByDescending { it.rank }
}

/** A word for what a line is about, from its rank. */
private fun topicOf(rank: Int): String = when (rank) {
    100 -> "IQAMA"; 92 -> "SPECIAL"; 90 -> "CALL"; 85, 95 -> "BATTERY"; 80, 15 -> "PRAYER"; 78 -> "CAR"; 75 -> "FOCUS"
    72 -> "LINE"; 70, 25 -> "MESSAGES"; 65 -> "CALENDAR"; 55 -> "UPDATE"; 50 -> "LIMIT"; 30 -> "SETUP"; 5 -> "NOTE"; 0 -> ""
    else -> "WELLBEING"
}

@Composable
private fun QuietStyle(msgs: List<StatusMsg>, modifier: Modifier) {
    val accent = Accent
    var index by remember { mutableIntStateOf(0) }
    val i = index % msgs.size
    val m = msgs[i]
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(m.text.replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp).then(m.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier))
        if (msgs.size > 1) Text("${i + 1}/${msgs.size}", color = Muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { index++ }.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

