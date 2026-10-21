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
import com.cloudit24.stillpoint.data.TermColor
import com.cloudit24.stillpoint.data.TermLook
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
 * The terminal display above the apps: the one place Stillpoint talks to you. Prayer, calls, battery, updates,
 * focus, calendar and setup tips, most pressing first; a greeting when there's nothing else. Fixed height, so the
 * apps below never move. Everything comes from the phone.
 */
@Composable
fun StatusLine(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier = Modifier, older: Int = 2, fill: Boolean = false) {
    val context = LocalContext.current
    val msgs = statusMessages(vm, s, now, context)
    // The footer: battery as the status bar sees it, and the focus or the next prayer. Read once a minute.
    val footer = remember(now / 60_000, s.focusEndsAt, s.focusProfileId) {
        val b = com.cloudit24.stillpoint.data.batteryNow(context)
        val mid = vm.activeFocus(now)?.let { "${it.name} FOCUS ${minutesWord(vm.focusUntil(now) - now)}".uppercase() }
            ?: s.city?.takeIf { s.prayerOn }?.let { c ->
                PrayerTimes.next(now, c.lat, c.lon, s.prayerMethod, s.asrHanafi)?.let { (p, at) -> "${p.label} IN ${minutesWord(at - now)}".uppercase() }
            } ?: ""
        val progress = s.city?.takeIf { s.prayerOn }?.let { c -> PrayerTimes.span(now, c.lat, c.lon, s.prayerMethod, s.asrHanafi) }
            ?.let { sp -> if (sp.nextAt > sp.currentAt) ((now - sp.currentAt).toFloat() / (sp.nextAt - sp.currentAt)) else null }
        TermFooter(b.percent, b.charging || b.full, mid, progress)
    }
    // Only Neon Alley brings the cat and the drone; Cyberpunk is colours alone.
    val neon = s.theme == AppTheme.NEON
    when (s.statusStyle) {
        StatusStyle.TERMINAL -> TerminalStyle(msgs, s.edgeMotion, modifier, older, cat = false, drone = neon && s.themeDrone,
            mood = if (neon && s.themeDrone) catMood(vm, s, now, msgs) else null,
            onAsk = if (s.assistantOn) ({ vm.assistOpen = true }) else null, footer = footer, fill = fill,
            look = s.termLook, color = s.termColor)
        StatusStyle.QUIET -> QuietStyle(msgs, modifier)
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

/** The footer: battery, one useful thing (the focus, or the next prayer), and how far the prayer span has run. */
private class TermFooter(val battery: Int, val charging: Boolean, val mid: String, val progress: Float?)

/** The three shades every look is drawn in. */
private class Phosphor(val base: Color, val bright: Color, val hi: Color) { val dim get() = base.copy(alpha = 0.5f) }

@Composable
private fun phosphor(c: TermColor): Phosphor {
    val base = if (c == TermColor.ACCENT) Accent else Color(c.argb)
    return Phosphor(base, lerp(base, Color.White, 0.12f), lerp(base, Color.White, 0.78f))
}

/** A topic word for the data-rail look, from the message's rank. */
private fun topicOf(rank: Int): String = when (rank) {
    100 -> "IQAMA"; 92 -> "SPECIAL"; 90 -> "CALL"; 85, 95 -> "BATTERY"; 80, 15 -> "PRAYER"; 78 -> "CAR"; 75 -> "FOCUS"
    72 -> "LINE"; 70, 25 -> "MESSAGES"; 65 -> "CALENDAR"; 55 -> "UPDATE"; 50 -> "LIMIT"; 30 -> "SETUP"; 5 -> "NOTE"
    else -> "WELLBEING"
}

/**
 * The terminal display: every message takes its turn, typed out on the live line while the older ones scroll up;
 * the most pressing stay a little longer. Tap a line to act on it. Four looks share this one engine.
 */
@Composable
private fun TerminalStyle(msgs: List<StatusMsg>, motion: Boolean, modifier: Modifier, older: Int = 2, cat: Boolean = false, drone: Boolean = false,
                          mood: CatMood? = null, onAsk: (() -> Unit)? = null, footer: TermFooter? = null, fill: Boolean = false,
                          look: TermLook = TermLook.CRT, color: TermColor = TermColor.AMBER) {
    val ph = phosphor(color)
    val n = msgs.size
    var step by remember { mutableIntStateOf(0) }
    val typedState = remember { mutableIntStateOf(0) }
    var typed by typedState
    val cur = msgs[step % n]
    val typing by remember(cur.text) { androidx.compose.runtime.derivedStateOf { typedState.intValue < cur.text.length } }
    LaunchedEffect(step, cur.text, n) {
        typed = if (motion) 0 else cur.text.length
        while (typed < cur.text.length) { delay(28); typed++ }
        if (n > 1) { delay(if (cur.rank >= 80) 5_000 else 3_200); step++ }
    }
    val glow = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(ph.base.copy(alpha = 0.55f), blurRadius = 7f))
    val low = footer != null && footer.battery in 0..20 && !footer.charging
    // Endless animations only when they are seen: the cursor with motion on, the blink while the battery is low.
    val cursor by if (motion) rememberInfiniteTransition(label = "term").animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "cursor")
        else remember { mutableStateOf(1f) }
    val lowBlink by if (motion && low) rememberInfiniteTransition(label = "low").animateFloat(1f, 0.2f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "low")
        else remember { mutableStateOf(1f) }

    /** The lines: older ones dim, the live one bright with the cursor. [big] is the glass look's large live line. */
    @Composable
    fun androidx.compose.foundation.layout.ColumnScope.Lines(labels: Boolean, big: Boolean, glowing: Boolean, count: Int) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            },
            label = "feed",
            modifier = if (fill) Modifier.weight(1f) else Modifier,
        ) { st ->
            Column(Modifier.padding(top = 8.dp)) {
                val m = msgs[st % n]
                val text = if (st == step) m.text.take(typed) else m.text
                val live: @Composable () -> Unit = {
                    if (labels) Text(topicOf(m.rank), color = ph.base, fontSize = 9.5.sp, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.then(m.onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier)) {
                        Text((if (labels || big) "" else "> ") + text, color = ph.hi, fontSize = if (big) 19.sp else 15.sp, lineHeight = if (big) 26.sp else 22.sp,
                            style = if (glowing) glow else androidx.compose.ui.text.TextStyle.Default,
                            fontWeight = if (big) androidx.compose.ui.text.font.FontWeight.Light else null,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.padding(start = 3.dp).size(8.dp, 17.dp).graphicsLayer { alpha = if (motion) cursor else 1f }.background(ph.bright))
                    }
                }
                val olds: @Composable () -> Unit = {
                    // Older lines, kept even when empty so the window never changes height.
                    for (k in count downTo 1) {
                        val o = if (st - k >= 0 && n > k) msgs[(st - k) % n] else null
                        if (labels) Text(o?.let { topicOf(it.rank) } ?: " ", color = ph.base.copy(alpha = 0.35f), fontSize = 9.5.sp, letterSpacing = 2.sp)
                        TermLine(if (o == null) " " else (if (labels || big) "" else "> ") + o.text, if (big) Muted else ph.dim, o?.onTap, if (big) 13.sp else 15.sp)
                    }
                }
                if (big) { live(); androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)); olds() } else { olds(); live() }
            }
        }
    }

    @Composable
    fun androidx.compose.foundation.layout.ColumnScope.Footer(left: @Composable () -> Unit) {
        if (footer == null) return
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer { alpha = if (low && motion) lowBlink else 1f }) { left() }
            Text(footer.mid, color = ph.dim, fontSize = 10.sp, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(if (onAsk != null) "ASK_" else "READY_", color = if (onAsk != null) ph.bright else ph.dim, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.then(onAsk?.let { Modifier.clickable(onClick = it) } ?: Modifier))
        }
    }
    val batteryText = { blocks: String -> (if (footer == null) "" else blocks + (if (footer.battery >= 0) " ${footer.battery}" else "") + (if (footer.charging) "\u26A1" else "")) }
    val lowColor = Color(0xFFFF6B3D)

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
    val sizeMod = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    // How many older lines fit this box, for this look, at the phone's font size. Never overflows the footer.
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val fit = if (!fill) older else {
        val (head, live, line, foot, pad) = when (look) {
            TermLook.CRT -> listOf(16.dp * fs + 8.dp, 24.dp * fs, 22.dp * fs + 6.dp, 14.dp * fs + 10.dp, 36.dp)
            TermLook.GLASS -> listOf(0.dp, 26.dp * fs + 8.dp, 18.dp * fs + 6.dp, 14.dp * fs + 22.dp, 18.dp)
        }
        ((maxHeight - head - live - foot - pad) / line).toInt().coerceIn(1, 9)
    }
    when (look) {
        TermLook.CRT -> {
            val tube = listOf(lerp(ph.base, Color.Black, 0.88f), lerp(ph.base, Color.Black, 0.95f), Color.Black)
            val shape = RoundedCornerShape(18.dp)
            val scan = remember {
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent, 0.66f to Color.Transparent, 0.67f to Color.Black.copy(alpha = 0.26f), 1f to Color.Black.copy(alpha = 0.26f),
                    startY = 0f, endY = 3f, tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                )
            }
            Column(sizeMod.clip(shape).background(androidx.compose.ui.graphics.Brush.radialGradient(tube)).border(1.dp, ph.base.copy(alpha = 0.22f), shape)
                .drawWithContent { drawContent(); drawRect(scan) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SYS://STILLPOINT", color = ph.bright, fontSize = 11.sp, letterSpacing = 2.sp, style = glow, modifier = Modifier.weight(1f))
                    if (n > 1) Text("${step % n + 1}/$n", color = ph.dim, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Lines(labels = false, big = false, glowing = true, count = fit)
                Footer {
                    val blocks = if (footer == null || footer.battery < 0) 0 else ((footer.battery + 5) / 10).coerceIn(0, 10)
                    Text("PWR " + batteryText("\u25AE".repeat(blocks) + "\u25AF".repeat(10 - blocks)), color = if (low) lowColor else ph.dim, fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
        TermLook.GLASS -> {
            Column(sizeMod.drawBehind {
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ph.base, Color.Transparent), endX = size.width * 0.7f), size = androidx.compose.ui.geometry.Size(size.width, 1f))
            }.padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
                Lines(labels = false, big = true, glowing = false, count = fit)
                footer?.progress?.let { p ->
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(2.dp).background(Color.White.copy(alpha = 0.07f))) {
                        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(2.dp).background(ph.base))
                    }
                }
                Footer { Text(batteryText("").trim().let { if (it.isEmpty()) "" else "$it%" }.lowercase(), color = if (low) lowColor else Muted, fontSize = 10.5.sp, letterSpacing = 1.sp) }
            }
        }
    }
    // The cat wakes for anything that matters; otherwise it naps on the window's edge.
    if (mood != null) CatStage(mood, motion, cat, drone, typing = typing,
        onDroneTap = { if (n > 1) step++ }, Modifier.matchParentSize())
    }
}

