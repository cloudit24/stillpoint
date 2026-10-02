package com.cloudit24.stillpoint.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.HaEntity
import com.cloudit24.stillpoint.data.MsgKind
import com.cloudit24.stillpoint.data.SelfHostedConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Down = Color(0xFFE08A78)
private val Pending = Color(0xFFE0B65A)
private val Maintenance = Color(0xFF8FB4E0)

@Composable
private fun SmallTitle(text: String, onClick: (() -> Unit)? = null) {
    Text(text, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier))
}

@Composable
private fun SetUpHint(text: String) {
    Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
}

// ---------------- Shelf cards ----------------

/** Your chosen Home Assistant entities as tiles. Tap a light, switch, scene or script to use it. */
@Composable
fun HaCard(vm: LauncherViewModel) {
    val c = vm.selfHosted
    SmallTitle("Home Assistant")
    if (!c.haReady || c.haEntities.isEmpty()) {
        SetUpHint("Connect Home Assistant and choose what to show in Settings, Extras, Self-hosted.")
        return
    }
    LaunchedEffect(c.haUrl, c.haToken, c.haEntities) {
        while (true) {
            vm.loadHa()
            delay(60_000)
        }
    }
    vm.haError?.let { Text(it, color = Down, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
    val accent = Accent
    vm.haStates.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { e ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (e.on) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .then(if (e.actionable) Modifier.clickable { vm.haToggle(e) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(e.name, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.stateText(), color = if (e.on) accent else Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Your Uptime Kuma status page: all up, or what is down, and a dot per service. Tap the title for the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KumaCard(vm: LauncherViewModel) {
    val c = vm.selfHosted
    val context = LocalContext.current
    if (!c.kumaReady) {
        SmallTitle("Uptime Kuma")
        SetUpHint("Add your status page in Settings, Extras, Self-hosted.")
        return
    }
    LaunchedEffect(c.kumaUrl, c.kumaSlug) {
        while (true) {
            vm.loadKuma()
            delay(120_000)
        }
    }
    val k = vm.kuma
    SmallTitle(k?.title?.ifBlank { null } ?: "Uptime Kuma") {
        context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(vm.kumaPage())))
    }
    vm.kumaError?.let { Text(it, color = Down, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
    if (k == null) {
        if (vm.kumaError == null) SetUpHint("Checking…")
        return
    }
    val down = k.monitors.filter { it.status == 0 }
    val pending = k.monitors.filter { it.status == 2 }
    Text(
        when {
            k.monitors.isEmpty() -> "No monitors on this page"
            down.isEmpty() -> "All ${k.monitors.size} up"
            else -> "${down.size} down"
        },
        color = if (down.isEmpty()) Accent else Down, fontSize = 26.sp, fontWeight = FontWeight.Light,
    )
    (down + pending).forEach { m ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (m.status == 0) Down else Pending))
            Text(m.name, color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text(if (m.status == 0) "down" else "pending", color = Muted, fontSize = 12.sp)
        }
    }
    val accent = Accent
    FlowRow(Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        k.monitors.forEach { m ->
            Box(Modifier.size(9.dp).clip(CircleShape).background(when (m.status) {
                1 -> accent
                0 -> Down
                2 -> Pending
                3 -> Maintenance
                else -> Muted.copy(alpha = 0.4f)
            }))
        }
    }
}

/** The latest messages from your ntfy topics or Gotify server, newest first. */
@Composable
fun MessagesCard(vm: LauncherViewModel) {
    val c = vm.selfHosted
    val context = LocalContext.current
    SmallTitle(if (c.msgKind == MsgKind.OFF) "Messages" else c.msgKind.label)
    if (!c.msgReady) {
        SetUpHint("Connect ntfy or Gotify in Settings, Extras, Self-hosted.")
        return
    }
    LaunchedEffect(c.msgKind, c.msgUrl, c.msgTopic, c.msgToken) {
        while (true) {
            vm.loadMessages()
            delay(60_000)
        }
    }
    vm.messagesError?.let { Text(it, color = Down, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
    if (vm.messagesError == null && vm.messages.isEmpty()) SetUpHint("No messages in the last day.")
    val today = LocalDate.now()
    vm.messages.take(5).forEach { m ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title.ifBlank { "Message" }, color = if (m.priority >= 4) Down else Ink, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val day = Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(if (day == today) formatClock(context, m.time) else day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (m.text.isNotBlank()) Text(m.text, color = Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Settings, Extras, Self-hosted ----------------

private class Field(val title: String, val hint: String, val initial: String, val secret: Boolean, val save: (String) -> Unit)

@Composable
fun SelfHostedSettings(vm: LauncherViewModel) {
    val c = vm.selfHosted
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf<Field?>(null) }
    var picking by remember { mutableStateOf(false) }
    var haTest by remember { mutableStateOf<String?>(null) }
    var kumaTest by remember { mutableStateOf<String?>(null) }
    var msgTest by remember { mutableStateOf<String?>(null) }
    fun set(f: (SelfHostedConfig) -> SelfHostedConfig) = vm.saveSelfHosted(f(vm.selfHosted))

    Group("Home Assistant") {
        ActionRow("Address", c.haUrl.ifBlank { "Not set" }) {
            field = Field("Home Assistant address", "http://homeassistant.local:8123", c.haUrl, false) { v -> set { it.copy(haUrl = v) } }
        }
        ActionRow("Access token", if (c.haToken.isBlank()) "Not set · Profile, Security, Long-lived access tokens" else "Saved on this phone") {
            field = Field("Long-lived access token", "Paste the token", "", true) { v -> set { it.copy(haToken = v) } }
        }
        if (c.haReady) {
            ActionRow("On the card", if (c.haEntities.isEmpty()) "Choose lights, switches, sensors…" else "${c.haEntities.size} chosen") { picking = true }
            ActionRow("Test connection", haTest ?: "Tap to check") {
                haTest = "Checking…"
                scope.launch { haTest = vm.testHa() }
            }
        }
    }
    Group("Uptime Kuma") {
        ActionRow("Address", c.kumaUrl.ifBlank { "Not set" }) {
            field = Field("Uptime Kuma address", "https://status.example.com", c.kumaUrl, false) { v -> set { it.copy(kumaUrl = v) } }
        }
        ActionRow("Status page", c.kumaSlug.ifBlank { "Not set · the last part of its address" }) {
            field = Field("Status page slug", "home (from …/status/home)", c.kumaSlug, false) { v -> set { it.copy(kumaSlug = v.substringAfterLast('/')) } }
        }
        if (c.kumaReady) ActionRow("Test connection", kumaTest ?: "Tap to check") {
            kumaTest = "Checking…"
            scope.launch { kumaTest = vm.testKuma() }
        }
    }
    Group("Messages") {
        ActionRow("Server", c.msgKind.label + " · tap to change") {
            set { it.copy(msgKind = MsgKind.entries[(it.msgKind.ordinal + 1) % MsgKind.entries.size]) }
            msgTest = null
        }
        if (c.msgKind != MsgKind.OFF) {
            ActionRow("Address", c.msgUrl.ifBlank { "Not set" }) {
                field = Field("${c.msgKind.label} address", if (c.msgKind == MsgKind.NTFY) "https://ntfy.sh" else "https://gotify.example.com",
                    c.msgUrl, false) { v -> set { it.copy(msgUrl = v) } }
            }
            if (c.msgKind == MsgKind.NTFY) {
                ActionRow("Topics", c.msgTopic.ifBlank { "Not set · several with commas" }) {
                    field = Field("ntfy topics", "alerts, backups", c.msgTopic, false) { v -> set { it.copy(msgTopic = v) } }
                }
                ActionRow("Access token", if (c.msgToken.isBlank()) "Only for protected topics" else "Saved on this phone") {
                    field = Field("ntfy access token", "tk_…", "", true) { v -> set { it.copy(msgToken = v) } }
                }
            } else {
                ActionRow("Client token", if (c.msgToken.isBlank()) "Not set · Gotify, Clients, Create client" else "Saved on this phone") {
                    field = Field("Gotify client token", "Paste the token", "", true) { v -> set { it.copy(msgToken = v) } }
                }
            }
            if (c.msgReady) ActionRow("Test connection", msgTest ?: "Tap to check") {
                msgTest = "Checking…"
                scope.launch { msgTest = vm.testMessages() }
            }
        }
    }
    Note("Each connects only to the address you enter. Tokens stay on this phone and aren't backed up. Add the cards from Shelf › Add.")

    field?.let { f ->
        var text by remember(f) { mutableStateOf(f.initial) }
        AlertDialog(
            onDismissRequest = { field = null },
            confirmButton = { TextButton(onClick = { f.save(text.trim()); field = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { field = null }) { Text("Cancel") } },
            title = { Text(f.title) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text(f.hint) },
                    visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None)
            },
        )
    }
    if (picking) HaPicker(vm, onDismiss = { picking = false })
}

/** Choose up to 12 entities for the card, from everything Home Assistant has. */
@Composable
private fun HaPicker(vm: LauncherViewModel, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf<List<HaEntity>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(vm.selfHosted.haEntities) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        vm.haAll().fold({ all = it }, { error = it.message })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { vm.saveSelfHosted(vm.selfHosted.copy(haEntities = picked)); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("On the card") },
        text = {
            Column {
                Text("Up to 12, in the order you pick them. ${picked.size} chosen.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Search") }, modifier = Modifier.padding(vertical = 8.dp))
                when {
                    error != null -> Text(error.orEmpty(), color = Down, fontSize = 14.sp)
                    all == null -> Text("Loading…", color = Muted, fontSize = 14.sp)
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        val q = filter.trim().lowercase()
                        items(all.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.id.contains(q) }, key = { it.id }) { e ->
                            val at = picked.indexOf(e.id)
                            Row(Modifier.fillMaxWidth().clickable {
                                picked = when {
                                    at >= 0 -> picked - e.id
                                    picked.size < 12 -> picked + e.id
                                    else -> picked
                                }
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, color = if (at >= 0) Accent else Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${e.id} · ${e.stateText()}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (at >= 0) Text("${at + 1}", color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
    )
}
