package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import com.cloudit24.stillpoint.data.Moon
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import com.cloudit24.stillpoint.data.DialMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableFloatStateOf
import com.cloudit24.stillpoint.data.InfoPanel
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextAlign
import android.os.BatteryManager
import android.content.Context
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

private data class HeroCard(val title: String, val subtitle: String, val color: Color, val small: Boolean = false, val clock: Boolean = false)

/**
 * Clock-free header: accent date line, a big headline that flips like a live tile
 * (time, screen time, weather, Hijri, Tamil), page dots, and a glass strip with network, memory and IP.
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
        // The greeting lives in the terminal display now; the headline starts with the time.
        add(HeroCard(formatClock(context, now), DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(zdt), Ink, clock = true))
        if (s.showUsage && vm.hasUsageAccess && vm.totalUsage > 0) {
            add(HeroCard(formatDuration(vm.totalUsage), "on screen today", Ink))
        }
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
        if (s.malayalamOn) {
            val parts = Calendars.malayalam(today).split(" · ", limit = 2)
            add(HeroCard(parts[0], parts.getOrElse(1) { "" }, Ink, small = true))
        }
        if (s.sakaOn) {
            val parts = Calendars.saka(today).split(" ")
            add(HeroCard("${parts[0]} ${parts[1]}", "${parts[2]} Saka · Indian national", Ink, small = true))
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
       if (s.dialMode != DialMode.OFF) DayDial(vm, s, now, Modifier.padding(start = 12.dp).size(104.dp))
      }
    }
}

/** The info zone under the headline: the chosen panels, then the quiet footer figures. */
@Composable
fun HeroInfo(vm: LauncherViewModel, s: LauncherSettings, now: Long) {
    InfoSlot(vm, s, now, Modifier)
    if (s.showStats || s.showLocalIp || s.publicIpOn) {
        StatsStrip(vm, s, Modifier.padding(top = 26.dp))
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
        // Shrinks until the whole line fits next to the dial (down to 22 sp) instead of cutting it off.
        // Fixed heights: every card takes exactly the same space (Tamil script is taller than English),
        // so nothing below moves when the card flips.
        var size by remember(display.title) { mutableFloatStateOf(if (display.small) 30f else 40f) }
        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.CenterStart) {
            Text(display.title, color = if (display.clock && LocalLook.current.neon) LocalLook.current.clock else display.color, fontSize = if (display.clock) 48.sp else size.sp,
                lineHeight = if (display.clock) 52.sp else 46.sp,
                fontWeight = if (display.clock) FontWeight.ExtraLight else FontWeight.Light,
                letterSpacing = if (display.clock) 1.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = if (display.clock) "tnum" else null),
                maxLines = 1, softWrap = false,
                overflow = if (size > 22f) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight(unbounded = true),
                onTextLayout = { if (it.hasVisualOverflow && size > 22f) size -= 2f })
        }
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
            Text(display.subtitle, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight(unbounded = true))
        }
    }
}

/**
 * The ring beside the headline: one clean circle, nothing else.
 * Prayer: the name of the prayer we're in, and the ring empties as its time runs out (between sunrise and Dhuhr
 * it shows Dhuhr and fills towards it). Battery: the ring is the charge. Time left today: the ring is the day left.
 */
