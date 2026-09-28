package com.cloudit24.stillpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import com.cloudit24.stillpoint.data.Prayer
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.net.TrafficStats
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.LocalIp
import com.cloudit24.stillpoint.data.NetInfo
import com.cloudit24.stillpoint.data.PrayerTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HeroPrayer = Color(0xFF8FC4A8)
private val HeroWeather = Color(0xFFA9C8E8)

private data class HeroCard(val title: String, val subtitle: String, val color: Color, val small: Boolean = false)

/**
 * Clock-free header: accent date line, a big headline that flips like a live tile
 * (greeting, next prayer, gold, Hijri, Tamil), page dots, and a glass strip with network, memory and IP.
 */
@Composable
fun HeroHeader(vm: LauncherViewModel, s: LauncherSettings, now: Long) {
    val context = LocalContext.current
    val zdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val dayLine = remember(today) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()).format(today).uppercase(Locale.getDefault())
    }

    val cards = buildList {
        val greeting = when (zdt.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        add(HeroCard(greeting, "It's ${formatClock(context, now)}", Ink))
        val w = vm.weather
        if (s.weatherOn && w != null) {
            val t = if (s.fahrenheit) w.tempC * 9 / 5 + 32 else w.tempC
            add(HeroCard("${t.roundToInt()}° ${weatherKind(w.code).label}", s.city?.name ?: "", HeroWeather))
        }
        if (s.hijriOn) {
            val h = Calendars.hijri(today, s.hijriAdjust)
            val m = Regex("^(.*) (\\d+ AH)$").find(h)
            add(HeroCard(m?.groupValues?.get(1) ?: h, m?.groupValues?.get(2) ?: "", Ink, small = true))
        }
        if (s.tamilOn) {
            val parts = Calendars.tamil(today).split(" · ", limit = 2)
            add(HeroCard(parts[0], parts.getOrElse(1) { "" }, Ink, small = true))
        }
    }

    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards.size) {
        while (cards.size > 1) {
            delay(6_000)
            index++
        }
    }
    val current = index % cards.size

    val prayerShown = s.prayerOn && s.city != null
    Column(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text(dayLine, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp)
        FlipCard(
            key = current,
            card = cards[current],
            modifier = Modifier.padding(top = 6.dp).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { index++ },
        )
        if (cards.size > 1) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.indices.forEach { i ->
                    val w by animateDpAsState(if (i == current) 18.dp else 5.dp, tween(300), label = "dot")
                    Box(Modifier.height(5.dp).width(w).clip(RoundedCornerShape(3.dp))
                        .background(if (i == current) Accent else Muted.copy(alpha = 0.35f)))
                }
            }
        }
       }
       if (prayerShown) DayDial(vm, s, now, Modifier.padding(start = 12.dp).size(96.dp))
      }
        if (prayerShown) PrayerNowPanel(vm, s, now, Modifier.padding(top = 24.dp))
        if (s.showStats || s.showLocalIp || s.publicIpOn) {
            StatsStrip(vm, s, Modifier.padding(top = if (prayerShown) 10.dp else 24.dp))
        }
    }
}

