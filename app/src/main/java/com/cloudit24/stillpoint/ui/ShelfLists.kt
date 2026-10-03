package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications as BellOn
import androidx.compose.material.icons.outlined.Notifications as BellOff
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalTime
import java.time.format.FormatStyle
import com.cloudit24.stillpoint.widget.LockNotification
import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.TaskItem
import com.cloudit24.stillpoint.data.TaskSource
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Overdue = Color(0xFFE08A78)

/** A due day in words: today, tomorrow, the weekday this week, else the date. Second is true when it's late. */
internal fun dueLabel(epochDay: Long, today: LocalDate = LocalDate.now()): Pair<String, Boolean>? {
    if (epochDay < 0) return null
    val diff = epochDay - today.toEpochDay()
    val d = LocalDate.ofEpochDay(epochDay)
    return when {
        diff < 0 -> "overdue" to true
        diff == 0L -> "today" to false
        diff == 1L -> "tomorrow" to false
        diff < 7 -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) to false
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) to false
    }
}

/** Open tasks first by due day (no date last), then in the order they were added. */
internal fun sortTasks(list: List<TaskItem>): List<TaskItem> =
    list.sortedWith(compareBy({ if (it.due < 0) Long.MAX_VALUE else it.due }, { it.id }))

/** The round tick, like Reminders: an empty ring that fills with the accent and a check. */
@Composable
fun TickCircle(done: Boolean, onClick: () -> Unit) {
    val accent = Accent
    val fill by animateFloatAsState(if (done) 1f else 0f, tween(220), label = "tick")
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp)) {
            val r = size.minDimension / 2
            drawCircle(lerp(Muted, accent, fill), r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            if (fill > 0f) {
                drawCircle(accent.copy(alpha = fill), r * fill)
                val p = Path().apply {
                    moveTo(r * 0.55f, r * 1.02f)
                    lineTo(r * 0.88f, r * 1.36f)
                    lineTo(r * 1.48f, r * 0.68f)
                }
                drawPath(p, Color(0xFF0B0B0A).copy(alpha = fill),
                    style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Swipe left to delete. Only one direction, so a swipe right still goes back home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val shown = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(Modifier.fillMaxSize().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                if (shown) Text(stringResource(R.string.s_delete), color = Overdue, fontSize = 14.sp)
            }
        },
    ) { content() }
}

@Composable
private fun BlockHeader(title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (count.isNotEmpty()) Text(count, color = Muted, fontSize = 13.sp)
    }
}

/** A thin line between tasks, starting under the words. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(start = 40.dp).height(0.5.dp).background(Color(0xFF232321)))
}

@Composable
private fun DueChip(label: String, late: Boolean) {
    Text(label, color = if (late) Overdue else Muted, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** One task: tick, text, due day. Ticking fills the circle, then the task folds away. */
@Composable
private fun TaskRow(
    text: String, done: Boolean, due: Pair<String, Boolean>?, onTick: () -> Unit, onOpen: (() -> Unit)?,
    bell: (@Composable () -> Unit)? = null,
) {
    var ticking by remember { mutableStateOf(false) }
    LaunchedEffect(ticking) {
        if (ticking) {
            delay(420)
            onTick()
            ticking = false
        }
    }
    Row(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        TickCircle(done || ticking) { if (done) onTick() else ticking = true }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Text(text, fontSize = 16.sp, color = if (done) Muted else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null)
            // The day (and alert time) under the words: late in red, today in the accent.
            if (!done && due != null) Text(due.first.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, maxLines = 1,
                color = when { due.second -> Overdue; due.first.startsWith("today") -> Accent; else -> Muted })
        }
        if (!done) bell?.invoke()
    }
}

/**
 * Tasks on the Shelf: round ticks, a due day when you want one, done ones folded away.
 * Swipe a task left to delete it (Undo appears at the bottom). Where tasks come from is chosen in Settings, Tasks.
 */