@Composable
private fun DayDial(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val city = if (s.prayerOn && s.dialMode == DialMode.PRAYER) s.city else null
    val mode = if (s.dialMode == DialMode.PRAYER && city == null) DialMode.DAY else s.dialMode
    val midnight = remember(LocalDate.now()) { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val span = remember(now, city, s.prayerMethod, s.asrHanafi) {
        if (city == null) null else PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    }
    val (charge, charging) = remember(now) { battery(context) }

    // What the ring holds (0..1), the big word and the small line under it.
    val (fraction, big, small) = when {
        span != null && span.current.isPrayer -> {
            val left = (span.endsAt - now).coerceAtLeast(0)
            Triple(left.toFloat() / (span.endsAt - span.currentAt).coerceAtLeast(1), span.current.label,
                "${formatDuration(left)} left")
        }
        span != null -> Triple(
            ((now - span.currentAt).toFloat() / (span.nextAt - span.currentAt).coerceAtLeast(1)).coerceIn(0f, 1f),
            span.next.label, "in ${formatDuration(span.nextAt - now)}",
        )
        mode == DialMode.BATTERY -> Triple(charge.coerceIn(0, 100) / 100f, "$charge%", if (charging) "charging" else "battery")
        else -> {
            val left = (midnight + 86_400_000L - now).coerceAtLeast(0)
            Triple(left / 86_400_000f, formatDuration(left), "left today")
        }
    }
    val accent = Accent
    // Sweeps in when home opens.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(modifier.clickable(enabled = span != null) { vm.screen = Screen.PRAYER }, contentAlignment = Alignment.Center) {
        // The text may use only the middle of the circle; longer words (Maghrib, 23h 59m, charging) shrink to fit.
        val textWidth = maxWidth * 0.62f
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val r = size.minDimension / 2 - stroke
            drawCircle(Color.White.copy(alpha = 0.09f), r, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * fraction.coerceIn(0f, 1f) * intro.value, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.width(textWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(big, Ink, 18f, 10f)
            FitText(small, Muted, 11f, 8f)
        }
    }
}

/** One line that steps its size down until it fits its width, never spilling out. */
@Composable
private fun FitText(text: String, color: Color, maxSp: Float, minSp: Float) {
    var size by remember(text) { mutableFloatStateOf(maxSp) }
    Text(text, color = color, fontSize = size.sp, maxLines = 1, softWrap = false,
        overflow = if (size > minSp) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow && size > minSp) size -= 1f })
}

/**
 * The info zone under the headline. One card, or several you swipe across (small dots show where you are).
 * The cards share one fixed height, so nothing below moves when you swipe.
 */
@Composable
private fun InfoSlot(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier) {
    val shown = InfoPanel.entries.filter { p ->
        p in s.infoPanels && p != InfoPanel.PRAYER && (p != InfoPanel.WEATHER || (s.weatherOn && vm.weather != null))
    }
    if (shown.isEmpty()) return
    if (shown.size == 1) {
        Box(modifier) { InfoPage(vm, s, now, shown[0]) }
        return
    }
    val pager = rememberPagerState { shown.size }
    val accent = Accent
    Column(modifier) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(100.dp), pageSpacing = 24.dp, verticalAlignment = Alignment.Top) { i ->
            InfoPage(vm, s, now, shown[i])
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            shown.indices.forEach { i ->
                val on = i == pager.currentPage
                val w by animateDpAsState(if (on) 14.dp else 4.dp, tween(250), label = "page")
                Box(Modifier.height(4.dp).width(w).clip(RoundedCornerShape(2.dp))
                    .background(if (on) accent.copy(alpha = 0.8f) else Muted.copy(alpha = 0.3f)))
            }
        }
    }
}

@Composable
private fun InfoPage(vm: LauncherViewModel, s: LauncherSettings, now: Long, panel: InfoPanel) {
    when (panel) {
        InfoPanel.PRAYER -> Unit
        InfoPanel.AGENDA -> AgendaInfo(vm, now, Modifier)
        InfoPanel.DAY -> DayInfo(now, Modifier)
        InfoPanel.WEEK -> WeekInfo(s, Modifier)
        InfoPanel.WEATHER -> WeatherInfo(vm, s, Modifier)
        InfoPanel.WORLD -> WorldInfo(s, now, Modifier)
    }
}

