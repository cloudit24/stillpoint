package com.cloudit24.stillpoint.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.service.LockAccessibilityService
import kotlinx.coroutines.delay

/** Tick any number of apps. [selected] is read live, so ticks show at once. */
@Composable
fun AppMultiPicker(vm: LauncherViewModel, title: String, selected: Set<String>, onDismiss: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val apps = remember(vm.apps) { vm.apps.filter { it.key !in vm.settings.hidden }.sortedBy { it.label.lowercase() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(apps, key = { it.key }) { app ->
                    val on = app.key in selected
                    Row(Modifier.fillMaxWidth().clickable { onToggle(app.key, !on) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { onToggle(app.key, it) })
                        Text(app.label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_done_2)) } },
    )
}

/**
 * The pause before a hooked app: a few seconds to breathe, today's time and opens, then a choice.
 * "Not now" is always there; "Open" waits for the count.
 */
@Composable
fun PauseScreen(vm: LauncherViewModel, app: AppEntry) {
    val green = Color(0xFF1D9E75)
    val seconds = remember(app.key) { vm.pauseLength(app) }
    var left by remember(app.key) { mutableIntStateOf(seconds) }
    LaunchedEffect(app.key) { while (left > 0) { delay(1_000); left-- } }
    val used = vm.usage[app.packageName] ?: 0L
    val limit = vm.settings.appLimits[app.key]
    val opens = vm.opensToday[app.packageName] ?: 0
    Dialog(onDismissRequest = { vm.pausing = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(120.dp).border(3.dp, if (left > 0) green else Muted, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (left > 0) "$left" else "·", color = Color.White, fontSize = 40.sp)
            }
            Text(if (left > 0) "Take a breath" else "Still want it?", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp))
            Text("Opening ${app.label}", color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Today ${formatDuration(used)}" + (limit?.let { " of your ${formatDuration(it * 60_000L)}" } ?: "") +
                (if (opens > 0) " · $opens opens" else ""), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp))
            if (vm.overLimit(app)) Text("Today's limit is used up.", color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PauseButton("Not now", filled = true, enabled = true, Modifier.weight(1f)) { vm.pausing = null }
                PauseButton(if (left > 0) "Open (${left}s)" else "Open", filled = false, enabled = left == 0, Modifier.weight(1f)) { vm.launchNow(app) }
            }
        }
    }
}

@Composable
private fun PauseButton(text: String, filled: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.height(54.dp).clip(shape)
        .then(if (filled) Modifier.background(Accent) else Modifier.border(1.dp, Color(0xFF444444), shape))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.Black else if (enabled) Color.White else Muted, fontSize = 16.sp)
    }
}

/** Settings, Productivity, Wellbeing. */
@Composable
fun WellbeingSettings(vm: LauncherViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf<String?>(null) }
    val names = { keys: Collection<String> -> vm.apps.filter { it.key in keys }.joinToString(", ") { it.label }.ifEmpty { "None" } }
    val guardService = remember(vm.resumeTick) { LockAccessibilityService.isEnabled(ctx) }

    Group("Always allowed") {
        ActionRow(names(s.alwaysAllowed), "Open in every focus, at bedtime and without a pause") { pick = "always" }
    }
    Note("For family and work emergencies.")
    Group("Pause before opening") {
        ActionRow("Apps that pause", names(s.hookedApps)) { pick = "hooked" }
        Stepper("Pause", "${s.pauseSeconds} s",
            onMinus = { vm.updateSettings { it.copy(pauseSeconds = (it.pauseSeconds - 1).coerceAtLeast(3)) } },
            onPlus = { vm.updateSettings { it.copy(pauseSeconds = (it.pauseSeconds + 1).coerceAtMost(15)) } })
    }
    Note("A few seconds to breathe, with today's time and opens. Three times longer once a daily limit is used up.")
    Group("Daily limits") {
        s.appLimits.forEach { (key, m) ->
            Stepper(vm.apps.firstOrNull { it.key == key }?.label ?: "Removed app", formatDuration(m * 60_000L),
                onMinus = { vm.setLimit(key, m - 15) }, onPlus = { vm.setLimit(key, (m + 15).coerceAtMost(600)) })
        }
        ActionRow("Add a limit", "Choose apps; each starts at 30 minutes") { pick = "limit" }
    }
    Note("Past its limit an app still opens, after a longer pause. Go below 15 minutes to remove a limit.")
    Group("Screen time") {
        Stepper("Daily goal", if (s.screenGoal == 0) "Off" else formatDuration(s.screenGoal * 60_000L),
            onMinus = { vm.updateSettings { it.copy(screenGoal = (it.screenGoal - 30).coerceAtLeast(0)) } },
            onPlus = { vm.updateSettings { it.copy(screenGoal = (it.screenGoal + 30).coerceAtMost(720)) } })
        ToggleRow("Calm home: no icons, times or dots", s.calmHome) { on -> vm.updateSettings { it.copy(calmHome = on) } }
    }
    Note("The terminal display shows your unlocks, your goal and, on Fridays, the week.")
    Group("Focus") {
        ActionRow("Profiles", s.focusProfiles.joinToString(", ") { it.name }) { vm.screen = Screen.FOCUS }
    }
    Group("Stronger guard") {
        ToggleRow("Also for apps opened from notifications", s.guardAll) { on -> vm.updateSettings { it.copy(guardAll = on) } }
        if (s.guardAll && !guardService) {
            ActionRow("Turn on the gesture service", "Android settings, Accessibility, Stillpoint") {
                ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }
    Note("Without it, the pause and focus work for apps opened from Stillpoint. With it, the gesture service notices which app opens. It reads nothing on screen and sends nothing.")

    when (pick) {
        "always" -> AppMultiPicker(vm, "Always allowed", s.alwaysAllowed, onDismiss = { pick = null }) { k, on -> vm.setAlways(k, on) }
        "hooked" -> AppMultiPicker(vm, "Apps that pause", s.hookedApps, onDismiss = { pick = null }) { k, on -> vm.setHooked(k, on) }
        "limit" -> AppMultiPicker(vm, "Daily limits", s.appLimits.keys, onDismiss = { pick = null }) { k, on -> vm.setLimit(k, if (on) 30 else 0) }
    }
}