/** Flips around its horizontal axis when [key] changes; otherwise shows [card] live. */
@Composable
private fun FlipCard(key: Int, card: HeroCard, modifier: Modifier) {
    var shownKey by remember { mutableIntStateOf(key) }
    var shown by remember { mutableStateOf(card) }
    val latest by rememberUpdatedState(card)
    val rot = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == shownKey) return@LaunchedEffect
        rot.animateTo(90f, tween(220, easing = FastOutLinearInEasing))
        shown = latest
        shownKey = key
        rot.snapTo(-90f)
        rot.animateTo(0f, tween(300, easing = LinearOutSlowInEasing))
    }
    val display = if (shownKey == key) card else shown
    Column(modifier.graphicsLayer {
        rotationX = rot.value
        cameraDistance = 14f * density
    }) {
        Text(display.title, color = display.color, fontSize = if (display.small) 30.sp else 40.sp, lineHeight = 46.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(display.subtitle, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The day as a ring: midnight at the top, a dot for each prayer (the next one larger), the time since the current
 * prayer as an arc up to "now", and the countdown to the next prayer in the middle. Redrawn once a minute.
 */
@Composable
private fun DayDial(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier) {
    val city = s.city ?: return
    val today = LocalDate.now()
    val midnight = remember(today) { today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val times = remember(today, city, s.prayerMethod, s.asrHanafi) {
        PrayerTimes.forDate(today, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    }
    val span = remember(now, city, s.prayerMethod, s.asrHanafi) {
        PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    }
    val accent = Accent
    fun angle(ms: Long) = (ms - midnight) / 86_400_000f * 360f - 90f
    Box(modifier.clickable { vm.screen = Screen.PRAYER }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val r = size.minDimension / 2 - 6.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.08f), r, style = Stroke(stroke))
            fun at(ms: Long): Offset {
                val a = Math.toRadians(angle(ms).toDouble())
                return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            }
            span?.let { sp ->
                val sweep = ((angle(now) - angle(sp.currentAt)) % 360f + 360f) % 360f
                drawArc(accent, angle(sp.currentAt), sweep, useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            times.forEach { (p, t) ->
                val next = span?.next == p
                drawCircle(
                    when {
                        next -> accent
                        p.isPrayer -> Color.White.copy(alpha = 0.6f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                    if (next) 4.5.dp.toPx() else 2.5.dp.toPx(), at(t),
                )
            }
            drawCircle(accent.copy(alpha = 0.3f), 8.dp.toPx(), at(now))
            drawCircle(Color.White, 3.5.dp.toPx(), at(now))
        }
        span?.let { sp ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(sp.nextAt - now), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Text("to ${sp.next.label}", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

/** Glass panel: the prayer time we're in (with iqama countdown) and the next one, with progress between them. */
@Composable
private fun PrayerNowPanel(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val city = s.city ?: return
    val span = remember(now, city, s.prayerMethod, s.asrHanafi) {
        PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    } ?: return
    val iqamaAt = if (span.current.isPrayer) span.currentAt + s.iqamaMin(span.current) * 60_000L else 0L
    val nowLine = when {
        now < iqamaAt -> "Iqama in ${formatDuration(iqamaAt - now)}"
        span.current == Prayer.FAJR -> "until sunrise ${formatClock(context, span.endsAt)}"
        else -> "since ${formatClock(context, span.currentAt)}"
    }
    val progress = ((now - span.currentAt).toFloat() / (span.nextAt - span.currentAt).coerceAtLeast(1)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable { vm.screen = Screen.PRAYER }
            .padding(vertical = 14.dp),
    ) {
        Row {
            PrayerCell("NOW", span.current, nowLine, HeroPrayer, Modifier.weight(1f))
            PrayerCell("NEXT", span.next, "${formatClock(context, span.nextAt)} · in ${formatDuration(span.nextAt - now)}", Ink,
                Modifier.weight(1f))
        }
        Box(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(HeroPrayer))
        }
    }
}

@Composable
private fun PrayerCell(label: String, p: Prayer, line: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp)) {
        Text(label, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(p.label, color = color, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(p.arabic, color = Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
        }
        Text(line, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Glass strip: network speed, memory, IP. Sampled once a second only while home is on screen. */
@Composable
private fun StatsStrip(vm: LauncherViewModel, s: LauncherSettings, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var down by remember { mutableStateOf("…") }
    var up by remember { mutableStateOf("…") }
    var ramPct by remember { mutableIntStateOf(0) }
    var ramTotal by remember { mutableIntStateOf(0) }
    var local by remember { mutableStateOf<LocalIp?>(null) }

    LaunchedEffect(lifecycleOwner, s.publicIpOn) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo()
            var lastRx = TrafficStats.getTotalRxBytes()
            var lastTx = TrafficStats.getTotalTxBytes()
            var lastT = SystemClock.elapsedRealtime()
            var tick = 0
            while (true) {
                if (tick % 3 == 0) {
                    val l = withContext(Dispatchers.IO) { NetInfo.localIp(context) }
                    local = l
                    vm.refreshPublicIp(l?.address)
                }
                am.getMemoryInfo(mem)
                ramPct = ((1 - mem.availMem.toDouble() / mem.totalMem) * 100).roundToInt()
                ramTotal = (mem.totalMem / 1_073_741_824.0).roundToInt()
                delay(1_000)
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val t = SystemClock.elapsedRealtime()
                val secs = (t - lastT).coerceAtLeast(1) / 1000.0
                if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
                    down = speed((rx - lastRx) / secs)
                    up = speed((tx - lastTx) / secs)
                }
                lastRx = rx; lastTx = tx; lastT = t
                tick++
            }
        }
    }

    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.09f), shape).padding(vertical = 12.dp),
    ) {
        if (s.showStats) {
            StatCell("NETWORK", "↓ $down", "↑ $up", Modifier.weight(1f).clickable { vm.screen = Screen.DATA })
            StatCell("MEMORY", "$ramPct%", "of $ramTotal GB", Modifier.weight(0.8f).clickable { vm.screen = Screen.DATA })
        }
        if (s.showLocalIp || s.publicIpOn) {
            val l = local
            val first = if (s.showLocalIp) l?.address ?: "Offline" else vm.publicIp ?: "…"
            val second = when {
                s.showLocalIp && s.publicIpOn -> vm.publicIp ?: "…"
                s.showLocalIp -> l?.kind ?: ""
                else -> "public"
            }
            StatCell(if (s.showLocalIp) (l?.kind?.uppercase() ?: "IP") else "PUBLIC IP", first, second,
                Modifier.weight(1.3f).clickable {
                    val copy = listOfNotNull(l?.address, vm.publicIp).joinToString("\n")
                    if (copy.isNotEmpty()) {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("IP address", copy))
                        Toast.makeText(context, "IP copied", Toast.LENGTH_SHORT).show()
                    }
                    vm.refreshPublicIp(l?.address, force = true)
                })
        }
    }
}

@Composable
private fun StatCell(label: String, first: String, second: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp)) {
        Text(label, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
        Text(first, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(second, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun speed(bytesPerSec: Double): String {
    val b = bytesPerSec.coerceAtLeast(0.0)
    return if (b >= 1_048_576) String.format(Locale.US, "%.1f MB/s", b / 1_048_576)
    else String.format(Locale.US, "%.0f KB/s", b / 1024)
}
