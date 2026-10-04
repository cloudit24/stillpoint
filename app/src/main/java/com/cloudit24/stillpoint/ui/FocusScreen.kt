package com.cloudit24.stillpoint.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.FocusProfile
import kotlinx.coroutines.delay

private const val END_EARLY_WAIT_S = 10
private val LENGTHS = listOf(15, 25, 30, 45, 60, 90, 120, 180, 240, 480)
private val KIND_ICONS = listOf(Icons.Outlined.Build, Icons.Outlined.Place, Icons.Outlined.Lock, Icons.Outlined.Home, Icons.Outlined.Star)

private fun lengthWord(m: Int) = if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
private fun clockWord(context: android.content.Context, minute: Int): String =
    formatClock(context, java.time.LocalDate.now().atTime(minute / 60, minute % 60)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())

/** Focus: pick a profile to start; while one is on, only its apps and the always-allowed ones open. */
@Composable
fun FocusScreen(vm: LauncherViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val now by rememberTicker(1_000L)
    val active = vm.activeFocus(now)
    var editing by remember { mutableStateOf<FocusProfile?>(null) }
    var pickAlways by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        if (active != null) {
            val until = vm.focusUntil(now)
            Text("${active.name} focus".uppercase(), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
            Text(formatRemaining(until - now), fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, modifier = Modifier.padding(top = 8.dp))
            Text("Until ${formatClock(context, until)} · ${vm.focusAllowedNow().size} apps open", color = Muted, fontSize = 14.sp)
            SectionHeader("Allowed apps")
            val allowed = vm.visibleApps()
            if (allowed.isEmpty()) Text(stringResource(R.string.s_no_apps_allowed_in_this_session), color = Muted)
            LazyColumn(Modifier.weight(1f)) {
                items(allowed, key = { it.key }) { app -> AppRow(app.label, null, 19.sp, onClick = { vm.launch(app) }) }
            }
            EndEarly(onEnd = { vm.endFocus(); vm.screen = Screen.HOME })
        } else {
            Text(stringResource(R.string.s_focus), fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(stringResource(R.string.s_focus_profiles_note), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.weight(1f).padding(top = 16.dp)) {
                items(s.focusProfiles, key = { it.id }) { p ->
                    ProfileCard(p, onStart = { vm.startFocus(p); vm.screen = Screen.HOME }, onEdit = { editing = p })
                }
                item {
                    Text("+ New profile", color = Accent, fontSize = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { editing = FocusProfile(System.currentTimeMillis(), "", 4) }
                            .padding(vertical = 12.dp, horizontal = 4.dp))
                }
                item {
                    SectionHeader("Always allowed")
                    ActionRow(vm.apps.filter { it.key in s.alwaysAllowed }.joinToString(", ") { it.label }.ifEmpty { "None yet · tap to choose" },
                        "Open in every focus and without a pause. For family and work emergencies.") { pickAlways = true }
                }
            }
        }
    }

    editing?.let { p -> ProfileEditor(vm, p, isNew = s.focusProfiles.none { it.id == p.id }, onDismiss = { editing = null }) }
    if (pickAlways) AppMultiPicker(vm, "Always allowed", s.alwaysAllowed, onDismiss = { pickAlways = false }) { key, on -> vm.setAlways(key, on) }
}

@Composable
private fun ProfileCard(p: FocusProfile, onStart: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).border(1.dp, Muted.copy(alpha = 0.3f), shape)
            .clickable(onClick = onStart).padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIND_ICONS[p.kind.coerceIn(0, KIND_ICONS.lastIndex)], contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(p.name, fontSize = 17.sp)
            Text("${p.apps.size} apps · ${lengthWord(p.minutes)}" +
                (if (p.scheduled) " · ${clockWord(context, p.from)}–${clockWord(context, p.to)}" else ""),
                color = Muted, fontSize = 13.sp)
        }
        Text("Edit", color = Muted, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** Name, length, an optional daily window, and the apps a profile lets through. */
@Composable
private fun ProfileEditor(vm: LauncherViewModel, initial: FocusProfile, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var p by remember { mutableStateOf(initial) }
    var pickApps by remember { mutableStateOf(false) }
    val pickTime = { start: Int, set: (Int) -> Unit ->
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, start / 60, start % 60, DateFormat.is24HourFormat(context)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New profile" else p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(p.name, { p = p.copy(name = it.take(24)) }, label = { Text(stringResource(R.string.s_name)) }, singleLine = true)
                ActionRow("Apps", vm.apps.filter { it.key in p.apps }.joinToString(", ") { it.label }.ifEmpty { "None · tap to choose" }) { pickApps = true }
                Stepper("Length", lengthWord(p.minutes),
                    onMinus = { p = p.copy(minutes = LENGTHS.lastOrNull { it < p.minutes } ?: LENGTHS.first()) },
                    onPlus = { p = p.copy(minutes = LENGTHS.firstOrNull { it > p.minutes } ?: LENGTHS.last()) })
                ToggleRow("Turns on by itself every day", p.scheduled) { on ->
                    p = if (on) p.copy(from = if (p.kind == 2) 23 * 60 else 9 * 60, to = if (p.kind == 2) 6 * 60 else 17 * 60) else p.copy(from = -1, to = -1)
                }
                if (p.scheduled) {
                    ActionRow("From", clockWord(context, p.from)) { pickTime(p.from) { p = p.copy(from = it) } }
                    ActionRow("To", clockWord(context, p.to)) { pickTime(p.to) { p = p.copy(to = it) } }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    KIND_ICONS.forEachIndexed { i, icon ->
                        Icon(icon, contentDescription = null, tint = if (p.kind == i) Accent else Muted,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { p = p.copy(kind = i) }.padding(10.dp).size(22.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveProfile(p.copy(name = p.name.trim().ifEmpty { "Focus" })); onDismiss() }) { Text(stringResource(R.string.s_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deleteProfile(p.id); onDismiss() }) { Text(stringResource(R.string.s_delete), color = Muted) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) }
            }
        },
    )
    if (pickApps) AppMultiPicker(vm, "Apps for ${p.name.ifBlank { "this profile" }}", p.apps, onDismiss = { pickApps = false }) { key, on ->
        p = p.copy(apps = if (on) p.apps + key else p.apps - key)
    }
}

@Composable
private fun EndEarly(onEnd: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(END_EARLY_WAIT_S) }

    if (!confirming) {
        TextButton(onClick = { secondsLeft = END_EARLY_WAIT_S; confirming = true }) {
            Text(stringResource(R.string.s_end_early), color = Muted)
        }
        return
    }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) { delay(1_000); secondsLeft-- }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onEnd, enabled = secondsLeft == 0) {
            Text(if (secondsLeft > 0) "End in ${secondsLeft}s" else "End focus now")
        }
        TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.s_keep_focusing), color = Muted) }
    }
}