/** This week, today in the accent, and today's date in each calendar you turned on. */
@Composable
private fun WeekInfo(s: LauncherSettings, modifier: Modifier) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val weeks = WeekFields.of(locale)
    val first = today.with(TemporalAdjusters.previousOrSame(weeks.firstDayOfWeek))
    val accent = Accent
    val line = remember(today, s.hijriOn, s.hijriAdjust, s.sakaOn, s.malayalamOn, s.tamilOn) {
        buildList {
            if (s.hijriOn) add(Calendars.hijri(today, s.hijriAdjust))
            if (s.sakaOn) add(Calendars.saka(today))
            if (s.malayalamOn) add(Calendars.malayalam(today).substringBefore(" · "))
            if (s.tamilOn) add(Calendars.tamil(today).substringBefore(" · "))
        }.ifEmpty { listOf("Week ${today.get(weeks.weekOfWeekBasedYear())}") }.joinToString("  ·  ")
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            (0L..6L).forEach { i ->
                val d = first.plusDays(i)
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale), maxLines = 1,
                        color = if (isToday) accent else Muted, fontSize = 10.sp)
                    Box(Modifier.padding(top = 4.dp).size(32.dp).clip(CircleShape).background(if (isToday) accent else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", fontSize = 15.sp, color = when {
                            isToday -> Color(0xFF0B0B0A)
                            d.isBefore(today) -> Muted
                            else -> Ink
                        })
                    }
                }
            }
        }
        Text(line, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
    }
}

/** The weather now, bigger than the headline card, with when it was last fetched. */
@Composable
internal fun WeatherInfo(vm: LauncherViewModel, s: LauncherSettings, modifier: Modifier) {
    val context = LocalContext.current
    val w = vm.weather ?: return
    val t = if (s.fahrenheit) w.tempC * 9 / 5 + 32 else w.tempC
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(w.code, w.isDay, animate = false, modifier = Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("${t.roundToInt()}° ${weatherKind(w.code).label}", color = Accent, fontSize = 28.sp,
                fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.city?.name, "updated ${formatClock(context, w.fetchedAt)}").joinToString(" · "),
                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Up to three cities: their time and how far they are from yours. */
@Composable
internal fun WorldInfo(s: LauncherSettings, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val cities = s.worldClocks.mapNotNull { e -> e.split("|").takeIf { it.size == 2 } }.take(3)
    if (cities.isEmpty()) {
        Text(stringResource(R.string.s_world_clock_choose_up_to_three), color = Muted, fontSize = 14.sp,
            modifier = modifier.padding(top = 8.dp))
        return
    }
    val here = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(now)
    val hereDate = at.atZone(here).toLocalDate()
    val fmt = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")
    Row(modifier.fillMaxWidth()) {
        cities.forEach { (label, zone) ->
            val z = runCatching { ZoneId.of(zone) }.getOrNull() ?: return@forEach
            val t = at.atZone(z)
            val diffMin = (z.rules.getOffset(at).totalSeconds - here.rules.getOffset(at).totalSeconds) / 60
            val dayShift = t.toLocalDate().compareTo(hereDate)
            val diff = buildString {
                if (diffMin == 0) append("same time") else {
                    val a = kotlin.math.abs(diffMin)
                    append(if (diffMin > 0) "+" else "−").append(a / 60).append("h")
                    if (a % 60 != 0) append(" ").append(a % 60).append("m")
                }
                if (dayShift > 0) append(" · tomorrow")
                if (dayShift < 0) append(" · yesterday")
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.format(fmt), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Text(diff, color = Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A plain progress line: how much is done. */
private fun DrawScope.progressTrack(accent: Color, frac: Float) {
    val y = size.height / 2
    val w = size.width
    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), 2.dp.toPx(), cap = StrokeCap.Round)
    if (frac > 0f) drawLine(accent, Offset(0f, y), Offset(w * frac.coerceIn(0f, 1f), y), 3.dp.toPx(), cap = StrokeCap.Round)
}

private val TIMELINE = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

/**
 * Prayer card: tonight's moon beside the prayer we're in and the next one, then all five times in a row
 * (now in the accent, next in white, past ones faded). Tap for the Qibla compass.
 */
@Composable
internal fun PrayerTimeline(vm: LauncherViewModel, s: LauncherSettings, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val city = s.city ?: return
    val today = LocalDate.now()
    val times = remember(today, city, s.prayerMethod, s.asrHanafi) {
        PrayerTimes.forDate(today, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    }
    val span = remember(now, city, s.prayerMethod, s.asrHanafi) {
        PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
    } ?: return
    val iqamaAt = if (span.current.isPrayer) span.currentAt + s.iqamaMin(span.current) * 60_000L else 0L
    val sub = when {
        now < iqamaAt -> "Iqama in ${formatDuration(iqamaAt - now)}"
        span.current == Prayer.FAJR -> "until sunrise ${formatClock(context, span.endsAt)}"
        span.current == Prayer.SUNRISE -> "Fajr has ended"
        else -> "since ${formatClock(context, span.currentAt)}"
    }
    val age = Moon.age(now)
    val hijri = remember(today, s.hijriAdjust) { Calendars.hijri(today, s.hijriAdjust).replace(Regex(" \\d+ AH$"), "") }
    val accent = Accent
    Column(
        modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { vm.screen = Screen.PRAYER },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonIcon(age, 60.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(span.current.label, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Text(span.current.arabic, color = Muted, fontSize = 14.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp))
                }
                Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.s_next), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
                Text("${span.next.label} ${formatClock(context, span.nextAt)}", color = Ink, fontSize = 15.sp, maxLines = 1)
                Text("in ${formatDuration(span.nextAt - now)}", color = accent, fontSize = 13.sp, maxLines = 1)
            }
        }
        Text(
            "${Moon.name(age)} · ${(Moon.illumination(age) * 100).roundToInt()}% lit · $hijri",
            color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            TIMELINE.forEach { p ->
                val at = times[p]
                val isNow = p == span.current
                val isNext = p == span.next
                val past = at != null && at < now && !isNow
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, color = if (isNow) accent else Muted.copy(alpha = if (past) 0.5f else 0.85f),
                        fontSize = 10.sp, letterSpacing = 0.5.sp, maxLines = 1)
                    Text(at?.let { formatClock(context, it) } ?: "—", maxLines = 1, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        color = when {
                            isNow -> accent
                            isNext -> Ink
                            past -> Muted.copy(alpha = 0.5f)
                            else -> Muted
                        })
                    Box(Modifier.padding(top = 6.dp).height(2.dp).width(18.dp).clip(RoundedCornerShape(1.dp)).background(
                        when {
                            isNow -> accent
                            isNext -> Ink.copy(alpha = 0.35f)
                            else -> Color.Transparent
                        }))
                }
            }
        }
    }
}

/** The moon as it looks tonight: dark disc, lit part, a faint halo that grows towards full moon. */
@Composable
private fun MoonIcon(age: Double, size: Dp) {
    val lit = Moon.illumination(age).toFloat()
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2 * 0.78f
        drawCircle(
            Brush.radialGradient(listOf(Ink.copy(alpha = 0.06f + 0.14f * lit), Color.Transparent), c, r * 1.28f),
            r * 1.28f, c,
        )
        drawCircle(Color(0xFF2A2A28), r, c)
        drawPath(Moon.litPath(age, c.x, c.y, r).asComposePath(), Ink)
    }
}

