package com.cloudit24.stillpoint.ui

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
import com.cloudit24.stillpoint.data.LocalProject
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
                if (shown) Text("Delete", color = Overdue, fontSize = 14.sp)
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

@Composable
private fun DueChip(label: String, late: Boolean) {
    Text(label, color = if (late) Overdue else Muted, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** One task: tick, text, due day. Ticking fills the circle, then the task folds away. */
@Composable
private fun TaskRow(text: String, done: Boolean, due: Pair<String, Boolean>?, onTick: () -> Unit, onOpen: (() -> Unit)?) {
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
        Text(text, fontSize = 16.sp, color = if (done) Muted else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 6.dp, bottom = 6.dp))
        if (!done && due != null) DueChip(due.first, due.second)
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

    Column(Modifier.animateContentSize()) {
        when {
            src == TaskSource.HUB && !hub -> {
                BlockHeader("Tasks · Project Hub", "")
                Text("Connect Project Hub in Settings, Extras.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            hub -> {
                val list = vm.hub?.tasks.orEmpty()
                BlockHeader("Tasks · Project Hub", list.count { !it.done }.let { if (it > 0) "$it left" else "" })
                if (list.isEmpty()) Text("Nothing due today.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
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
                    Text("No open tasks.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
                vm.providerTasks.forEach { t ->
                    key(t.id) { TaskRow(t.text, false, null, onTick = { vm.providerDone(t.id) }, onOpen = null) }
                }
            }
            else -> {
                val open = sortTasks(vm.tasks.filter { !it.done })
                val done = vm.tasks.filter { it.done }
                BlockHeader("Tasks", if (open.isNotEmpty()) "${open.size} left" else if (done.isNotEmpty()) "All done" else "")
                open.forEach { t ->
                    key(t.id) {
                        SwipeToDelete({ vm.deleteTask(t.id) }) {
                            TaskRow(t.text, false, dueLabel(t.due), onTick = { vm.toggleTask(t.id) }, onOpen = { editing = t })
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (showDone) "▾ " else "▸ ") + "${done.size} done", color = Muted, fontSize = 13.sp,
                            modifier = Modifier.clickable { showDone = !showDone }.padding(vertical = 8.dp))
                        Spacer(Modifier.weight(1f))
                        if (showDone) Text("Clear", color = Accent, fontSize = 13.sp,
                            modifier = Modifier.clickable { vm.clearDoneTasks() }.padding(8.dp))
                    }
                    if (showDone) done.forEach { t ->
                        key(t.id) {
                            SwipeToDelete({ vm.deleteTask(t.id) }) {
                                TaskRow(t.text, true, null, onTick = { vm.toggleTask(t.id) }, onOpen = null)
                            }
                        }
                    }
                }
            }
        }
        if (src != TaskSource.HUB || hub) AddLine("Add a task", withDue = !hub && !provider) { text, due ->
            when {
                hub -> vm.hubAdd(text)
                provider -> vm.providerAdd(text)
                else -> vm.addTask(text, due)
            }
        }
    }
    editing?.let { t ->
        TaskDialog(t, onDismiss = { editing = null }) { text, due ->
            vm.updateTask(t.id, text, due)
            editing = null
        }
    }
}

/** "+ Add a task": type and press done. With [withDue], Today and Tomorrow appear while typing. */
@Composable
private fun AddLine(hint: String, withDue: Boolean, onAdd: (String, Long) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableLongStateOf(-1L) }
    val focus = LocalFocusManager.current
    val today = LocalDate.now().toEpochDay()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("+", color = Muted, fontSize = 20.sp) }
        BasicTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp), cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (input.isNotBlank()) onAdd(input, due)
                input = ""
                due = -1L
                focus.clearFocus()
            }),
            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp)
                    inner()
                }
            },
        )
    }
    if (withDue && input.isNotEmpty()) {
        Row(Modifier.padding(start = 40.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuePick("Today", due == today) { due = if (due == today) -1L else today }
            DuePick("Tomorrow", due == today + 1) { due = if (due == today + 1) -1L else today + 1 }
        }
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
private fun TaskDialog(initial: TaskItem, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial.text) }
    var due by remember { mutableLongStateOf(initial.due) }
    val today = LocalDate.now().toEpochDay()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text, due) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Task") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Task") })
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuePick("No date", due < 0) { due = -1L }
                    DuePick("Today", due == today) { due = today }
                    DuePick("Tomorrow", due == today + 1) { due = today + 1 }
                }
                val picked = due > today + 1
                Row(Modifier.padding(top = 8.dp)) {
                    DuePick(if (picked) dueLabel(due)?.first ?: "Pick a day" else "Pick a day", picked) {
                        val d = if (due >= 0) LocalDate.ofEpochDay(due) else LocalDate.now()
                        DatePickerDialog(context, { _, y, m, dd -> due = LocalDate.of(y, m + 1, dd).toEpochDay() },
                            d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                }
            }
        },
    )
}

