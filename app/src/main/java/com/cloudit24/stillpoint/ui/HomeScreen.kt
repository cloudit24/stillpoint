package com.cloudit24.stillpoint.ui

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
    LaunchedEffect(s.hubOn) {
        while (s.hubOn) {
            vm.refreshHub()
            delay(2 * 60_000L)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
            if (s.hubOn) HubCard(vm, now)
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

/** Shown on the widget page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksBlock(vm: LauncherViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Connected to Project Hub: overdue + today's tasks come from the hub, and new ones go there.
    val hub = vm.settings.hubOn && vm.hubConnected()
    Text(if (hub) "Tasks · Project Hub" else "Tasks", color = Muted, fontSize = 13.sp)
    if (hub) {
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
    } else vm.tasks.forEach { t ->
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
    BasicTextField(
        value = input,
        onValueChange = { input = it },
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (hub) vm.hubAdd(input) else vm.addTask(input)
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
