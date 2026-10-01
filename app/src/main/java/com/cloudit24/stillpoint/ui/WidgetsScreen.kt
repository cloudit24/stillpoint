package com.cloudit24.stillpoint.ui

import com.cloudit24.stillpoint.data.BuiltIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.gestures.scrollBy
import com.cloudit24.stillpoint.data.WidgetLook
import com.cloudit24.stillpoint.widget.LongPressHostView
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetsScreen(vm: LauncherViewModel, onAddWidget: (AppWidgetProviderInfo) -> Unit) {
    val density = LocalDensity.current
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val drag = remember { ShelfDrag() }
    val shelfAccent = Accent
    val shelfScroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    // While a widget is dragged near the top or bottom, the page scrolls by itself (faster closer to the edge).
    LaunchedEffect(drag.id) {
        if (drag.id == null) return@LaunchedEffect
        val edge = with(density) { 96.dp.toPx() }
        while (true) {
            val f = drag.finger
            val speed = when {
                f.y > viewport.bottom - edge -> ((f.y - (viewport.bottom - edge)) / edge).coerceAtMost(1f) * 22f
                f.y < viewport.top + edge -> -((viewport.top + edge - f.y) / edge).coerceAtMost(1f) * 22f
                else -> 0f
            }
            if (speed != 0f) {
                shelfScroll.scrollBy(speed)
                drag.id?.let { drag.trySwap(it, vm) }
            }
            withFrameMillis { }
        }
    }

    if (picking) {
        BackHandler { picking = false }
        WidgetPicker(vm, onBuiltIn = { picking = false; vm.addBuiltIn(it) }) { picking = false; onAddWidget(it) }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            // A soft light in the accent colour from the top corner.
            .drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(shelfAccent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.9f, 0f), radius = size.width * 1.1f,
                ))
            }
            .pointerInput(editing) {
                if (editing) return@pointerInput // Arranging: a drag must never leave the page.
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
            ShelfTabs(vm, Modifier.weight(1f))
            if (!editing && vm.settings.toolsOn) Text("Tools", color = Muted, modifier = Modifier.clickable {
                vm.toolsReturn = Screen.WIDGETS
                vm.screen = Screen.TOOLS
            }.padding(8.dp))
            if (vm.widgetIds.isNotEmpty()) {
                Text(if (editing) "Done" else "Edit", color = Muted,
                    modifier = Modifier.clickable { editing = !editing }.padding(8.dp))
            }
        }

        // The Shelf: things to keep near but off the home screen. Notes, tasks, projects, then widgets.
        Column(Modifier.weight(1f).padding(top = 16.dp).onGloballyPositioned { viewport = it.boundsInRoot() }
            .verticalScroll(shelfScroll, enabled = drag.id == null)) {
            if (editing) Text("Drag to move · corner to resize · − to remove", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp))
            if (vm.widgetIds.isEmpty()) {
                Text("Nothing on this shelf yet. Tap Add below for Stillpoint cards and widgets.", color = Muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            // Widgets flow like tiles: two half-width ones sit side by side.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                val areaW = maxWidth
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    vm.widgetIds.forEachIndexed { i, id ->
                        key(id) {
                            // The widget being moved floats above the others and follows the finger.
                            val moving = drag.id == id
                            Box(
                                Modifier.zIndex(if (moving) 1f else 0f)
                                    .onGloballyPositioned { drag.bounds[id] = it.boundsInRoot() }
                                    .animatePlacement(enabled = !moving)
                                    .graphicsLayer {
                                        val b = drag.bounds[id]
                                        if (moving && b != null) {
                                            translationX = drag.finger.x - drag.grab.x - b.left
                                            translationY = drag.finger.y - drag.grab.y - b.top
                                            scaleX = 1.04f
                                            scaleY = 1.04f
                                            shadowElevation = 24.dp.toPx()
                                            shape = RoundedCornerShape(24.dp)
                                        }
                                    },
                            ) {
                                AppearIn(i) {
                                    if (id < 0) BuiltInItem(vm, id, editing, areaW, drag) { editing = true }
                                    else WidgetItem(vm, id, editing, areaW, drag) { editing = true }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp)) {
            Text("Home", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.HOME }.padding(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { UndoBar(vm) }
            Text("Add", color = Muted, modifier = Modifier.clickable { editing = false; picking = true }.padding(8.dp))
        }
    }
}

@Composable
private fun WidgetItem(vm: LauncherViewModel, id: Int, editing: Boolean, areaW: Dp, drag: ShelfDrag, onArrange: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val info = remember(id) { vm.widgetManager.getAppWidgetInfo(id) }
    val natural = remember(info) { info?.let { naturalSize(it, density.density) } }
    val saved = vm.widgetSizes[id]
    val heightDp = saved?.first ?: natural?.height ?: 80
    // Width: 0 = full, -1 = the widget's own width, more = chosen by dragging the corner.
    val widthSetting = saved?.second ?: if (natural?.fullWidth != false) 0 else -1
    val targetW = when {
        natural == null || widthSetting == 0 -> areaW
        widthSetting < 0 -> minOf(areaW, natural.width.dp)
        else -> minOf(areaW, widthSetting.dp)
    }
    // Two half-width widgets fit side by side with the gap between them.
    val half = (areaW - 12.dp) / 2

    // While the corner is dragged the widget follows the finger; otherwise size changes glide.
    var dragW by remember { mutableStateOf<Dp?>(null) }
    var dragH by remember { mutableStateOf<Dp?>(null) }
    val resizing = dragW != null
    val w by animateDpAsState(dragW ?: targetW, if (resizing) snap<Dp>() else spring<Dp>(stiffness = Spring.StiffnessMediumLow), label = "w")
    val h by animateDpAsState(dragH ?: heightDp.dp, if (resizing) snap<Dp>() else spring<Dp>(stiffness = Spring.StiffnessMediumLow), label = "h")
    val curW by rememberUpdatedState(w)
    val curH by rememberUpdatedState(h)
    val look = vm.widgetLooks[id] ?: WidgetLook()
    val accent = Accent
    val latestArrange by rememberUpdatedState(onArrange)
    var confirmRemove by remember { mutableStateOf(false) }
    val label = info?.loadLabel(context.packageManager) ?: "Widget no longer available"

    Column(Modifier.width(w).padding(vertical = 6.dp)) {
        if (editing || info == null) {
            Text(label, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        if (info == null || natural == null) {
            Text("Remove", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { vm.removeWidget(id) }.padding(6.dp))
            return@Column
        }
        var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
        Box(Modifier.fillMaxWidth().height(h).widgetLook(look, accent)) {
            AndroidView(
                factory = { ctx ->
                    vm.widgetHost.createView(ctx, id, info).also { v ->
                        hostView = v
                        (v as? LongPressHostView)?.onLongPress = { latestArrange() }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (look.style == 1) 6.dp else 0.dp)
                    .clip(RoundedCornerShape(cornerFor(look)))
                    // Lets lists inside widgets (mail, calendar, notes) scroll instead of the page.
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .onSizeChanged { size -> hostView?.let { reportSize(it, size.width / density.density, size.height / density.density) } },
            )
            if (editing) {
                Box(Modifier.matchParentSize().border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(cornerFor(look) + 4.dp)))
                EditOverlay(id, drag, vm) { confirmRemove = true }
                // Resize, bottom-right. Snaps to full width and to half (two side by side).
                Box(
                    Modifier.align(Alignment.BottomEnd).size(40.dp).pointerInput(id, areaW) {
                        detectDragGestures(
                            onDragStart = { dragW = curW; dragH = curH },
                            onDragCancel = { dragW = null; dragH = null },
                            onDragEnd = {
                                val fw = dragW ?: curW
                                val fh = dragH ?: curH
                                val hh = ((fh.value / 4).roundToInt() * 4).coerceIn(natural.minHeight, natural.maxHeight)
                                val ww = when {
                                    fw >= areaW - 16.dp -> 0
                                    kotlin.math.abs((fw - half).value) < 24f -> half.value.toInt()
                                    else -> (fw.value / 4).roundToInt() * 4
                                }
                                vm.setWidgetSize(id, hh, ww)
                                dragW = null
                                dragH = null
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragW = ((dragW ?: curW) + amount.x.toDp()).coerceIn(120.dp, areaW)
                                dragH = ((dragH ?: curH) + amount.y.toDp()).coerceIn(natural.minHeight.dp, natural.maxHeight.dp)
                            },
                        )
                    },
                ) {
                    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                        val s = size.width
                        drawArc(accent, 0f, 90f, useCenter = false, topLeft = Offset(-s, -s), size = Size(s * 2, s * 2),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
        if (editing) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("−", fontSize = 20.sp, modifier = Modifier.clip(CircleShape)
                    .clickable { vm.setWidgetSize(id, (heightDp - 20).coerceAtLeast(natural.minHeight), widthSetting) }
                    .padding(horizontal = 10.dp))
                Text("$heightDp", fontSize = 13.sp, color = Muted)
                Text("+", fontSize = 20.sp, modifier = Modifier.clip(CircleShape)
                    .clickable { vm.setWidgetSize(id, (heightDp + 20).coerceAtMost(natural.maxHeight), widthSetting) }
                    .padding(horizontal = 10.dp))
                Spacer(Modifier.weight(1f))
                LookChip(if (widthSetting == 0) "Full" else "Own") {
                    vm.setWidgetSize(id, heightDp, if (widthSetting == 0) -1 else 0)
                }
            }
            // Its look: each chip moves to the next choice.
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LookChip(listOf("Plain", "Glass", "Glow")[look.style]) { vm.setWidgetLook(id, look.copy(style = (look.style + 1) % 3)) }
                LookChip(listOf("Square", "Soft", "Round")[look.corners]) { vm.setWidgetLook(id, look.copy(corners = (look.corners + 1) % 3)) }
                LookChip("${look.alpha}%") {
                    vm.setWidgetLook(id, look.copy(alpha = when (look.alpha) { 100 -> 80; 80 -> 60; else -> 100 }))
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeWidget(id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
            title = { Text("Remove $label?") },
        )
    }
}

/** A Stillpoint card on the Shelf (notes, tasks, calendar...). Full width or half; arranged like widgets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BuiltInItem(vm: LauncherViewModel, id: Int, editing: Boolean, areaW: Dp, drag: ShelfDrag, onArrange: () -> Unit) {
    val kind = BuiltIn.of(id)
    if (kind == null) {
        Text("Remove", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { vm.removeWidget(id) }.padding(6.dp))
        return
    }
    val half = (areaW - 12.dp) / 2
    val isHalf = kind.canHalf && (vm.widgetSizes[id]?.second ?: 0) > 0
    val w by animateDpAsState(if (isHalf) half else areaW, spring<Dp>(stiffness = Spring.StiffnessMediumLow), label = "bw")
    val look = vm.widgetLooks[id] ?: WidgetLook()
    val accent = Accent
    var confirmRemove by remember { mutableStateOf(false) }
    Column(Modifier.width(w).padding(vertical = 6.dp)) {
        Box(
            Modifier.fillMaxWidth().widgetLook(look, accent)
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = {}, onLongClick = onArrange),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (look.style == 1) 14.dp else 4.dp,
                vertical = if (look.style == 1) 12.dp else 2.dp)) {
                BuiltInContent(vm, kind, id)
            }
            if (editing) {
                Box(Modifier.matchParentSize().border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(cornerFor(look) + 4.dp)))
                EditOverlay(id, drag, vm) { confirmRemove = true }
            }
        }
        if (editing) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (kind.canHalf) LookChip(if (isHalf) "Half" else "Full") { vm.setWidgetSize(id, 0, if (isHalf) 0 else 1) }
                LookChip(listOf("Plain", "Glass", "Glow")[look.style]) { vm.setWidgetLook(id, look.copy(style = (look.style + 1) % 3)) }
                LookChip(listOf("Square", "Soft", "Round")[look.corners]) { vm.setWidgetLook(id, look.copy(corners = (look.corners + 1) % 3)) }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeWidget(id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
            title = { Text("Remove ${kind.title} from this shelf?") },
            text = { Text("What's in it stays; add it again any time.") },
        )
    }
}

/** While arranging: the whole item is the handle to move it, a grab bar on top, and remove at the top-left. */
@Composable
private fun BoxScope.EditOverlay(id: Int, drag: ShelfDrag, vm: LauncherViewModel, onRemove: () -> Unit) {
    val accent = Accent
    var areaAt by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { areaAt = it.boundsInRoot().topLeft }
            .pointerInput(id) {
                detectDragGestures(
                    onDragStart = { at ->
                        val b = drag.bounds[id]
                        if (b != null) {
                            drag.finger = areaAt + at
                            drag.grab = drag.finger - b.topLeft
                            drag.lastSwap = null
                            drag.id = id
                        }
                    },
                    onDragEnd = { drag.id = null },
                    onDragCancel = { drag.id = null },
                    onDrag = { change, amount ->
                        change.consume()
                        if (drag.id == id) {
                            drag.finger += amount
                            drag.trySwap(id, vm)
                        }
                    },
                )
            },
    )
    Box(
        Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(34.dp).height(5.dp)
            .clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.9f)),
    )
    Box(
        Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).clip(CircleShape)
            .background(Color(0xE6262624)).border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) { Text("−", color = Ink, fontSize = 17.sp) }
}

/** Shelf tabs: tap one to switch, long-press to rename or delete it, + for a new shelf. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfTabs(vm: LauncherViewModel, modifier: Modifier) {
    var renaming by remember { mutableStateOf<Int?>(null) }
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
        vm.shelfPages.forEachIndexed { i, p ->
            val on = i == vm.shelfIndex
            val size by animateFloatAsState(if (on) 34f else 20f, tween(250), label = "tab")
            Text(p.name, fontSize = size.sp, fontWeight = FontWeight.Light, color = if (on) Ink else Muted, maxLines = 1,
                modifier = Modifier.padding(end = 18.dp).combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { vm.selectShelf(i) }, onLongClick = { renaming = i }))
        }
        if (vm.shelfPages.size < 9) {
            Text("+", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Muted,
                modifier = Modifier.clip(CircleShape).clickable { vm.addShelf() }.padding(horizontal = 8.dp))
        }
    }
    renaming?.let { i ->
        val page = vm.shelfPages.getOrNull(i) ?: return@let
        var name by remember(i) { mutableStateOf(page.name) }
        var sure by remember(i) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = { TextButton(onClick = { vm.renameShelf(i, name); renaming = null }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            title = { Text("Shelf") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, singleLine = true, label = { Text("Name") })
                    if (vm.shelfPages.size > 1) {
                        Text(if (sure) "Tap again to delete it and its widgets" else "Delete this shelf",
                            color = Color(0xFFE08A78), fontSize = 14.sp,
                            modifier = Modifier.padding(top = 14.dp).clickable {
                                if (sure) { vm.deleteShelf(i); renaming = null } else sure = true
                            }.padding(vertical = 6.dp))
                    }
                }
            },
        )
    }
}

/** Other widgets glide to their new place when one is moved, instead of jumping there. */
private fun Modifier.animatePlacement(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this.onPlaced { target = it.positionInParent().round() }
        .offset {
            val a = anim ?: Animatable(target, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != target) {
                scope.launch {
                    if (enabled) a.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) else a.snapTo(target)
                }
            }
            if (enabled) a.value - target else IntOffset.Zero
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

/** Notes: a scribble board. Type and press done to keep a line; tap one to change it; long-press to let it go (with Undo). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesBlock(vm: LauncherViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    val accent = Accent
    Text("Notes", color = Muted, fontSize = 13.sp)
    vm.notes.forEach { n ->
        key(n.id) {
            if (editingId == n.id) {
                BasicTextField(
                    value = editText, onValueChange = { editText = it },
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.updateNote(n.id, editText); editingId = null }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            } else {
                Text(n.text, fontSize = 16.sp, color = Ink, modifier = Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { editingId = n.id; editText = n.text }, onLongClick = { vm.deleteNote(n.id) })
                    .padding(vertical = 6.dp))
            }
        }
    }
    BasicTextField(
        value = input, onValueChange = { input = it }, singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.addNote(input); input = "" }),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (input.isEmpty()) Text("Write a note", color = Muted, fontSize = 16.sp)
                inner()
            }
        },
    )
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
private fun WidgetPicker(vm: LauncherViewModel, onBuiltIn: (BuiltIn) -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
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
                Text("Add to shelf", fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("${groups.size} apps", color = Muted, fontSize = 13.sp)
            }
        }
        val shelfId = vm.shelfPages.getOrNull(vm.shelfIndex)?.id ?: 0
        val cards = BuiltIn.entries.filter { it.id(shelfId) !in vm.widgetIds }
        if (cards.isNotEmpty()) {
            item { Text("Stillpoint cards", color = Accent, fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            items(cards, key = { "card_${it.name}" }) { b ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onBuiltIn(b) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(b.title, fontSize = 15.sp)
                    Text(b.summary, color = Muted, fontSize = 12.sp)
                }
            }
            item { Text("Widgets from your apps", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) }
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

/** Drag-to-move on the Shelf: where every widget sits, which one is moving, and where the finger is. */
private class ShelfDrag {
    val bounds = mutableStateMapOf<Int, Rect>()
    var id by mutableStateOf<Int?>(null)
    var finger by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var lastSwap: Int? = null

    /**
     * Takes another widget's place once the finger is well inside it (not just its edge),
     * so two widgets don't flip back and forth at the border.
     */
    fun trySwap(id: Int, vm: LauncherViewModel) {
        val over = bounds.entries.firstOrNull { (k, r) ->
            k != id && k in vm.widgetIds && r.deflate(minOf(r.width, r.height) * 0.2f).contains(finger)
        }?.key
        if (over == null) {
            lastSwap = null
            return
        }
        if (over == lastSwap) return
        vm.moveWidgetTo(id, vm.widgetIds.indexOf(over))
        lastSwap = over
    }
}

private fun cornerFor(look: WidgetLook): Dp = when (look.corners) {
    0 -> 0.dp
    2 -> 28.dp
    else -> 16.dp
}

/**
 * Glass: a frosted tile with a hairline edge around the widget. Glow: soft accent light behind it.
 * Opacity fades the whole widget.
 */
private fun Modifier.widgetLook(look: WidgetLook, accent: Color): Modifier {
    val shape = RoundedCornerShape(cornerFor(look) + 6.dp)
    val glow = if (look.style != 2) Modifier else Modifier.drawBehind {
        val pad = 22.dp.toPx()
        drawRoundRect(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent), center, size.maxDimension * 0.8f),
            topLeft = Offset(-pad, -pad), size = Size(size.width + pad * 2, size.height + pad * 2),
            cornerRadius = CornerRadius(40.dp.toPx()),
        )
    }
    val glass = if (look.style != 1) Modifier
    else Modifier.clip(shape).background(Color.White.copy(alpha = 0.06f)).border(0.5.dp, Color.White.copy(alpha = 0.14f), shape)
    return this.then(glow).graphicsLayer { alpha = look.alpha / 100f }.then(glass)
}

@Composable
private fun LookChip(label: String, onClick: () -> Unit) {
    Text(label, color = Accent, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp))
}
