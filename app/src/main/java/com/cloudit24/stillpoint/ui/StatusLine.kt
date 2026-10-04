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
import com.cloudit24.stillpoint.data.AppTheme
import androidx.compose.foundation.layout.offset
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
        StatusStyle.TERMINAL -> TerminalStyle(msgs, s.edgeMotion, modifier,
            cat = s.theme == AppTheme.NEON && s.themeCat, drone = s.theme == AppTheme.NEON && s.themeDrone)
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

/**
 * A small terminal window: every message takes its turn, typed out on the bottom line while the older ones
 * scroll up. The most pressing ones stay a little longer. Tap a line to act on it.
 */
@Composable
private fun TerminalStyle(msgs: List<StatusMsg>, motion: Boolean, modifier: Modifier, cat: Boolean = false, drone: Boolean = false) {
    val accent = Accent
    val neon = LocalNeon.current
    val n = msgs.size
    var step by remember { mutableIntStateOf(0) }
    var typed by remember { mutableIntStateOf(0) }
    val cur = msgs[step % n]
    LaunchedEffect(step, cur.text, n) {
        typed = if (motion) 0 else cur.text.length
        while (typed < cur.text.length) { delay(28); typed++ }
        if (n > 1) { delay(if (cur.rank >= 80) 5_000 else 3_200); step++ }
    }
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursor by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "cursor")
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().clip(shape).background(accent.copy(alpha = 0.05f))
        .border(1.dp, accent.copy(alpha = 0.35f), shape).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dots = if (neon) listOf(NeonOrange, NeonTeal, Muted) else List(3) { accent.copy(alpha = 0.45f) }
            dots.forEach { c -> Box(Modifier.padding(end = 5.dp).size(6.dp).clip(CircleShape).background(c)) }
            Text("stillpoint", color = accent.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp).weight(1f))
            if (n > 1) Text("${step % n + 1}/$n", color = accent.copy(alpha = 0.55f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "feed",
        ) { st ->
            Column(Modifier.padding(top = 8.dp)) {
                // Two older lines above, kept even when empty so the window never changes height.
                for (k in 2 downTo 1) {
                    val m = if (st - k >= 0 && n > k) msgs[(st - k) % n] else null
                    TermLine(if (m == null) " " else "> ${m.text}", accent.copy(alpha = 0.45f), m?.onTap)
                }
                val m = msgs[st % n]
                val text = if (st == step) m.text.take(typed) else m.text
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.then(m.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier)) {
                    Text("> $text", color = accent, fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.padding(start = 3.dp).size(8.dp, 17.dp).alpha(if (motion) cursor else 1f).background(accent))
                }
            }
        }
    }
    // The cat wakes for anything that matters; otherwise it naps on the window's edge.
    if (cat) AlleyCat(awake = msgs.any { it.rank >= 50 }, Modifier.align(Alignment.TopEnd))
    if (drone) Drone(motion, Modifier.align(Alignment.TopStart).offset(x = (-10).dp, y = (-13).dp))
    }
}

/** A line-drawn cat sitting on the terminal window. Tap it for a purr. */
@Composable
private fun AlleyCat(awake: Boolean, modifier: Modifier) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val w = if (awake) 36.dp else 50.dp
    val h = if (awake) 40.dp else 29.dp
    androidx.compose.foundation.Canvas(modifier.offset(x = (-16).dp, y = -(h - 1.5.dp)).size(w, h)
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }) {
        val u = size.width / (if (awake) 40f else 58f)
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * u, y * u)
        val line = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val body = androidx.compose.ui.graphics.Path()
        val extra = androidx.compose.ui.graphics.Path()
        if (awake) {
            body.moveTo(10 * u, 42 * u); body.cubicTo(8 * u, 30 * u, 10 * u, 22 * u, 14 * u, 18 * u)
            body.lineTo(12 * u, 8 * u); body.lineTo(18 * u, 14 * u); body.lineTo(24 * u, 14 * u); body.lineTo(30 * u, 8 * u)
            body.lineTo(28 * u, 18 * u); body.cubicTo(32 * u, 22 * u, 34 * u, 30 * u, 32 * u, 42 * u); body.close()
            extra.moveTo(32 * u, 40 * u); extra.cubicTo(38 * u, 38 * u, 38 * u, 30 * u, 34 * u, 28 * u)
        } else {
            body.moveTo(6 * u, 30 * u); body.cubicTo(6 * u, 16 * u, 20 * u, 12 * u, 32 * u, 14 * u)
            body.cubicTo(44 * u, 12 * u, 54 * u, 18 * u, 52 * u, 30 * u); body.close()
            extra.moveTo(40 * u, 16 * u); extra.lineTo(43 * u, 9 * u); extra.lineTo(46 * u, 15 * u)
            extra.lineTo(50 * u, 10 * u); extra.lineTo(51 * u, 18 * u)
            extra.moveTo(6 * u, 30 * u); extra.cubicTo(2 * u, 28 * u, 2 * u, 22 * u, 8 * u, 22 * u)
            extra.moveTo(42 * u, 22 * u); extra.cubicTo(42.7f * u, 23 * u, 45.3f * u, 23 * u, 46 * u, 22 * u)
            extra.moveTo(47 * u, 22 * u); extra.cubicTo(47.7f * u, 23 * u, 50.3f * u, 23 * u, 51 * u, 22 * u)
        }
        drawPath(body, NeonBack)
        drawPath(body, NeonOrange, style = line)
        drawPath(extra, NeonOrange, style = line)
        if (awake) { drawCircle(NeonTeal, 1.9f * u, o(17f, 21f)); drawCircle(NeonTeal, 1.9f * u, o(25f, 21f)) }
    }
}

/** A small companion drone perched on the window's corner, bobbing gently. */
@Composable
private fun Drone(motion: Boolean, modifier: Modifier) {
    val bob = rememberInfiniteTransition(label = "drone")
    val y by bob.animateFloat(0f, -3f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "drone")
    androidx.compose.foundation.Canvas(modifier.offset(y = if (motion) y.dp else 0.dp).size(26.dp, 20.dp)) {
        val u = size.width / 26f
        val line = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val tl = androidx.compose.ui.geometry.Offset(3 * u, 4 * u)
        val sz = androidx.compose.ui.geometry.Size(20 * u, 12 * u)
        val r = androidx.compose.ui.geometry.CornerRadius(6 * u)
        drawRoundRect(NeonBack, tl, sz, r)
        drawRoundRect(NeonTeal, tl, sz, r, style = line)
        drawCircle(NeonTeal, 2.5f * u, androidx.compose.ui.geometry.Offset(13 * u, 10 * u))
        drawLine(NeonTeal, androidx.compose.ui.geometry.Offset(8 * u, 4 * u), androidx.compose.ui.geometry.Offset(6 * u, 0.5f * u), 1.2f * u)
        drawLine(NeonTeal, androidx.compose.ui.geometry.Offset(18 * u, 4 * u), androidx.compose.ui.geometry.Offset(20 * u, 0.5f * u), 1.2f * u)
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

