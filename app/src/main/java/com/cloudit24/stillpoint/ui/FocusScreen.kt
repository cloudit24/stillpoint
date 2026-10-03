package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import kotlinx.coroutines.delay

private val DURATIONS = listOf(25, 50, 90, 180)
private const val END_EARLY_WAIT_S = 10

@Composable
fun FocusScreen(vm: LauncherViewModel) {
    val s = vm.settings
    val now by rememberTicker(1_000L)
    val active = s.focusEndsAt > now

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.s_focus), fontSize = 34.sp, fontWeight = FontWeight.Light)

        if (active) {
            Text(formatRemaining(s.focusEndsAt - now), fontSize = 56.sp, fontWeight = FontWeight.ExtraLight,
                modifier = Modifier.padding(top = 16.dp))
            SectionHeader("Allowed apps")
            val allowed = vm.visibleApps()
            if (allowed.isEmpty()) Text(stringResource(R.string.s_no_apps_allowed_in_this_session), color = Muted)
            LazyColumn(Modifier.weight(1f)) {
                items(allowed, key = { it.key }) { app ->
                    AppRow(app.label, null, 19.sp, onClick = { vm.launch(app) })
                }
            }
            EndEarly(onEnd = { vm.endFocus(); vm.screen = Screen.HOME })
        } else {
            var minutes by rememberSaveable { mutableIntStateOf(25) }
            Text(stringResource(R.string.s_only_the_apps_you_tick_can),
                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))

            Row(Modifier.padding(top = 20.dp)) {
                DURATIONS.forEach { d ->
                    val selected = d == minutes
                    Text(
                        if (d < 60) "${d}m" else if (d % 60 == 0) "${d / 60}h" else "${d / 60}h ${d % 60}m",
                        color = if (selected) Color.Black else Ink,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, if (selected) Slate else Muted, RoundedCornerShape(20.dp))
                            .then(if (selected) Modifier.background(Slate) else Modifier)
                            .clickable { minutes = d }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            SectionHeader("Allowed during focus")
            val candidates = vm.apps.filter { it.key !in s.hidden }
            LazyColumn(Modifier.weight(1f)) {
                items(candidates, key = { it.key }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleFocusAllowed(app) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = app.key in s.focusAllowed, onCheckedChange = { vm.toggleFocusAllowed(app) })
                        Text(app.label, fontSize = 17.sp)
                    }
                }
            }
            Button(
                onClick = { vm.startFocus(minutes); vm.screen = Screen.HOME },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.s_start_focus)) }
        }
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
