package com.cloudit24.stillpoint.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.Note
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sticky-note colours, soft enough for the black theme: paper and ink. */
internal val NoteColors = listOf(
    Color(0xFF3A3320) to Color(0xFFF5E6B8), // amber
    Color(0xFF1F3330) to Color(0xFFC4EBE0), // teal
    Color(0xFF36232B) to Color(0xFFF2C8D6), // rose
    Color(0xFF2A2740) to Color(0xFFD6D0F5), // violet
    Color(0xFF1E2C3D) to Color(0xFFC6DCF5), // blue
    Color(0xFF2A2A27) to Color(0xFFE8E6E1), // stone
)

private fun noteColor(i: Int) = NoteColors[i.mod(NoteColors.size)]

/** How old a note is, in a word: now, 5m, 3h, Mon, 12 Sep. */
internal fun noteAge(id: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - id) / 60_000
    val at = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault())
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 24 * 60 -> "${mins / 60}h"
        mins < 7 * 24 * 60 -> at.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> at.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

/**
 * Notes as sticky notes, newest first. Tap one to open it (change the words or the colour, or delete it).
 * Hold one and an × appears to throw it away; Undo brings it back.
 */
@Composable
fun NotesBlock(vm: LauncherViewModel) {
    var open by remember { mutableStateOf<Note?>(null) }
    var adding by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Long?>(null) }
    val list = vm.notes.asReversed()
    Column(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notes", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (picked != null) Text("Done", color = Accent, fontSize = 13.sp,
                modifier = Modifier.clickable { picked = null }.padding(horizontal = 8.dp, vertical = 4.dp))
            else if (list.isNotEmpty()) Text("${list.size}", color = Muted, fontSize = 13.sp)
        }
        (list + listOf<Note?>(null)).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n ->
                    key(n?.id ?: -1L) {
                        Box(Modifier.weight(1f)) {
                            if (n == null) AddSticky { picked = null; adding = true }
                            else Sticky(
                                n, picked == n.id,
                                onOpen = { if (picked != null) picked = null else open = n },
                                onPick = { picked = n.id },
                                onDelete = { picked = null; vm.deleteNote(n.id) },
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (list.size in 1..3 && picked == null) Text("Tap to open · hold to throw away", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp))
    }
    val e = open
    if (adding || e != null) {
        NoteEditor(e, startColor = vm.notes.size,
            onDelete = e?.let { { open = null; vm.deleteNote(it.id) } }) { text, color ->
            when {
                e == null -> vm.addNote(text, color)
                text.isBlank() -> vm.deleteNote(e.id)
                else -> vm.updateNote(e.id, text, color)
            }
            open = null
            adding = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Sticky(n: Note, picked: Boolean, onOpen: () -> Unit, onPick: () -> Unit, onDelete: () -> Unit) {
    val (paper, ink) = noteColor(n.color)
    val scale by animateFloatAsState(if (picked) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "pick")
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(shape).background(paper)
                .then(if (picked) Modifier.border(1.5.dp, ink.copy(alpha = 0.7f), shape) else Modifier)
                .combinedClickable(onClick = onOpen, onLongClick = onPick)
                .padding(12.dp),
        ) {
            Text(n.text, color = ink, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
            Text(noteAge(n.id), color = ink.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
        }
        if (picked) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp).clip(CircleShape)
                    .background(Color(0xFF262624)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = Ink, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun AddSticky(onClick: () -> Unit) {
    val line = Muted.copy(alpha = 0.5f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("+", color = Muted, fontSize = 24.sp) }
}

/** One note, full size, on its own colour. Done keeps it; an empty note is thrown away. */
@Composable
private fun NoteEditor(initial: Note?, startColor: Int, onDelete: (() -> Unit)?, onSave: (String, Int) -> Unit) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: startColor.mod(NoteColors.size)) }
    val (paper, ink) = noteColor(color)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = { onSave(text, color) },
        containerColor = paper,
        shape = RoundedCornerShape(22.dp),
        confirmButton = { TextButton(onClick = { onSave(text, color) }) { Text("Done", color = ink) } },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = ink.copy(alpha = 0.7f)) }
        },
        text = {
            Column {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = TextStyle(color = ink, fontSize = 17.sp, lineHeight = 24.sp), cursorBrush = SolidColor(ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("Write something", color = ink.copy(alpha = 0.5f), fontSize = 17.sp)
                            inner()
                        }
                    },
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteColors.forEachIndexed { i, (_, dot) ->
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .border(2.dp, if (i == color) ink else Color.Transparent, CircleShape)
                                .padding(4.dp).clip(CircleShape).background(dot)
                                .clickable { color = i },
                        )
                    }
                }
            }
        },
    )
}
