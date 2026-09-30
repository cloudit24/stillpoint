package com.cloudit24.stillpoint.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import android.appwidget.AppWidgetManager
import android.util.SizeF
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import android.os.Build
import android.os.Bundle
import android.appwidget.AppWidgetHostView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.cloudit24.stillpoint.data.LocalProject
import com.cloudit24.stillpoint.data.ProjectSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen

/** Swipe-left page. Widgets stack vertically; Edit shows a Remove link above each. */
@Composable
fun WidgetsScreen(vm: LauncherViewModel, onAddWidget: (AppWidgetProviderInfo) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        WidgetPicker(vm) { picking = false; onAddWidget(it) }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (kotlin.math.abs(total) > threshold) vm.screen = Screen.HOME },
                    onHorizontalDrag = { change, dx ->
                        total += dx
                        change.consume()
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (vm.widgetIds.isNotEmpty()) {
                Text(if (editing) "Done" else "Edit", color = Muted,
                    modifier = Modifier.clickable { editing = !editing }.padding(8.dp))
            }
        }

        Column(Modifier.weight(1f).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
            if (vm.settings.showTasks) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 20.dp)) { TasksBlock(vm) }
            }
            if (vm.settings.hubOn && vm.settings.projectsSource == ProjectSource.PHONE) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 20.dp)) { ProjectsBlock(vm) }
            }
            if (vm.widgetIds.isEmpty()) {
                Text("No widgets yet. Tap Add widget below.", color = Muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp))
            }
            vm.widgetIds.forEachIndexed { i, id -> key(id) { AppearIn(i) { WidgetItem(vm, id, editing) } } }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp)) {
            Text("Home", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.HOME }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("Add widget", color = Muted, modifier = Modifier.clickable { editing = false; picking = true }.padding(8.dp))
        }
    }
}

@Composable
private fun WidgetItem(vm: LauncherViewModel, id: Int, editing: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val info = remember(id) { vm.widgetManager.getAppWidgetInfo(id) }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (editing || info == null) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(info?.loadLabel(context.packageManager) ?: "Widget no longer available",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Remove", color = Accent, modifier = Modifier.clickable { vm.removeWidget(id) }.padding(8.dp))
            }
        }
        if (info == null) return@Column
        val natural = remember(info) { naturalSize(info, density.density) }
        val saved = vm.widgetSizes[id]
        val heightDp = saved?.first ?: natural.height
        // Width: 0 = full, -1 = the widget's own width, more = chosen by dragging the corner.
        val widthSetting = saved?.second ?: if (natural.fullWidth) 0 else -1
        if (editing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Height", color = Muted, fontSize = 13.sp)
                Text("−", fontSize = 20.sp, modifier = Modifier.clip(CircleShape)
                    .clickable { vm.setWidgetSize(id, (heightDp - 20).coerceAtLeast(natural.minHeight), widthSetting) }
                    .padding(horizontal = 12.dp))
                Text("$heightDp", fontSize = 14.sp)
                Text("+", fontSize = 20.sp, modifier = Modifier.clip(CircleShape)
                    .clickable { vm.setWidgetSize(id, (heightDp + 20).coerceAtMost(natural.maxHeight), widthSetting) }
                    .padding(horizontal = 12.dp))
                Spacer(Modifier.weight(1f))
                Text(if (widthSetting == 0) "Full width" else "Own width", color = Accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.setWidgetSize(id, heightDp, if (widthSetting == 0) -1 else 0) }.padding(8.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val maxW = maxWidth
            val targetW = when {
                widthSetting == 0 -> maxW
                widthSetting < 0 -> minOf(maxW, natural.width.dp)
                else -> minOf(maxW, widthSetting.dp)
            }
            // While the corner is dragged the widget follows the finger; otherwise size changes glide.
            var dragW by remember { mutableStateOf<Dp?>(null) }
            var dragH by remember { mutableStateOf<Dp?>(null) }
            val dragging = dragW != null
            val w by animateDpAsState(dragW ?: targetW, if (dragging) snap<Dp>() else spring<Dp>(stiffness = Spring.StiffnessMediumLow), label = "w")
            val h by animateDpAsState(dragH ?: heightDp.dp, if (dragging) snap<Dp>() else spring<Dp>(stiffness = Spring.StiffnessMediumLow), label = "h")
            val curW by rememberUpdatedState(w)
            val curH by rememberUpdatedState(h)
            var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
            Box(Modifier.width(w).height(h)) {
                AndroidView(
                    factory = { ctx -> vm.widgetHost.createView(ctx, id, info).also { hostView = it } },
                    modifier = Modifier
                        .fillMaxSize()
                        // Lets lists inside widgets (mail, calendar, notes) scroll instead of the page.
                        .nestedScroll(rememberNestedScrollInteropConnection())
                        .onSizeChanged { size -> hostView?.let { reportSize(it, size.width / density.density, size.height / density.density) } },
                )
                if (editing) {
                    val accent = Accent
                    Box(Modifier.matchParentSize().border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp)))
                    // Corner handle: drag to resize, like on iPhone and Pixel.
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(40.dp).pointerInput(id, maxW) {
                            detectDragGestures(
                                onDragStart = { dragW = curW; dragH = curH },
                                onDragCancel = { dragW = null; dragH = null },
                                onDragEnd = {
                                    val fw = dragW ?: curW
                                    val fh = dragH ?: curH
                                    val hh = ((fh.value / 4).roundToInt() * 4).coerceIn(natural.minHeight, natural.maxHeight)
                                    val ww = if (fw >= maxW - 4.dp) 0 else (fw.value / 4).roundToInt() * 4
                                    vm.setWidgetSize(id, hh, ww)
                                    dragW = null
                                    dragH = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    // The widget is centred, so the width grows on both sides: twice the finger's move.
                                    dragW = ((dragW ?: curW) + (amount.x * 2).toDp()).coerceIn(120.dp, maxW)
                                    dragH = ((dragH ?: curH) + amount.y.toDp())
                                        .coerceIn(natural.minHeight.dp, natural.maxHeight.dp)
                                },
                            )
                        },
                    ) {
                        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                            val s = size.width
                            val stroke = 3.dp.toPx()
                            drawArc(accent, 0f, 90f, useCenter = false, topLeft = Offset(-s, -s), size = Size(s * 2, s * 2),
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

/** Widgets settle into place when the page opens: a short fade and rise, one after another. Nothing more. */
@Composable
private fun AppearIn(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 14.dp.toPx() }) { content() }
}

private class NaturalSize(val width: Int, val height: Int, val minHeight: Int, val maxHeight: Int, val fullWidth: Boolean)

/**
 * The size a widget asks for, in dp. Uses its target cells (Android 12+) or the classic
 * "70 dp per cell minus 30" rule, with about 96 x 88 dp per home-screen cell.
 */
private fun naturalSize(info: AppWidgetProviderInfo, density: Float): NaturalSize {
    val minW = (info.minWidth / density).toInt()
    val minH = (info.minHeight / density).toInt()
    val cellsW = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth
    else ((minW + 30) / 70f).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    val cellsH = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight
    else ((minH + 30) / 70f).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    val width = maxOf(minW, cellsW * 96)
    val height = maxOf(minH, cellsH * 88, 56)
    val resizeMin = (info.minResizeHeight / density).toInt().takeIf { it > 0 } ?: minH
    val resizeMax = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) (info.maxResizeHeight / density).toInt() else 640
    return NaturalSize(width, height, resizeMin.coerceAtLeast(40), maxOf(resizeMax, height), fullWidth = cellsW >= 4 || minW >= 250)
}

