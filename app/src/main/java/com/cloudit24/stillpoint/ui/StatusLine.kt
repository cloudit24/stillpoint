package com.cloudit24.stillpoint.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
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
import com.cloudit24.stillpoint.update.UpdateNotice
import com.cloudit24.stillpoint.widget.PrayerAlerts
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** One thing Stillpoint has to say. Higher [rank] comes first; [onTap] acts on it. */
private class StatusMsg(val text: String, val rank: Int, val onTap: (() -> Unit)? = null)

private val Lcd = Color(0xFFEF9F27)
private val LcdDim = Color(0xFF8A6420)
private val LcdBack = Color(0xFF1C1A10)

/**
 * The terminal display above the apps: the one place Stillpoint talks to you. Prayer, calls, battery, updates,
 * focus, calendar and setup tips, most pressing first; a greeting when there's nothing else. Fixed height, so the
 * apps below never move. Everything comes from the phone.
 */
@Composable
fun StatusLine(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val msgs = statusMessages(vm, s, now, context)
    when (s.statusStyle) {
        StatusStyle.TERMINAL -> TerminalStyle(msgs, s.edgeMotion, modifier)
        StatusStyle.LCD -> LcdStyle(msgs, modifier)
        StatusStyle.QUIET -> QuietStyle(msgs, modifier)
    }
}

private fun minutesWord(ms: Long): String {
    val m = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    return if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"
}

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
                items.any { it.important } -> out += StatusMsg("${items.size} new from ${label.lowercase()}", 70, tap)
                else -> out += StatusMsg("${items.size} new from ${label.lowercase()}", 25, tap)
            }
        }
    }

    // Battery running low.
    if (on(StatusTopic.BATTERY)) {
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.let { i ->
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l < 0) -1 else (l * 100f / sc).roundToInt()
        } ?: -1
        val charging = (b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }
        if (level in 0..20 && !charging) out += StatusMsg("battery $level% · charge soon", if (level <= 10) 95 else 85)
    }

    // Focus.
    if (on(StatusTopic.FOCUS) && s.focusEndsAt > now) {
        out += StatusMsg("focus until ${formatClock(context, s.focusEndsAt)} · only allowed apps", 75) { vm.screen = Screen.FOCUS }
    }

    // Calendar: the next event within two hours.
    if (on(StatusTopic.CALENDAR)) {
        vm.agenda.firstOrNull { !it.allDay && it.begin > now && it.begin - now <= 2 * 3_600_000L }?.let { e ->
            out += StatusMsg("${e.title.lowercase()} at ${formatClock(context, e.begin)}", 65)
        }
    }

    // A new version.
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

    // Always there: a greeting, with the weather when it's on.
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val hello = when (hour) {
        in 5..11 -> "good morning"
        in 12..16 -> "good afternoon"
        in 17..20 -> "good evening"
        else -> "good night"
    }
    val w = vm.weather
    val weather = if (on(StatusTopic.WEATHER) && s.weatherOn && w != null) {
        val t = if (s.fahrenheit) w.tempC * 9 / 5 + 32 else w.tempC
        " · ${t.roundToInt()}° ${weatherKind(w.code).label.lowercase()}"
    } else ""
    out += StatusMsg(hello + weather, 0)

    return out.sortedByDescending { it.rank }
}

/** Green-screen lines: the most pressing at the bottom with a blinking cursor, two quieter ones above it. */
@Composable
private fun TerminalStyle(msgs: List<StatusMsg>, motion: Boolean, modifier: Modifier) {
    val accent = Accent
    val top = msgs.first()
    val rest = msgs.drop(1).take(2).reversed()
    // The newest line types itself out when it changes.
    var typed by remember(top.text) { mutableIntStateOf(if (motion) 0 else top.text.length) }
    LaunchedEffect(top.text) {
        while (typed < top.text.length) { delay(28); typed++ }
    }
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursor by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "cursor")
    Column(modifier.fillMaxWidth().heightIn(min = 84.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom) {
        rest.forEach { m ->
            TermLine("> ${m.text}", accent.copy(alpha = 0.45f), m.onTap)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.then(top.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier)) {
            Text("> " + top.text.take(typed), color = accent, fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.padding(start = 3.dp).size(8.dp, 17.dp).alpha(if (motion) cursor else 1f).background(accent))
        }
    }
}

@Composable
private fun TermLine(text: String, color: Color, onTap: (() -> Unit)?) {
    Text(text, color = color, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).then(onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier))
}

/** An amber segment-style panel: the main message big, the next two under it. Tap a line to act; tap 1/4 for the next. */
@Composable
private fun LcdStyle(msgs: List<StatusMsg>, modifier: Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val i = index % msgs.size
    val m = msgs[i]
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth().heightIn(min = 104.dp).clip(shape).background(LcdBack).border(1.dp, Color(0xFF3A3420), shape)
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.text.uppercase(), color = Lcd, fontSize = 18.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(m.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier))
            if (msgs.size > 1) Text("${i + 1}/${msgs.size}", color = LcdDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { index++ }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
        }
        (1..2).mapNotNull { k -> msgs.getOrNull((i + k) % msgs.size)?.takeIf { msgs.size > k } }.forEach { n ->
            Text(n.text.uppercase(), color = LcdDim, fontSize = 13.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().then(n.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier))
        }
    }
}

/** One calm sentence with a dot, Apple-like. Tap the count on the right for the next. */
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