/** What the cat and drone react to. Worked out from the same things the terminal display shows. */
private class CatMood(
    val special: Boolean, val missedCall: Boolean, val prayer: Boolean, val lowBattery: Boolean,
    val unread: Int, val color: Color?, val asleep: Boolean, val roam: Boolean,
)

private fun catMood(vm: LauncherViewModel, s: LauncherSettings, now: Long, msgs: List<StatusMsg>): CatMood {
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    // At night and in Sleep focus the cat stays curled up and ignores everything.
    val asleep = hour >= 23 || hour < 6 || vm.activeFocus(now)?.kind == 2
    val react = !asleep
    val lit = vm.litNotifications()
    return CatMood(
        special = react && lit.any { it.special },
        missedCall = react && msgs.any { it.rank == 90 },
        prayer = react && msgs.any { it.rank == 100 || it.rank == 80 },
        lowBattery = msgs.any { it.rank == 85 || it.rank == 95 },
        unread = if (react) lit.size else 0,
        color = lit.maxByOrNull { it.postTime }?.let { vm.notifyColor(it.pkg) },
        asleep = asleep,
        roam = true,
    )
}

private enum class CatPose { SLEEP, SIT, WALK }

/** The cat and the drone, living on the top edge of the terminal window. */
@Composable
private fun CatStage(m: CatMood, motion: Boolean, cat: Boolean, drone: Boolean, typing: Boolean, onDroneTap: () -> Unit, modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val width = maxWidth
        if (drone) Drone(m, motion, typing, onDroneTap, Modifier.align(Alignment.TopStart))
    }
}