/** Tells the widget the exact space it has, the way launchers do, so it picks the layout that fits. */
private fun reportSize(view: AppWidgetHostView, wDp: Float, hDp: Float) {
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
    }
    runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            view.updateAppWidgetSize(options, listOf(SizeF(wDp, hDp)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(options, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
        }
    }
}

/** Widgets grouped under their app, apps A to Z, with Stillpoint's own widget first and open. */
@Composable
private fun WidgetPicker(vm: LauncherViewModel, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val own = context.packageName
    val groups = remember {
        vm.widgetProviders()
            .groupBy { it.provider.packageName }
            .map { (pkg, list) -> Triple(pkg, if (pkg == own) "Stillpoint" else appLabel(pm, pkg), list) }
            .sortedWith(compareBy({ it.first != own }, { it.second.lowercase() }))
    }
    var open by remember { mutableStateOf(setOf(own)) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Row(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add widget", fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("${groups.size} apps", color = Muted, fontSize = 13.sp)
            }
        }
        if (groups.isEmpty()) {
            item { Text("No widgets found on this phone.", color = Muted, fontSize = 14.sp) }
        }
        groups.forEach { (pkg, label, list) ->
            val isOpen = pkg in open
            item(key = "app_$pkg") {
                val app = remember(pkg) { vm.apps.firstOrNull { it.packageName == pkg } }
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (isOpen) open - pkg else open + pkg }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app != null) AppIcon(vm, app, 36.dp) else Box(Modifier.size(36.dp).clip(CircleShape).background(Accent))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(label, fontSize = 17.sp, color = if (pkg == own) Accent else Ink)
                        Text(if (list.size == 1) "1 widget" else "${list.size} widgets", color = Muted, fontSize = 13.sp)
                    }
                    Text(if (isOpen) "▾" else "▸", color = Muted, fontSize = 16.sp)
                }
            }
            if (isOpen) {
                items(list, key = { "${it.provider.flattenToString()}#${it.profile.hashCode()}" }) { p ->
                    val dm = context.resources.displayMetrics
                    // Rough size in home-screen cells (about 74dp each), as other launchers show it.
                    val cols = ((p.minWidth / dm.density + 30) / 74).toInt().coerceAtLeast(1)
                    val rows = ((p.minHeight / dm.density + 30) / 74).toInt().coerceAtLeast(1)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 50.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(p) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.loadLabel(pm), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("$cols × $rows", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

/** Projects kept on the phone, each with its next step. Tap to edit, long-press to delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectsBlock(vm: LauncherViewModel) {
    var editing by remember { mutableStateOf<LocalProject?>(null) }
    var adding by remember { mutableStateOf(false) }
    Text("Projects", color = Muted, fontSize = 13.sp)
    vm.projects.forEach { p ->
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { editing = p }, onLongClick = { vm.deleteProject(p.id) })
                .padding(vertical = 6.dp),
        ) {
            Text(p.name, fontSize = 16.sp)
            Text(if (p.next.isBlank()) "No next step · tap to add one" else "Next: ${p.next}", color = Muted, fontSize = 13.sp)
        }
    }
    Text("+ New project", color = Muted, fontSize = 15.sp, modifier = Modifier.clickable { adding = true }.padding(vertical = 8.dp))
    val e = editing
    if (adding || e != null) {
        ProjectDialog(e, onDismiss = { adding = false; editing = null }) { name, next ->
            if (e != null) vm.updateProject(e.copy(name = name, next = next)) else vm.addProject(name, next)
            adding = false
            editing = null
        }
    }
}

@Composable
private fun ProjectDialog(initial: LocalProject?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var next by remember { mutableStateOf(initial?.next.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(name, next) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (initial == null) "New project" else "Edit project") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Project") })
                OutlinedTextField(value = next, onValueChange = { next = it }, singleLine = true, label = { Text("Next step") },
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
    )
}