/**
 * Projects kept on the phone. Each has steps: the first is the next one. Tick it and the following step moves up;
 * the bar shows how far along the project is. Tap to edit, swipe left to delete.
 */
@Composable
fun ProjectsBlock(vm: LauncherViewModel) {
    var editing by remember { mutableStateOf<LocalProject?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        BlockHeader("Projects", if (vm.projects.isEmpty()) "" else "${vm.projects.size}")
        vm.projects.forEach { p ->
            key(p.id) {
                SwipeToDelete({ vm.deleteProject(p.id) }) { ProjectRow(vm, p) { editing = p } }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { adding = true }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("+", color = Muted, fontSize = 20.sp) }
            Text("New project", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
        }
    }
    val e = editing
    if (adding || e != null) {
        ProjectDialog(e, onDismiss = { adding = false; editing = null }) { name, steps, resetDone ->
            if (e != null) vm.updateProject(e.copy(name = name, next = steps.firstOrNull() ?: "", steps = steps.drop(1),
                done = if (resetDone) 0 else e.done))
            else vm.addProject(name, steps)
            adding = false
            editing = null
        }
    }
}

@Composable
private fun ProjectRow(vm: LauncherViewModel, p: LocalProject, onOpen: () -> Unit) {
    var ticking by remember(p.next) { mutableStateOf(false) }
    LaunchedEffect(ticking) {
        if (ticking) {
            delay(420)
            vm.projectStepDone(p.id)
        }
    }
    val total = p.done + (if (p.next.isNotBlank()) 1 else 0) + p.steps.size
    val frac by animateFloatAsState(if (total == 0) 0f else p.done / total.toFloat(), tween(500), label = "progress")
    val accent = Accent
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp)) {
        if (p.next.isNotBlank()) TickCircle(ticking) { ticking = true }
        else Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Muted.copy(alpha = 0.5f)))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (total > 0) Text("${p.done} of $total", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Text(if (p.next.isBlank()) "Add the next step" else p.next, color = Muted, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            if (total > 0) {
                Box(Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
                }
            } else Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ProjectDialog(initial: LocalProject?, onDismiss: () -> Unit, onSave: (String, List<String>, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var steps by remember {
        mutableStateOf((listOfNotNull(initial?.next?.takeIf { it.isNotBlank() }) + initial?.steps.orEmpty()).joinToString("\n"))
    }
    var reset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), steps.lines().map { it.trim() }.filter { it.isNotEmpty() }, reset) },
                enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (initial == null) "New project" else "Project") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                OutlinedTextField(value = steps, onValueChange = { steps = it }, minLines = 3, maxLines = 8,
                    label = { Text("Steps, one per line") }, modifier = Modifier.padding(top = 8.dp))
                Text("The first line is the next step.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                if (initial != null && initial.done > 0) {
                    Text(if (reset) "Done count will start again" else "${initial.done} done so far · start again",
                        color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clickable { reset = !reset })
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
        Text("Undo", color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
            .clickable { vm.runUndo() }.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)