/**
 * A small companion drone on the window's corner. It bobs, its eye flickers while a message types,
 * it hops and lights up in the app's colour when something comes in, and glows pink for your special person.
 * Tap it for the next message.
 */
@Composable
private fun Drone(m: CatMood, motion: Boolean, typing: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val bob = rememberInfiniteTransition(label = "drone")
    val yState = bob.animateFloat(0f, -3f, infiniteRepeatable(tween(if (m.special) 700 else 1_400), RepeatMode.Reverse), label = "drone")
    val hop = remember { androidx.compose.animation.core.Animatable(0f) }
    var flash by remember { androidx.compose.runtime.mutableStateOf<Color?>(null) }
    var lastUnread by remember { mutableIntStateOf(m.unread) }
    LaunchedEffect(m.unread) {
        if (m.unread > lastUnread) {
            flash = m.color
            if (motion) { hop.animateTo(-8f, tween(150)); hop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.35f)) }
            delay(3_000)
            flash = null
        }
        lastUnread = m.unread
    }
    var talk by remember { mutableIntStateOf(0) }
    LaunchedEffect(typing, motion) { while (typing && motion) { delay(90); talk++ } }
    val light = when { m.special -> SpecialPink; flash != null -> flash!!; else -> NeonTeal }
    androidx.compose.foundation.Canvas(modifier.offset {
            IntOffset((-16).dp.roundToPx(), (-19 + (if (motion) yState.value else 0f) + hop.value).dp.roundToPx())
        }
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onTap)
        .padding(6.dp).size(26.dp, 20.dp)) {
        val u = size.width / 26f
        val line = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val tl = androidx.compose.ui.geometry.Offset(3 * u, 4 * u)
        val sz = androidx.compose.ui.geometry.Size(20 * u, 12 * u)
        val r = androidx.compose.ui.geometry.CornerRadius(6 * u)
        drawRoundRect(NeonBack, tl, sz, r)
        drawRoundRect(light, tl, sz, r, style = line)
        val eyeR = if (typing && talk % 2 == 1) 1.6f else 2.5f
        drawCircle(light, eyeR * u, androidx.compose.ui.geometry.Offset(13 * u, 10 * u))
        drawLine(light, androidx.compose.ui.geometry.Offset(8 * u, 4 * u), androidx.compose.ui.geometry.Offset(6 * u, 0.5f * u), 1.2f * u)
        drawLine(light, androidx.compose.ui.geometry.Offset(18 * u, 4 * u), androidx.compose.ui.geometry.Offset(20 * u, 0.5f * u), 1.2f * u)
    }
}

@Composable
private fun TermLine(text: String, color: Color, onTap: (() -> Unit)?, size: androidx.compose.ui.unit.TextUnit = 15.sp) {
    Text(text, color = color, fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).then(onTap?.let { Modifier.clickable(onClick = it) } ?: Modifier))
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

