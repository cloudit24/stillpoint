package com.cloudit24.stillpoint.ui

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
            left = if (!sp.current.isPrayer) null
            else (1f - (now - sp.currentAt).toFloat() / (sp.endsAt - sp.currentAt).coerceAtLeast(1)).coerceIn(0f, 1f),
            ending = sp.current.isPrayer && sp.endsAt - now <= s.edgeWarnMin * 60_000L,
            right = ((now - sp.currentAt).toFloat() / (sp.nextAt - sp.currentAt).coerceAtLeast(1)).coerceIn(0f, 1f),
            starting = sp.nextAt - now <= 10 * 60_000L,
            started = now < iqamaAt,
        )
    }
    val edgeGrow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { edgeGrow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    var edgePhase by remember { mutableFloatStateOf(0f) }
    val edgeMoving = s.edgeMotion && edge != null && (edge.ending || edge.starting || edge.started)
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
                if (edge != null) drawEdges(edge, s.edgeStyle, EDGE_BRIGHTNESS[s.edgeBright.coerceIn(1, 3) - 1], edgeGrow.value, edgePhase, accent)
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
        // Clock on the left; dates and live system info stacked on the right.
        val today = LocalDate.now()
        val hijri = if (s.hijriOn) remember(today, s.hijriAdjust) { Calendars.hijri(today, s.hijriAdjust) } else null
        val tamil = if (s.tamilOn) remember(today) { Calendars.tamil(today) } else null
        if (s.clockStyle == ClockStyle.HEADLINE) HeroHeader(vm, s, now)
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column {
                Clock(s.clockStyle, now)
                if (s.prayerOn || s.goldOn) {
                    // Thin accent bar in the margin holds the two together.
                    Row(Modifier.padding(top = 10.dp).offset(x = (-14).dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(Accent))
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.prayerOn) PrayerLine(vm, s, now, compact = true)
                            if (s.goldOn) GoldLine(vm, s, compact = true)
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).padding(start = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(dateFmt.format(today), color = Ink, fontSize = 16.sp, textAlign = TextAlign.End)
                RotatingLine(listOfNotNull(
                    hijri?.replace(Regex(" (\\d+ AH)$"), "\n$1"),
                    tamil?.replaceFirst(" · ", "\n"),
                ))
                if (s.showStats) SystemStatsLine(onClick = { vm.screen = Screen.DATA })
                if (s.showLocalIp || s.publicIpOn) IpLine(vm, s)
            }
        }

        if (s.showUsage) {
            if (vm.hasUsageAccess) {
                Text(
                    "${formatDuration(vm.totalUsage)} on screen today",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp),
                )
            } else {
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

        Column(Modifier.weight(1f).padding(top = 28.dp).verticalScroll(rememberScrollState())) {
            if (s.hubOn) {
                if (s.projectsSource == ProjectSource.HUB) HubCard(vm, now) else LocalProjectCard(vm)
            }
            if (s.showAgenda) AgendaBlock(context, vm.agenda)
        }

        if (homeApps.isEmpty()) {
            Text("Swipe left for apps, right for widgets. Long-press for settings.", color = Muted, fontSize = 14.sp)
        }
        if (s.homeStyle == HomeStyle.TILES) {
            HomeTiles(vm, homeApps)
        } else if (s.homeStyle == HomeStyle.ICONS) {
            HomeIcons(vm, homeApps, (s.homeSize * 2).dp)
        } else {
            homeApps.forEach { app ->
                AppRow(
                    label = app.label,
                    usageMs = if (s.showUsage) vm.usage[app.packageName] else null,
                    fontSize = s.homeSize.sp,
                    onClick = { vm.launch(app) },
                    icon = appIcon(vm, app, (s.homeSize * 1.4f).dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            val left = s.gesture(GestureSlot.SHORTCUT_LEFT)
            val right = s.gesture(GestureSlot.SHORTCUT_RIGHT)
            vm.targetLabel(left)?.let { label ->
                Text(label, color = Muted, modifier = Modifier.clickable { runTarget(vm, context, left) }.padding(vertical = 10.dp))
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

/** Windows Phone start screen: square accent tiles, three across. Long-press shows the full name. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTiles(vm: LauncherViewModel, apps: List<AppEntry>) {
    val context = LocalContext.current
    val accent = Accent
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.chunked(3).forEach { rowApps ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowApps.forEach { app ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).background(accent).combinedClickable(
                            onClick = { vm.launch(app) },
                            onLongClick = { Toast.makeText(context, app.label, Toast.LENGTH_SHORT).show() },
                        ),
                    ) {
                        Box(Modifier.align(Alignment.Center)) { AppIcon(vm, app, 40.dp) }
                        Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
                repeat(3 - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Icons-only home row. Long-press shows the app name. */
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
        Text("No next step yet. Add projects on the widget page.", color = Muted, fontSize = 14.sp,
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

/** Shown on the widget page. Where the tasks come from is chosen in Settings, Tasks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksBlock(vm: LauncherViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val src = vm.settings.tasksSource
    val hub = src == TaskSource.HUB && vm.hubConnected()
    val provider = src.authority != null
    LaunchedEffect(src) { if (provider) vm.loadProviderTasks() }

    Text(if (src == TaskSource.PHONE) "Tasks" else "Tasks · ${src.label}", color = Muted, fontSize = 13.sp)
    when {
        src == TaskSource.HUB && !hub ->
            Text("Connect Project Hub in Settings, Extras.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
        hub -> {
            val list = vm.hub?.tasks.orEmpty()
            if (list.isEmpty()) Text("Nothing due today.", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
            list.forEach { t ->
                Text(
                    t.text + (t.due?.let { if (it < LocalDate.now().toString()) "  · overdue" else "" } ?: ""),
                    fontSize = 16.sp,
                    color = if (t.done) Muted else Ink,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.fillMaxWidth().clickable { vm.hubDone(t.id, !t.done) }.padding(vertical = 6.dp),
                )
            }
        }
        provider -> {
            vm.providerError?.let {
                Text(it, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp).clickable { vm.loadProviderTasks() })
            }
            if (vm.providerError == null && vm.providerTasks.isEmpty()) {
                Text("No open tasks.", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            vm.providerTasks.forEach { t ->
                Text(t.text, fontSize = 16.sp, color = Ink,
                    modifier = Modifier.fillMaxWidth().clickable { vm.providerDone(t.id) }.padding(vertical = 6.dp))
            }
        }
        else -> vm.tasks.forEach { t ->
            Text(
                t.text,
                fontSize = 16.sp,
                color = if (t.done) Muted else Ink,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { vm.toggleTask(t.id) }, onLongClick = { vm.deleteTask(t.id) })
                    .padding(vertical = 6.dp),
            )
        }
    }
    if (src != TaskSource.HUB || hub) {
        BasicTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when {
                    hub -> vm.hubAdd(input)
                    provider -> vm.providerAdd(input)
                    else -> vm.addTask(input)
                }
                input = ""
                focusManager.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            decorationBox = { inner ->
                if (input.isEmpty()) Text("Add a task", color = Muted, fontSize = 16.sp)
                inner()
            },
        )
    }
}

private class EdgeInfo(val left: Float?, val ending: Boolean, val right: Float, val starting: Boolean, val started: Boolean)

private val EdgeCalm = Color(0xFF8FC4A8)
private val EdgeRed = Color(0xFFE5484D)

private val EDGE_BRIGHTNESS = floatArrayOf(0.45f, 0.75f, 1f)

/** Each side is a track from "empty" to "full": up the sides, or from the outside towards the middle at the bottom. */
private fun DrawScope.drawEdges(e: EdgeInfo, style: EdgeStyle, bright: Float, grow: Float, phase: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val (lFrom, lTo, rFrom, rTo) = when (style) {
        EdgeStyle.BOTTOM -> {
            val y = h - 4.dp.toPx()
            val gap = 10.dp.toPx()
            val pad = 28.dp.toPx()
            listOf(Offset(pad, y), Offset(w / 2 - gap, y), Offset(w - pad, y), Offset(w / 2 + gap, y))
        }
        EdgeStyle.FLAT -> {
            val x = 6.dp.toPx()
            val m = 56.dp.toPx()
            listOf(Offset(x, h - m), Offset(x, m), Offset(w - x, h - m), Offset(w - x, m))
        }
        else -> {
            val x = 1.dp.toPx()
            listOf(Offset(x, h), Offset(x, 0f), Offset(w - x, h), Offset(w - x, 0f))
        }
    }
    val line = 2.dp.toPx()
    drawLine(Color.White.copy(alpha = 0.05f), lFrom, lTo, line, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.05f), rFrom, rTo, line, cap = StrokeCap.Round)

    // Left: what's left of the current prayer.
    e.left?.let { left ->
        if (e.ending) edgeBeam(lFrom, lTo, left * grow, EdgeRed, bright * (0.25f + 0.75f * wave(phase, 1.2f)))
        else edgeBeam(lFrom, lTo, left * grow, EdgeCalm, bright * 0.85f)
    }

    // Right: the next prayer coming, or the one that just began.
    if (e.started) {
        edgeBeam(rFrom, rTo, grow, accent, bright * (0.4f + 0.6f * wave(phase, 3f)))
    } else {
        edgeBeam(rFrom, rTo, e.right * grow, accent, bright)
        if (e.starting && e.right > 0f) {
            val t = (phase % 1.8f) / 1.8f
            val at = lerp(rFrom, lerp(rFrom, rTo, e.right * grow), t)
            val fade = sin(t * PI.toFloat())
            drawCircle(accent.copy(alpha = 0.35f * fade * bright), 12.dp.toPx(), at)
            drawCircle(Color.White.copy(alpha = 0.9f * fade * bright), 2.5.dp.toPx(), at)
        }
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
