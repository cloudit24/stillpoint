package com.cloudit24.stillpoint.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Action
import com.cloudit24.stillpoint.data.AppTheme
import com.cloudit24.stillpoint.data.Automation
import com.cloudit24.stillpoint.data.Rule
import com.cloudit24.stillpoint.data.Trigger

/** Settings → Productivity → Automation: rules in plain sentences, and the Tasker bridge. */
@Composable
fun AutomationSettings(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var editing by remember { mutableStateOf<Rule?>(null) }

    Group(stringResource(R.string.s_rules)) {
        if (s.rules.isEmpty()) Note(stringResource(R.string.s_rules_empty))
        s.rules.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.sentence(s), fontSize = 16.sp, color = if (r.enabled) Ink else Muted,
                    modifier = Modifier.weight(1f).clickable { editing = r }.padding(vertical = 4.dp))
                Switch(checked = r.enabled, onCheckedChange = { vm.saveRule(r.copy(enabled = it)) })
            }
        }
        ActionRow("+ " + stringResource(R.string.s_add_a_rule)) {
            editing = Rule(System.currentTimeMillis(), Trigger.IQAMA, "", Action.START_FOCUS, s.focusProfiles.firstOrNull()?.id?.toString().orEmpty())
        }
        Note(stringResource(R.string.s_rules_note))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (s.rules.any { it.action == Action.SILENT } && nm?.isNotificationPolicyAccessGranted != true) {
            ActionRow(stringResource(R.string.s_allow_dnd), stringResource(R.string.s_allow_dnd_sum)) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }
        }
    }

    Group(stringResource(R.string.s_tasker)) {
        ToggleRow(stringResource(R.string.s_send_events_to_tasker), s.taskerOut) { on -> vm.updateSettings { it.copy(taskerOut = on) } }
        ToggleRow(stringResource(R.string.s_accept_commands_from_tasker), s.taskerIn) { on -> vm.updateSettings { it.copy(taskerIn = on) } }
        ActionRow(stringResource(R.string.s_copy_event_names), stringResource(R.string.s_copy_event_names_sum)) {
            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stillpoint", Automation.cheatSheet()))
        }
        Note(stringResource(R.string.s_tasker_note))
    }

    editing?.let { r -> RuleDialog(vm, r, isNew = s.rules.none { it.id == r.id }, onDismiss = { editing = null }) }
}

/** One rule: pick the when, the then, and whatever they need (a focus, a theme, minutes, a line). */
@Composable
private fun RuleDialog(vm: LauncherViewModel, initial: Rule, isNew: Boolean, onDismiss: () -> Unit) {
    val s = vm.settings
    var r by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }
    val profiles = s.focusProfiles
    val profileName = { id: String -> profiles.firstOrNull { it.id.toString() == id }?.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { vm.saveRule(r); onDismiss() }) { Text(stringResource(R.string.s_save)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deleteRule(r.id); onDismiss() }) { Text(stringResource(R.string.s_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) }
            }
        },
        title = { Text(stringResource(if (isNew) R.string.s_new_rule else R.string.s_rule)) },
        text = {
            Column {
                Text(r.sentence(s), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                ActionRow(stringResource(R.string.s_when), r.trigger.label) { picking = "trigger" }
                if (r.trigger.picksFocus) ActionRow(stringResource(R.string.s_which_focus),
                    profileName(r.triggerArg) ?: stringResource(R.string.s_any_focus)) { picking = "triggerFocus" }
                ActionRow(stringResource(R.string.s_then), r.action.label) { picking = "action" }
                when (r.action) {
                    Action.START_FOCUS -> ActionRow(stringResource(R.string.s_which_focus),
                        profileName(r.actionArg) ?: profiles.firstOrNull()?.name ?: "") { picking = "actionFocus" }
                    Action.THEME -> ActionRow(stringResource(R.string.s_theme),
                        runCatching { AppTheme.valueOf(r.actionArg).label }.getOrDefault(AppTheme.STILLPOINT.label)) { picking = "theme" }
                    Action.SILENT -> {
                        val min = r.actionArg.toIntOrNull() ?: 20
                        Stepper(stringResource(R.string.s_minutes), "$min",
                            onMinus = { r = r.copy(actionArg = "${(min - 5).coerceAtLeast(5)}") },
                            onPlus = { r = r.copy(actionArg = "${(min + 5).coerceAtMost(180)}") })
                    }
                    Action.LINE -> OutlinedTextField(r.actionArg, { r = r.copy(actionArg = it) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.s_line_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    else -> {}
                }
            }
        },
    )

    when (picking) {
        "trigger" -> Pick(stringResource(R.string.s_when), Trigger.entries, { it.label }, { picking = null }) { r = r.copy(trigger = it, triggerArg = "") }
        "action" -> Pick(stringResource(R.string.s_then), Action.entries, { it.label }, { picking = null }) {
            r = r.copy(action = it, actionArg = if (it == Action.START_FOCUS) profiles.firstOrNull()?.id?.toString().orEmpty() else "")
        }
        "triggerFocus" -> Pick(stringResource(R.string.s_which_focus), listOf(null) + profiles,
            { it?.name ?: stringResource(R.string.s_any_focus) }, { picking = null }) { r = r.copy(triggerArg = it?.id?.toString().orEmpty()) }
        "actionFocus" -> Pick(stringResource(R.string.s_which_focus), profiles, { it.name }, { picking = null }) { r = r.copy(actionArg = it.id.toString()) }
        "theme" -> Pick(stringResource(R.string.s_theme), AppTheme.entries, { it.label }, { picking = null }) { r = r.copy(actionArg = it.name) }
    }
}

@Composable
private fun <T> Pick(title: String, options: List<T>, label: @Composable (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { o ->
                    Text(label(o), fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(o); onDismiss() }.padding(vertical = 10.dp))
                }
            }
        },
    )
}
