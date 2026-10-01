package com.cloudit24.stillpoint.ui

import com.cloudit24.stillpoint.data.NotifyStyle
import androidx.compose.runtime.key
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import com.cloudit24.stillpoint.data.EdgeStyle
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.withFrameMillis
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.ProjectSource
import com.cloudit24.stillpoint.data.TaskSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import com.cloudit24.stillpoint.data.ClockStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AgendaItem
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.GestureSlot
import com.cloudit24.stillpoint.data.HomeAction
import com.cloudit24.stillpoint.data.HomeStyle
import com.cloudit24.stillpoint.data.GestureTarget
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val accent = Accent
    val now by rememberTicker(60_000L)
    val focusActive = s.focusEndsAt > now
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }
    val homeApps = vm.homeApps()
    // Gesture handlers are installed once; read the newest settings through this.
    val latest by rememberUpdatedState(s)
    val doubleTap = s.gesture(GestureSlot.DOUBLE_TAP)
    // The page follows the finger a little while swiping, then springs back.
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val offX by animateFloatAsState(if (dragging) dragX else 0f, if (dragging) snap<Float>() else spring<Float>(stiffness = Spring.StiffnessMediumLow), label = "dragX")
    val offY by animateFloatAsState(if (dragging) dragY else 0f, if (dragging) snap<Float>() else spring<Float>(stiffness = Spring.StiffnessMediumLow), label = "dragY")

    // Weather / gold: re-check every 5 minutes while home is shown; the view model skips fresh data.
    LaunchedEffect(s.weatherOn, s.goldOn, s.goldCurrency, s.city) {
        while (true) {
            vm.refreshLive()
            delay(5 * 60_000L)
        }
    }
    // Project Hub: every 2 minutes while home is shown.
    // The view model only asks the hub when the page's sync interval has passed.
    val usesHub = vm.usesHub()
    LaunchedEffect(usesHub) {
        while (usesHub) {
            vm.refreshHub()
            delay(60_000L)
        }
    }

    // Edge light on the curved sides.
    // Left: time left in the current prayer. Green, draining down; red and blinking in its last 15 minutes.
    // Right: the next prayer coming. Fills up; a spark runs up it in the last 10 minutes; glows from adhan to iqama.
    // Otherwise it's still and redrawn once a minute. The short animations run at ~20 frames a second
    // and pause whenever home isn't on screen or the screen is off.
    val edgeCity = s.city
    val edgeSpan = if (s.edgeStyle != EdgeStyle.OFF && s.prayerOn && edgeCity != null) {
        remember(now, edgeCity, s.prayerMethod, s.asrHanafi) {
            PrayerTimes.span(now, edgeCity.lat, edgeCity.lon, s.prayerMethod, s.asrHanafi)
        }
    } else null
    val edge = edgeSpan?.let { sp ->
        val iqamaAt = if (sp.current.isPrayer) sp.currentAt + s.iqamaMin(sp.current) * 60_000L else 0L
        EdgeInfo(
            progress = ((now - sp.currentAt).toFloat() / (sp.nextAt - sp.currentAt).coerceAtLeast(1)).coerceIn(0f, 1f),
            inPrayer = sp.current.isPrayer,
            ending = sp.current.isPrayer && sp.endsAt - now <= s.edgeWarnMin * 60_000L,
            starting = sp.nextAt - now <= 10 * 60_000L,
            started = now < iqamaAt,
        )
    }
    val edgeGrow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { edgeGrow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    var edgePhase by remember { mutableFloatStateOf(0f) }
    // Notification light: colours of the apps with something unread; important ones shine brighter.
    val lit = if (s.notifyLight || s.notifyDot) vm.litNotifications() else emptyList()
    val litColors = lit.sortedByDescending { it.important }.map { vm.notifyColor(it.pkg) }.distinct()
    val litImportant = lit.any { it.important }
    val edgeMoving = s.edgeMotion && ((edge != null && (edge.ending || edge.starting || edge.started)) || litColors.isNotEmpty())
    LaunchedEffect(edgeMoving) {
        if (edgeMoving) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { edgePhase = (it - start) / 1000f } // Waits while home is hidden.
                delay(50)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (edge != null) drawEdges(edge, s.edgeStyle, s.edgeRight, EDGE_BRIGHTNESS[s.edgeBright.coerceIn(1, 3) - 1], edgeGrow.value, edgePhase, accent)
                if (litColors.isNotEmpty()) {
                    drawNotifyLight(litColors, s.notifyStyle, if (s.edgeStyle == EdgeStyle.OFF) EdgeStyle.FLAT else s.edgeStyle,
                        edgePhase, s.edgeMotion, s.notifyLight, s.notifyDot, litImportant)
                }
            }
            .drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.1f, 0f),
                    radius = size.width * 1.1f,
                ))
            }
            .pointerInput(doubleTap) {
                detectTapGestures(
                    onDoubleTap = if (doubleTap == GestureTarget.action(HomeAction.NONE)) null
                    else { _ -> runTarget(vm, context, doubleTap) },
                    onLongPress = { vm.screen = Screen.SETTINGS },
                )
            }
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f; dragging = true },
                    onDragCancel = { dragging = false; dragX = 0f },
                    onDragEnd = {
                        dragging = false
                        dragX = 0f
                        if (total < -threshold) runTarget(vm, context, latest.gesture(GestureSlot.SWIPE_LEFT))
                        else if (total > threshold) runTarget(vm, context, latest.gesture(GestureSlot.SWIPE_RIGHT))
                    },
                    onHorizontalDrag = { change, dx ->
                        total += dx
                        dragX = total
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; dragging = true },
                    onDragCancel = { dragging = false; dragY = 0f },
                    onDragEnd = {
                        dragging = false
                        dragY = 0f
                        if (total < -threshold) runTarget(vm, context, latest.gesture(GestureSlot.SWIPE_UP))
                        else if (total > threshold) runTarget(vm, context, latest.gesture(GestureSlot.SWIPE_DOWN))
                    },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        dragY = total
                        change.consume()
                    },
                )
            }
            .graphicsLayer {
                translationX = offX * 0.25f
                translationY = offY * 0.2f
                alpha = (1f - (abs(offX) + abs(offY)) / (size.width * 1.5f)).coerceIn(0.5f, 1f)
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        // The frame (docs/DESIGN.md): headline, fixed · info zone, takes what is left and scrolls inside itself ·
        // recent apps, fixed · apps, at most a third of the screen, scrolls inside itself · shortcuts, fixed.
        // New things go inside a zone; zones never push each other off the screen.
        HeroHeader(vm, s, now)
        val infoScroll = rememberScrollState()
        // Scrolls only when it has to, so swipe gestures keep working when everything fits.
        Column(Modifier.weight(1f).padding(top = 26.dp).fadingEdges(infoScroll)
            .verticalScroll(infoScroll, enabled = infoScroll.maxValue > 0)) {
        HeroInfo(vm, s, now)

        if (s.showUsage) {
            // Screen time itself is one of the flipping headline cards.
            if (!vm.hasUsageAccess) {
                Text(
                    "Allow usage access to show screen time",
                    color = Accent, fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
            }
        }
        if (focusActive) {
            Text(
                "Focus until ${formatClock(context, s.focusEndsAt)}",
                color = Accent, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp).clickable { vm.screen = Screen.FOCUS },
            )
        }

        Column(Modifier.padding(top = 28.dp)) {
            if (s.hubOn) {
                if (s.projectsSource == ProjectSource.HUB) HubCard(vm, now)
            }
            if (s.showAgenda) AgendaBlock(context, vm.agenda)
        }
        }

        if (homeApps.isEmpty()) {
            Text("Swipe left for apps, right for widgets. Long-press for settings.", color = Muted, fontSize = 14.sp)
        }
        if (s.showRecent) {
            val recent = vm.recentlyUsed(6)
            if (recent.isNotEmpty()) RecentStrip(vm, recent)
        }
        // A set space for the apps (about a third of the screen); more apps scroll inside it.
        val listScale = 1f
        val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.34f).dp
        val appsScroll = rememberScrollState()
        Column(Modifier.heightIn(max = maxListHeight).fadingEdges(appsScroll).verticalScroll(appsScroll)) {
            if (s.homeStyle == HomeStyle.ICONS) {
                HomeIcons(vm, homeApps, (s.homeSize * 2).dp)
            } else {
                homeApps.forEach { app -> key(app.key) {
                    AppRow(
                        label = app.label,
                        usageMs = if (s.showUsage) vm.usage[app.packageName] else null,
                        fontSize = (s.homeSize * listScale).sp,
                        onClick = { vm.launch(app) },
                        icon = appIcon(vm, app, (s.homeSize * 1.4f * listScale).dp),
                        rowPadding = 5.dp,
                        dot = vm.dotFor(app.packageName),
                    )
                } }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val left = s.gesture(GestureSlot.SHORTCUT_LEFT)
            val right = s.gesture(GestureSlot.SHORTCUT_RIGHT)
            vm.targetLabel(left)?.let { label ->
                Text(label, color = Muted, modifier = Modifier.clickable { runTarget(vm, context, left) }.padding(vertical = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            // The middle shortcut (Settings, Gestures): Search by default, or any app or action, or nothing.
            val middle = s.gesture(GestureSlot.SHORTCUT_MIDDLE)
            vm.targetLabel(middle)?.let { label ->
                val isSearch = GestureTarget.actionOf(middle) == HomeAction.SEARCH
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                        .clickable { runTarget(vm, context, middle) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSearch) Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                    Text(label, color = Muted, fontSize = 14.sp, maxLines = 1,
                        modifier = Modifier.padding(start = if (isSearch) 8.dp else 0.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            vm.targetLabel(right)?.let { label ->
                Text(label, color = Muted, modifier = Modifier.clickable { runTarget(vm, context, right) }.padding(vertical = 10.dp))
            }
        }
    }
}

/** Hijri and Tamil dates; with both on, they take turns every few seconds with a small slide. */
@Composable
private fun RotatingLine(lines: List<String>) {
    if (lines.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size) {
        index = 0
        while (lines.size > 1) {
            delay(5_000)
            index = (index + 1) % lines.size
        }
    }
    AnimatedContent(
        targetState = lines[index % lines.size],
        contentAlignment = Alignment.TopEnd,
        transitionSpec = {
            (slideInVertically(tween(350)) { it } + fadeIn(tween(350))) togetherWith
                (slideOutVertically(tween(350)) { -it } + fadeOut(tween(250)))
        },
        label = "dates",
    ) { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.End, minLines = 2) }
}

/** Apps opened in the last 24 hours, newest first, as a quiet row of icons. */
@Composable
private fun RecentStrip(vm: LauncherViewModel, apps: List<AppEntry>) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text("RECENT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            apps.forEach { app ->
                key(app.key) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.launch(app) }) { AppIcon(vm, app, 34.dp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeIcons(vm: LauncherViewModel, apps: List<AppEntry>, size: Dp) {
    val context = LocalContext.current
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(size / 2),
        verticalArrangement = Arrangement.spacedBy(size / 3),
    ) {
        apps.forEach { app ->
            Box(
                Modifier.combinedClickable(
                    onClick = { vm.launch(app) },
                    onLongClick = { Toast.makeText(context, app.label, Toast.LENGTH_SHORT).show() },
                ),
            ) { AppIcon(vm, app, size) }
        }
    }
}

/**
 * Project Hub: the ONE next thing and why, with Done / Not now. Everything else is one quiet line.
 * Shows the last answer when offline, with its age.
 */
@Composable
private fun HubCard(vm: LauncherViewModel, now: Long) {
    val context = LocalContext.current
    val g = vm.hub
    if (g == null) {
        Text(vm.hubError ?: "Loading Project Hub…", color = Muted, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 20.dp).clickable { vm.refreshHub(force = true) })
        return
    }
    val n = g.now
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Accent))
        Column(Modifier.padding(start = 14.dp)) {
            Text(n.label.uppercase(Locale.getDefault()), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp)
            Text(
                n.title, color = Ink, fontSize = 22.sp, lineHeight = 27.sp,
                modifier = Modifier.padding(top = 2.dp).clickable { vm.hubUrl()?.let { context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
            )
            if (n.project.isNotBlank()) Text(n.project, color = Muted, fontSize = 13.sp)
            Text(n.why, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (n.kind == "task") {
                Row(Modifier.padding(top = 8.dp)) {
                    Text("✓ Done", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { vm.hubDone(n.id) }.padding(end = 24.dp, top = 6.dp, bottom = 6.dp))
                    Text("Not now", color = Muted, fontSize = 16.sp,
                        modifier = Modifier.clickable { vm.hubNotNow(n.id) }.padding(vertical = 6.dp))
                }
            }
            val bits = listOfNotNull(
                g.doneToday.takeIf { it > 0 }?.let { "$it done today" },
                g.nextEventStart?.let { "next ${it} ${g.nextEventTitle.orEmpty()}" },
                g.due.takeIf { it > 0 }?.let { "$it due today" },
                g.overdue.takeIf { it > 0 }?.let { "$it overdue" },
                g.lost.takeIf { it > 0 }?.let { "$it lost" },
            )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), color = Muted, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            vm.hubError?.let {
                val mins = ((now - g.fetchedAt) / 60_000L).coerceAtLeast(0)
                Text("Offline · from ${if (mins < 1) "just now" else "$mins min ago"}", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable { vm.refreshHub(force = true) })
            }
        }
    }
}

@Composable
private fun AgendaBlock(context: Context, items: List<AgendaItem>) {
    Text("Today", color = Muted, fontSize = 13.sp)
    if (items.isEmpty()) {
        Text("Nothing else scheduled", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
    items.forEach { e ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text(
                if (e.allDay) "All day" else formatClock(context, e.begin),
                color = Muted, fontSize = 15.sp, modifier = Modifier.width(76.dp),
            )
            Text(e.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(20.dp))
}

/** Home card for projects kept on the phone: the first project with a next step. */
@Composable
private fun LocalProjectCard(vm: LauncherViewModel) {
    val p = vm.projects.firstOrNull { it.next.isNotBlank() }
    if (p == null) {
        Text("No next step yet. Add projects on the Shelf.", color = Muted, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 20.dp).clickable { vm.screen = Screen.WIDGETS })
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Accent))
        Column(Modifier.padding(start = 14.dp)) {
            Text("NEXT STEP", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            Text(p.next, color = Ink, fontSize = 22.sp, lineHeight = 27.sp, modifier = Modifier.padding(top = 2.dp))
            Text(p.name, color = Muted, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp)) {
                Text("✓ Done", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { vm.projectStepDone(p.id) }.padding(end = 24.dp, top = 6.dp, bottom = 6.dp))
                Text("Later", color = Muted, fontSize = 16.sp,
                    modifier = Modifier.clickable { vm.projectLater(p.id) }.padding(vertical = 6.dp))
            }
        }
    }
}

private class EdgeInfo(val progress: Float, val inPrayer: Boolean, val ending: Boolean, val starting: Boolean, val started: Boolean)

private val EDGE_BRIGHTNESS = floatArrayOf(0.45f, 0.75f, 1f)

/**
 * One line in the accent colour. The bright part is the next prayer, growing from the start and pushing the dim part,
 * what's left of the current prayer, out of the far end. The dim part blinks before the prayer ends, a spark runs
 * along the bright part in the last 10 minutes, and the whole line glows from the adhan to the iqama.
 */
private fun DrawScope.drawEdges(e: EdgeInfo, style: EdgeStyle, right: Boolean, bright: Float, grow: Float, phase: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val (from, to) = when (style) {
        EdgeStyle.BOTTOM -> {
            val y = h - 4.dp.toPx()
            Offset(28.dp.toPx(), y) to Offset(w - 28.dp.toPx(), y)
        }
        EdgeStyle.FLAT -> {
            val x = if (right) w - 6.dp.toPx() else 6.dp.toPx()
            Offset(x, h - 56.dp.toPx()) to Offset(x, 56.dp.toPx())
        }
        else -> {
            val x = if (right) w - 1.dp.toPx() else 1.dp.toPx()
            Offset(x, h) to Offset(x, 0f)
        }
    }
    val line = 2.dp.toPx()
    drawLine(Color.White.copy(alpha = 0.05f), from, to, line, cap = StrokeCap.Round)

    if (e.started) {
        edgeBeam(from, to, grow, accent, bright * (0.4f + 0.6f * wave(phase, 3f)))
        return
    }
    val split = lerp(from, to, e.progress * grow)
    // Old: the current prayer, being pushed out.
    val old = when {
        !e.inPrayer -> 0.15f
        e.ending -> 0.15f + 0.75f * wave(phase, 1.2f)
        else -> 0.35f
    }
    if ((to - split).getDistance() > 1f) drawLine(accent.copy(alpha = old * bright), split, to, line, cap = StrokeCap.Round)
    // New: the next prayer, pushing in.
    edgeBeam(from, to, e.progress * grow, accent, bright)
    if (e.starting && e.progress > 0f) {
        val t = (phase % 1.8f) / 1.8f
        val at = lerp(from, split, t)
        val fade = sin(t * PI.toFloat())
        drawCircle(accent.copy(alpha = 0.35f * fade * bright), 12.dp.toPx(), at)
        drawCircle(Color.White.copy(alpha = 0.9f * fade * bright), 2.5.dp.toPx(), at)
    }
}

/** 0..1..0 over [period] seconds. */
private fun wave(phase: Float, period: Float): Float = 0.5f + 0.5f * cos(phase * 2f * PI.toFloat() / period)

/** A line from [from] towards [to], [frac] of the way, with a soft glow and a bright tip. */
private fun DrawScope.edgeBeam(from: Offset, to: Offset, frac: Float, color: Color, alpha: Float) {
    if (frac <= 0f) return
    val tip = lerp(from, to, frac.coerceAtMost(1f))
    if ((tip - from).getDistance() < 1f) return
    drawLine(
        Brush.linearGradient(listOf(color.copy(alpha = 0.22f * alpha), Color.Transparent), start = tip, end = from),
        tip, from, 14.dp.toPx(),
    )
    drawLine(
        Brush.linearGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0.25f * alpha)), start = tip, end = from),
        tip, from, 2.dp.toPx(), cap = StrokeCap.Round,
    )
    drawCircle(color.copy(alpha = 0.3f * alpha), radius = 10.dp.toPx(), center = tip)
    drawCircle(color.copy(alpha = alpha), radius = 3.dp.toPx(), center = tip)
}