/** The next event today, with where it sits in the day. */
@Composable
private fun AgendaInfo(vm: LauncherViewModel, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun frac(ms: Long) = ((ms - midnight) / 86_400_000f).coerceIn(0f, 1f)
    val next = vm.agenda.firstOrNull { it.allDay || it.end > now }
    if (next == null) {
        InfoLayout("Free", null, "Nothing else on your calendar today", "", "", modifier = modifier) { accent ->
            barTrack(accent, frac(now), null)
        }
        return
    }
    val sub = when {
        next.allDay -> "All day"
        next.begin <= now -> "Now · until ${formatClock(context, next.end)}"
        else -> "${formatClock(context, next.begin)} – ${formatClock(context, next.end)}"
    }
    InfoLayout(
        title = next.title,
        titleExtra = null,
        sub = sub,
        right = "",
        rightSub = if (next.allDay || next.begin <= now) "now" else "in ${formatDuration(next.begin - now)}",
        modifier = modifier,
    ) { accent ->
        barTrack(accent, frac(now), if (next.allDay) null else frac(next.begin)..frac(next.end))
    }
}

/** How much of today is left, and the battery. */
@Composable
private fun DayInfo(now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val left = (midnight + 86_400_000L - now).coerceAtLeast(0)
    val (pct, charging) = remember(now) { battery(context) }
    InfoLayout(
        title = "${formatDuration(left)} left",
        titleExtra = null,
        sub = "of today",
        right = if (pct >= 0) "Battery $pct%" else "",
        rightSub = if (charging) "charging" else "",
        modifier = modifier,
    ) { accent ->
        barTrack(accent, ((now - midnight) / 86_400_000f).coerceIn(0f, 1f), null)
    }
}