@Composable
fun TasksBlock(vm: LauncherViewModel) {
    val src = vm.settings.tasksSource
    val hub = src == TaskSource.HUB && vm.hubConnected()
    val provider = src.authority != null
    LaunchedEffect(src) { if (provider) vm.loadProviderTasks() }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var afterAllow by remember { mutableStateOf<(() -> Unit)?>(null) }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val f = afterAllow
        afterAllow = null
        if (ok) f?.invoke() else vm.blockedMessage = "Allow notifications for Stillpoint to get task alerts."
    }
    // Picks the alert time; asks for notifications first if Android needs that.
    val pickAlert: (Int, (Int) -> Unit) -> Unit = { start, then ->
        val show = {
            TimePickerDialog(ctx, { _, hh, mm -> then(hh * 60 + mm) }, start / 60, start % 60, DateFormat.is24HourFormat(ctx)).show()
        }
        if (LockNotification.canPost(ctx)) show() else {
            afterAllow = show
            allow.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.animateContentSize()) {
        when {
            src == TaskSource.HUB && !hub -> {
                BlockHeader(stringResource(R.string.s_tasks_project_hub), "")
                Text(stringResource(R.string.s_connect_project_hub_in_settings_extras), color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            hub -> {
                val list = vm.hub?.tasks.orEmpty()
                BlockHeader(stringResource(R.string.s_tasks_project_hub), list.count { !it.done }.let { if (it > 0) "$it left" else "" })
                if (list.isEmpty()) Text(stringResource(R.string.s_nothing_due_today), color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                list.forEach { t ->
                    key(t.id) {
                        val due = t.due?.let { d -> runCatching { LocalDate.parse(d.take(10)).toEpochDay() }.getOrNull() }
                        TaskRow(t.text, t.done, due?.let { dueLabel(it) }, onTick = { vm.hubDone(t.id, !t.done) }, onOpen = null)
                    }
                }
            }
            provider -> {
                BlockHeader("Tasks · ${src.label}", vm.providerTasks.size.let { if (it > 0) "$it left" else "" })
                vm.providerError?.let {
                    Text(it, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp).clickable { vm.loadProviderTasks() })
                }
                if (vm.providerError == null && vm.providerTasks.isEmpty()) {
                    Text(stringResource(R.string.s_no_open_tasks), color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                vm.providerTasks.forEach { t ->
                    key(t.id) { TaskRow(t.text, false, null, onTick = { vm.providerDone(t.id) }, onOpen = null) }
                }
            }
            else -> {
                val open = sortTasks(vm.tasks.filter { !it.done })
                val done = vm.tasks.filter { it.done }
                BlockHeader(stringResource(R.string.s_tasks), if (open.isNotEmpty()) "${open.size} left" else if (done.isNotEmpty()) "All done" else "")
                open.forEachIndexed { i, t ->
                    key(t.id) {
                        if (i > 0) Hairline()
                        SwipeToDelete({ vm.deleteTask(t.id) }) {
                            val due = dueLabel(t.due)?.let { (l, late) -> (if (t.remind >= 0) "$l · ${remindLabel(t.remind)}" else l) to late }
                            TaskRow(t.text, false, due, onTick = { vm.toggleTask(t.id) }, onOpen = { editing = t },
                                bell = {
                                    BellButton(t.remind >= 0) {
                                        if (t.remind >= 0) vm.setTaskRemind(t.id, -1) else pickAlert(9 * 60) { vm.setTaskRemind(t.id, it) }
                                    }
                                })
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    // Done tasks stay below, crossed out, until cleared. The latest three, or all of them.
                    if (open.isNotEmpty()) Hairline()
                    done.asReversed().let { if (showDone) it else it.take(3) }.forEach { t ->
                        key(t.id) {
                            SwipeToDelete({ vm.deleteTask(t.id) }) {
                                TaskRow(t.text, true, null, onTick = { vm.toggleTask(t.id) }, onOpen = null)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (done.size > 3) Text(if (showDone) "Show fewer" else "Show all ${done.size} done", color = Muted, fontSize = 13.sp,
                            modifier = Modifier.clickable { showDone = !showDone }.padding(vertical = 8.dp))
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.s_clear_done), color = Accent, fontSize = 13.sp,
                            modifier = Modifier.clickable { vm.clearDoneTasks() }.padding(8.dp))
                    }
                }
            }
        }
        if (src != TaskSource.HUB || hub) AddLine(stringResource(R.string.s_add_a_task), withDue = !hub && !provider, pickAlert) { text, due, remind ->
            when {
                hub -> vm.hubAdd(text)
                provider -> vm.providerAdd(text)
                else -> vm.addTask(text, due, remind)
            }
        }
    }
    editing?.let { t ->
        TaskDialog(t, pickAlert, onDismiss = { editing = null }) { text, due, remind ->
            vm.updateTask(t.id, text, due, remind)
            editing = null
        }
    }
}

/** An alert time in the phone's style: 9:00 or 9:00 AM. */
internal fun remindLabel(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** The bell: lit, the task rings on its day; off, no alert. */
@Composable
private fun BellButton(on: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) Accent else Muted.copy(alpha = 0.5f), tween(200), label = "bell")
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (on) Icons.Filled.BellOn else Icons.Outlined.BellOff, contentDescription = if (on) "Alert on" else "Alert off",
            tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** The input: one rounded bar. With [withDue], a day and a bell sit beside the words; the round button adds. */
@Composable
private fun AddLine(hint: String, withDue: Boolean, pickAlert: (Int, (Int) -> Unit) -> Unit, onAdd: (String, Long, Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableLongStateOf(-1L) }
    var remind by rememberSaveable { mutableIntStateOf(-1) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val accent = Accent
    val today = LocalDate.now().toEpochDay()
    val ready = input.isNotBlank()
    val submit = {
        if (input.isNotBlank()) onAdd(input, due, remind)
        input = ""
        due = -1L
        remind = -1
        focus.clearFocus()
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1A1A18))
            .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp)
                    inner()
                }
            },
        )
        if (withDue) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF262624)).clickable { menu = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DateRange, contentDescription = "Day", tint = if (due >= 0) accent else Muted, modifier = Modifier.size(15.dp))
                    dueLabel(due)?.let { Text(it.first, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp)) }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.s_no_day)) }, onClick = { due = -1L; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.s_today)) }, onClick = { due = today; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.s_tomorrow)) }, onClick = { due = today + 1; menu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.s_pick_a_day)) }, onClick = {
                        menu = false
                        val d = LocalDate.now()
                        DatePickerDialog(context, { _, y, mo, dd -> due = LocalDate.of(y, mo + 1, dd).toEpochDay() },
                            d.year, d.monthValue - 1, d.dayOfMonth).show()
                    })
                }
            }
            BellButton(remind >= 0) { if (remind >= 0) remind = -1 else pickAlert(9 * 60) { remind = it } }
        }
        Box(
            Modifier.padding(start = 4.dp).size(34.dp).clip(CircleShape).background(if (ready) accent else Color(0xFF262624))
                .clickable(enabled = ready) { submit() },
            contentAlignment = Alignment.Center,
        ) { Text("↑", color = if (ready) Color(0xFF0B0B0A) else Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun DuePick(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, color = if (on) Color(0xFF0B0B0A) else Accent, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Accent else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp))
}