/**
 * Unread notifications on the screen edges, in each app's colour (taking turns when there are several),
 * and a signal dot at the top right, like a BlackBerry LED. Still and softly lit when Animations is off.
 */
private fun DrawScope.drawNotifyLight(
    colors: List<Color>, style: NotifyStyle, geo: EdgeStyle, phase: Float, motion: Boolean,
    edges: Boolean, dot: Boolean, important: Boolean,
) {
    val strength = if (important) 1f else 0.75f
    val color = colors[(phase / 2.4f).toInt().coerceAtLeast(0) % colors.size]
    val w = size.width
    val h = size.height
    val tracks = when (geo) {
        EdgeStyle.BOTTOM -> listOf(Offset(28.dp.toPx(), h - 4.dp.toPx()) to Offset(w - 28.dp.toPx(), h - 4.dp.toPx()))
        EdgeStyle.FLAT -> {
            val x = 6.dp.toPx()
            val m = 56.dp.toPx()
            listOf(Offset(x, h - m) to Offset(x, m), Offset(w - x, h - m) to Offset(w - x, m))
        }
        else -> {
            val x = 1.dp.toPx()
            listOf(Offset(x, h) to Offset(x, 0f), Offset(w - x, h) to Offset(w - x, 0f))
        }
    }
    if (edges) for ((a, b) in tracks) {
        when (style) {
            NotifyStyle.BREATHE -> glowLine(a, b, color, strength * if (motion) 0.25f + 0.65f * wave(phase, 2.4f) else 0.7f)
            NotifyStyle.BLINK -> glowLine(a, b, color, strength * if (!motion || phase % 3f < 0.22f) 1f else 0.06f)
            NotifyStyle.SWEEP -> if (!motion) glowLine(a, b, color, strength * 0.7f) else {
                val t = (phase % 2f) / 2f
                val head = lerp(a, b, t)
                val tail = lerp(a, b, (t - 0.3f).coerceAtLeast(0f))
                glowLine(a, b, color, strength * 0.12f)
                if ((head - tail).getDistance() > 1f) {
                    drawLine(Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.3f * strength)), start = tail, end = head),
                        tail, head, 14.dp.toPx())
                    drawLine(Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = strength)), start = tail, end = head),
                        tail, head, 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
    if (dot && (!motion || phase % 2.5f < 0.3f)) {
        val c = Offset(w - 14.dp.toPx(), 10.dp.toPx())
        drawCircle(color.copy(alpha = 0.3f * strength), 10.dp.toPx(), c)
        drawCircle(color.copy(alpha = strength), 4.5.dp.toPx(), c)
    }
}

private fun DrawScope.glowLine(a: Offset, b: Offset, color: Color, alpha: Float) {
    drawLine(color.copy(alpha = 0.25f * alpha), a, b, 14.dp.toPx())
    drawLine(color.copy(alpha = alpha), a, b, 2.dp.toPx(), cap = StrokeCap.Round)
}