private fun battery(context: Context): Pair<Int, Boolean> {
    val bm = context.getSystemService(BatteryManager::class.java) ?: return -1 to false
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
}

/** A day line: done part, ticks at 6, 12 and 18, an optional block (an event), and "now". */
private fun DrawScope.barTrack(accent: Color, now: Float, block: ClosedFloatingPointRange<Float>?) {
    val y = size.height / 2
    val w = size.width
    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), 2.dp.toPx(), cap = StrokeCap.Round)
    for (t in listOf(0.25f, 0.5f, 0.75f)) {
        drawLine(Color.White.copy(alpha = 0.2f), Offset(w * t, y - 4.dp.toPx()), Offset(w * t, y + 4.dp.toPx()), 1.dp.toPx())
    }
    block?.let { drawLine(accent.copy(alpha = 0.4f), Offset(w * it.start, y), Offset(w * it.endInclusive, y), 7.dp.toPx(), cap = StrokeCap.Round) }
    drawLine(accent, Offset(0f, y), Offset(w * now, y), 3.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(accent.copy(alpha = 0.25f), 8.dp.toPx(), Offset(w * now, y))
    drawCircle(Color.White, 3.dp.toPx(), Offset(w * now, y))
}

/** Big line on the left, short line on the right, a quiet second line under each, then a track. No box. */
@Composable
private fun InfoLayout(
    title: String,
    titleExtra: String?,
    sub: String,
    right: String,
    rightSub: String,
    modifier: Modifier,
    labels: List<Pair<String, Boolean>> = emptyList(),
    onClick: (() -> Unit)? = null,
    track: DrawScope.(Color) -> Unit,
) {
    val accent = Accent
    Column(
        modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
            ) else Modifier,
        ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(title, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                titleExtra?.let {
                    Text(it, color = Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 5.dp))
                }
            }
            if (right.isNotEmpty()) {
                Text(right, color = Ink, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
            }
        }
        Row(Modifier.padding(top = 2.dp)) {
            Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (rightSub.isNotEmpty()) {
                Text(rightSub, color = accent, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Canvas(Modifier.padding(top = 16.dp).fillMaxWidth().height(16.dp)) { track(accent) }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                labels.forEach { (text, on) ->
                    Text(text, color = if (on) accent else Muted.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
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
                delay(2_000) // Every 2 s is plenty for a speed figure, and half the work.
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

    // Quiet footer: no box, hairlines between the cells.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (s.showStats) {
            StatCell("NETWORK", "↓ $down", "↑ $up", Modifier.weight(1f).clickable { vm.screen = Screen.DATA })
            StatRule()
            StatCell("MEMORY", "$ramPct%", "of $ramTotal GB", Modifier.weight(0.8f).clickable { vm.screen = Screen.DATA })
        }
        if (s.showLocalIp || s.publicIpOn) {
            if (s.showStats) StatRule()
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
    Column(modifier.padding(end = 12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp, maxLines = 1)
        Text(first, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Light, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(second, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatRule() {
    Box(Modifier.padding(end = 12.dp, top = 2.dp, bottom = 2.dp).width(0.5.dp).fillMaxHeight()
        .background(Color.White.copy(alpha = 0.12f)))
}

private fun speed(bytesPerSec: Double): String {
    val b = bytesPerSec.coerceAtLeast(0.0)
    return if (b >= 1_048_576) String.format(Locale.US, "%.1f MB/s", b / 1_048_576)
    else String.format(Locale.US, "%.0f KB/s", b / 1024)
}