/** Change a task's words or its day. */
@Composable
private fun TaskDialog(initial: TaskItem, pickAlert: (Int, (Int) -> Unit) -> Unit, onDismiss: () -> Unit, onSave: (String, Long, Int) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial.text) }
    var due by remember { mutableLongStateOf(initial.due) }
    var remind by remember { mutableIntStateOf(initial.remind) }
    val today = LocalDate.now().toEpochDay()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text, due, remind) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.s_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_task)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.s_task)) })
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuePick(stringResource(R.string.s_no_date), due < 0) { due = -1L }
                    DuePick(stringResource(R.string.s_today), due == today) { due = today }
                    DuePick(stringResource(R.string.s_tomorrow), due == today + 1) { due = today + 1 }
                }
                val picked = due > today + 1
                Row(Modifier.padding(top = 8.dp)) {
                    DuePick(if (picked) dueLabel(due)?.first ?: "Pick a day" else "Pick a day", picked) {
                        val d = if (due >= 0) LocalDate.ofEpochDay(due) else LocalDate.now()
                        DatePickerDialog(context, { _, y, m, dd -> due = LocalDate.of(y, m + 1, dd).toEpochDay() },
                            d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuePick(if (remind >= 0) "Alert at ${remindLabel(remind)}" else "No alert", remind >= 0) {
                        pickAlert(if (remind >= 0) remind else 9 * 60) { remind = it }
                    }
                    if (remind >= 0) DuePick(stringResource(R.string.s_turn_off), false) { remind = -1 }
                }
            }
        },
    )
}

/** "Task deleted · Undo", for a few seconds after something is deleted on the Shelf. */
@Composable
fun UndoBar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val u = vm.undo ?: return
    Row(modifier.clip(RoundedCornerShape(50)).background(Color(0xFF262624)).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(u.label, color = Ink, fontSize = 14.sp)
        Text(stringResource(R.string.s_undo), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
            .clickable { vm.runUndo() }.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)
