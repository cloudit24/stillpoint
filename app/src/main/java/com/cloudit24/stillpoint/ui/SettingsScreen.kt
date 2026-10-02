package com.cloudit24.stillpoint.ui

import com.cloudit24.stillpoint.data.AccentStyle
import androidx.compose.foundation.layout.height
import com.cloudit24.stillpoint.notify.NotifyTest
import com.cloudit24.stillpoint.notify.NotifyHub
import com.cloudit24.stillpoint.data.NotifyStyle
import com.cloudit24.stillpoint.data.AppEntry
import androidx.compose.material3.Switch
import com.cloudit24.stillpoint.data.Backup
import androidx.compose.material.icons.outlined.Send
import com.cloudit24.stillpoint.data.SourceKey
import androidx.compose.material.icons.outlined.Share
import com.cloudit24.stillpoint.update.UpdateNotice
import androidx.compose.material.icons.outlined.Settings
import kotlinx.coroutines.delay
import com.cloudit24.stillpoint.data.AppFont
import com.cloudit24.stillpoint.data.DialMode
import com.cloudit24.stillpoint.data.InfoPanel
import com.cloudit24.stillpoint.data.IconTint
import com.cloudit24.stillpoint.CrashLog
import androidx.compose.material.icons.outlined.LocationOn
import com.cloudit24.stillpoint.data.EdgeStyle
import androidx.compose.material.icons.outlined.PlayArrow
import com.cloudit24.stillpoint.widget.PrayerAlerts
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.LauncherSettings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Place
import com.cloudit24.stillpoint.data.CalendarSource
import com.cloudit24.stillpoint.data.ProjectSource
import com.cloudit24.stillpoint.data.SYNC_INTERVALS
import com.cloudit24.stillpoint.data.SyncConfig
import com.cloudit24.stillpoint.data.SyncFeature
import com.cloudit24.stillpoint.data.TaskSource
import com.cloudit24.stillpoint.data.intervalLabel
import com.cloudit24.stillpoint.sync.Sync
import androidx.compose.material.icons.outlined.Notifications
import com.cloudit24.stillpoint.widget.LockNotification
import com.cloudit24.stillpoint.widget.Refresh
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import com.cloudit24.stillpoint.data.ACCENTS
import com.cloudit24.stillpoint.data.DrawerTab
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.BuildConfig
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.City
import com.cloudit24.stillpoint.data.ClockStyle
import com.cloudit24.stillpoint.data.GOLD_CURRENCIES
import com.cloudit24.stillpoint.data.GestureSlot
import com.cloudit24.stillpoint.data.GoldSource
import com.cloudit24.stillpoint.data.GestureTarget
import com.cloudit24.stillpoint.data.HomeAction
import com.cloudit24.stillpoint.data.HomeStyle
import com.cloudit24.stillpoint.data.PrayerMethod
import com.cloudit24.stillpoint.update.UpdateCheck
import com.cloudit24.stillpoint.update.Updater
import com.cloudit24.stillpoint.data.HomeMode
import com.cloudit24.stillpoint.service.LockAccessibilityService
import kotlinx.coroutines.launch

/** Settings pages, grouped on the main screen under [section]. */
private enum class SettingsPage(val section: String, val title: String, val summary: String, val icon: ImageVector) {
    APPEARANCE("Personalization", "Appearance", "Accent, icons, app style and size, edge light", Icons.Outlined.Face),
    HOME("Personalization", "Home screen", "Headline, calendars, swipe cards, apps, footer", Icons.Outlined.Home),
    APPS("Personalization", "App list", "Starting tab, hidden apps", Icons.Outlined.Menu),
    GESTURES("Personalization", "Gestures and shortcuts", "Swipes, double-tap, bottom shortcuts", Icons.Outlined.ThumbUp),
    NOTIFY("Personalization", "Notification", "Edge light, signal dot and app dots; never miss important people", Icons.Outlined.Star),
    LOCK("Personalization", "Lock screen", "Next prayer and dates on the lock screen", Icons.Outlined.Notifications),
    TASKS("Productivity", "Tasks", "On this phone, Project Hub, Tasks.org or OpenTasks", Icons.Outlined.Done),
    CALENDAR("Productivity", "Calendar", "Phone calendar, Project Hub or a calendar link", Icons.Outlined.DateRange),
    PRAYER("Extras", "Islamic prayer", "Prayer times, alerts, iqama, Qibla", Icons.Outlined.Place),
    WEATHER("Extras", "Weather", "Headline card · uses Open-Meteo", Icons.Outlined.LocationOn),
    GOLD("Extras", "Gold price", "Home line and widget · uses outside websites", Icons.Outlined.Star),
    HUB("Extras", "Project Hub", "Connection to your own server", Icons.Outlined.CheckCircle),
    SELFHOSTED("Extras", "Self-hosted", "Home Assistant, Uptime Kuma, ntfy and Gotify", Icons.Outlined.Home),
    TOOLS("Extras", "Tools", "Device info, passwords, QR, ping, DNS and more", Icons.Outlined.Build),
    PRIVACY("System", "Permissions and data", "Usage access, gesture service, data usage", Icons.Outlined.Lock),
    BACKUP("System", "Backup and restore", "Keep your setup in a file, bring it to a new phone", Icons.Outlined.Send),
    FOOTPRINT("System", "Battery and memory", "What Stillpoint itself uses", Icons.Outlined.Settings),
    SOURCES("System", "Online sources", "Use your own server or another service for anything online", Icons.Outlined.Share),
    UPDATES("System", "Updates", "Download new versions from GitHub", Icons.Outlined.Refresh),
    SYSTEM("System", "System", "Permissions, backup, battery, online sources, updates", Icons.Outlined.Settings),
    ABOUT("System", "About", "Version, source code and privacy", Icons.Outlined.Info),
}

/** Pages reached from inside another page rather than from the main list. */
private val SettingsPage.parent: SettingsPage?
    get() = when (this) {
        SettingsPage.GESTURES -> SettingsPage.HOME
        SettingsPage.PRIVACY, SettingsPage.BACKUP, SettingsPage.FOOTPRINT, SettingsPage.SOURCES, SettingsPage.UPDATES -> SettingsPage.SYSTEM
        else -> null
    }

private val SettingsPage?.depth: Int get() = if (this == null) 0 else if (parent == null) 1 else 2

private val CardColor = Color(0xFF121211)
private val DividerColor = Color(0xFF232321)

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val a11yOn = remember(vm.resumeTick) { LockAccessibilityService.isEnabled(ctx) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var picking by remember { mutableStateOf<GestureSlot?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var textEdit by remember { mutableStateOf<TextEdit?>(null) }
    var backupNote by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupNote = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ctx).toByteArray()) }
                "Saved. Keep the file somewhere safe, like Drive or a computer."
            }.getOrElse { "Couldn't save the file: ${it.message}" }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                Backup.import(ctx, text)
            }.onSuccess { restored = true }.onFailure { backupNote = it.message ?: "Couldn't read that file." }
        }
    }
    val settingsScope = rememberCoroutineScope()
    // Checks GitHub quietly when Settings opens (at most every 6 hours) so a new version shows at the top.
    var available by remember { mutableStateOf(UpdateNotice.cached()) }
    LaunchedEffect(Unit) { UpdateNotice.check()?.let { available = it } }

    BackHandler(enabled = page != null) { page = page?.parent }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(lockOn = granted) }
        Refresh.all(ctx)
    }

    var pendingAlert by remember { mutableStateOf<((LauncherSettings) -> LauncherSettings)?>(null) }
    val alertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val f = pendingAlert
        pendingAlert = null
        if (granted && f != null) vm.updateSettings(f)
    }
    val alertToggle: (Boolean, (LauncherSettings) -> LauncherSettings) -> Unit = { on, f ->
        when {
            on && s.city == null -> dialog = SettingsDialog.PRAYER_CITY
            on && !LockNotification.canPost(ctx) -> {
                pendingAlert = f
                alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> vm.updateSettings(f)
        }
    }
    // Any change to the city, method or alerts moves the next alert.
    LaunchedEffect(s.city, s.prayerMethod, s.asrHanafi, s.adhanAlert, s.iqamaAlert, s.iqama) { PrayerAlerts.schedule(ctx) }

    var pendingTaskSource by remember { mutableStateOf<TaskSource?>(null) }
    val taskPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val src = pendingTaskSource
        pendingTaskSource = null
        if (src != null) {
            if (src.readPermission?.let { result[it] } == true) vm.setTaskSource(src)
            else vm.blockedMessage = "Stillpoint needs permission to read ${src.label}. Allow it in App info, Permissions."
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(showAgenda = granted) }
        vm.refresh()
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            (slideInHorizontally(tween(240)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(180)) { w -> if (forward) -w / 5 else w / 5 } + fadeOut(tween(160)))
        },
        label = "settings",
    ) { current ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (current == null) {
                Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
                available?.let { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                            .background(Accent.copy(alpha = 0.16f)).clickable { page = SettingsPage.UPDATES }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Accent)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Update available: ${a.version}", fontSize = 16.sp)
                            Text("You have ${BuildConfig.VERSION_NAME}. Tap to install.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Row(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink,
                        modifier = Modifier.clip(CircleShape).clickable { page = current.parent }.padding(8.dp))
                    Text(current.title, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 8.dp))
                }
            }

            when (current) {
                null -> {
                    SettingsPage.entries
                        .filter { it.parent == null }
                        .groupBy { it.section }
                        .forEach { (section, pages) ->
                            Group(section) {
                                if (section == "Productivity") TileRow(pages) { page = it }
                                else pages.forEachIndexed { i, p ->
                                    if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                                    MenuRow(p.icon, p.title, p.summary) { page = p }
                                }
                            }
                        }
                    Text("Stillpoint Launcher ${BuildConfig.VERSION_NAME} · by cloudit24", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = TextAlign.Center)
                }

                SettingsPage.APPEARANCE -> {
                    Group("Theme") {
                        Row(Modifier.fillMaxWidth().clickable { dialog = SettingsDialog.ACCENT }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Accent colour", fontSize = 16.sp)
                                Text(ACCENTS.firstOrNull { it.argb == s.accent }?.name ?: "Custom", color = Muted, fontSize = 13.sp)
                            }
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(s.accent)))
                        }
                        // Accent style: four swatches in the chosen colour.
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccentStyle.entries.forEach { st ->
                                val on = s.accentStyle == st
                                val shape = RoundedCornerShape(12.dp)
                                Column(Modifier.weight(1f).clip(shape).clickable { vm.updateSettings { it.copy(accentStyle = st) } },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height(44.dp).clip(shape).background(accentBrush(Color(s.accent), st))
                                        .then(if (on) Modifier.border(2.dp, Ink, shape) else Modifier))
                                    Text(st.label, fontSize = 12.sp, color = if (on) Ink else Muted, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        ActionRow("Icon colours", s.iconTint.label) { dialog = SettingsDialog.ICON_TINT }
                        ActionRow("Font", s.font.label) { dialog = SettingsDialog.FONT }
                    }
                    Group("Apps") {
                        ActionRow("Home apps as", s.homeStyle.label) { dialog = SettingsDialog.HOME_STYLE }
                        Stepper("App size on home", "${s.homeSize}",
                            onMinus = { vm.updateSettings { it.copy(homeSize = (it.homeSize - 2).coerceAtLeast(16)) } },
                            onPlus = { vm.updateSettings { it.copy(homeSize = (it.homeSize + 2).coerceAtMost(40)) } })
                        ToggleRow("Icons in lists", s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }
                    }
                    Group("Edge light") {
                        ActionRow("Style", s.edgeStyle.label) { dialog = SettingsDialog.EDGE_STYLE }
                        if (s.edgeStyle == EdgeStyle.CURVED || s.edgeStyle == EdgeStyle.FLAT) {
                            ActionRow("Side", if (s.edgeRight) "Right" else "Left") {
                                vm.updateSettings { it.copy(edgeRight = !it.edgeRight) }
                            }
                        }
                        if (s.edgeStyle != EdgeStyle.OFF) {
                            ActionRow("Brightness", listOf("Low", "Medium", "High")[s.edgeBright.coerceIn(1, 3) - 1]) {
                                vm.updateSettings { it.copy(edgeBright = it.edgeBright % 3 + 1) }
                            }
                            Stepper("Blink before a prayer ends", "${s.edgeWarnMin} min",
                                onMinus = { vm.updateSettings { it.copy(edgeWarnMin = (it.edgeWarnMin - 5).coerceAtLeast(5)) } },
                                onPlus = { vm.updateSettings { it.copy(edgeWarnMin = (it.edgeWarnMin + 5).coerceAtMost(60)) } })
                        }
                    }
                    Note(s.edgeStyle.detail + if (s.edgeStyle == EdgeStyle.OFF) "" else " Bright: the next prayer. Dim: what's left of this one." +
                        if (!s.prayerOn || s.city == null) " Needs Islamic prayer with a city." else "")
                    Group("Motion") {
                        ToggleRow("Animations", s.edgeMotion) { on -> vm.updateSettings { it.copy(edgeMotion = on) } }
                    }
                    Note("Spark, blink and pulse. They run only while home is open.")
                }

                SettingsPage.HOME -> {
                    var worldOpen by remember { mutableStateOf(false) }
                    Group("Gestures") {
                        SettingsPage.GESTURES.let { MenuRow(it.icon, it.title, it.summary) { page = it } }
                    }
                    Group("Headline") {
                        ToggleRow("Screen time card", s.showUsage) { on -> vm.updateSettings { it.copy(showUsage = on) } }
                        ActionRow("Ring beside it", s.dialMode.label) { dialog = SettingsDialog.DIAL_MODE }
                    }
                    Group("Calendars") {
                        ToggleRow("Hijri", s.hijriOn) { on -> vm.updateSettings { it.copy(hijriOn = on) } }
                        if (s.hijriOn) {
                            Stepper("Hijri adjustment (days)", if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}",
                                onMinus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust - 1).coerceAtLeast(-2)) } },
                                onPlus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust + 1).coerceAtMost(2)) } })
                        }
                        ToggleRow("Indian national (Saka)", s.sakaOn) { on -> vm.updateSettings { it.copy(sakaOn = on) } }
                        ToggleRow("Malayalam (Kollavarsham)", s.malayalamOn) { on -> vm.updateSettings { it.copy(malayalamOn = on) } }
                        ToggleRow("Tamil", s.tamilOn) { on -> vm.updateSettings { it.copy(tamilOn = on) } }
                    }
                    Note("Shown in the headline and on the week card. Hijri uses Umm al-Qura; adjust it to your moon sighting.")
                    Group("Cards under the headline") {
                        InfoPanel.entries.filter { it != InfoPanel.PRAYER }.forEach { panel ->
                            ToggleRow(panel.label, panel in s.infoPanels) { on ->
                                vm.updateSettings { it.copy(infoPanels = if (on) it.infoPanels + panel else it.infoPanels - panel) }
                                if (on && panel == InfoPanel.AGENDA) vm.refresh()
                                if (on && panel == InfoPanel.WORLD && s.worldClocks.isEmpty()) worldOpen = true
                            }
                        }
                        if (InfoPanel.WORLD in s.infoPanels) {
                            ActionRow("World clock cities",
                                s.worldClocks.joinToString(", ") { it.substringBefore("|") }.ifEmpty { "Choose up to three" }) { worldOpen = true }
                        }
                    }
                    Note("Swipe between cards on home. Weather appears once it's on in Extras.")
                    if (worldOpen) {
                        WorldCitiesDialog(s.worldClocks, onDismiss = { worldOpen = false }) { list ->
                            vm.updateSettings { it.copy(worldClocks = list) }
                            worldOpen = false
                        }
                    }
                    Group("Footer") {
                        ToggleRow("Network speed and memory", s.showStats) { on -> vm.updateSettings { it.copy(showStats = on) } }
                        ToggleRow("Local IP address", s.showLocalIp) { on -> vm.updateSettings { it.copy(showLocalIp = on) } }
                        ToggleRow("Public IP address", s.publicIpOn) { on -> vm.updateSettings { it.copy(publicIpOn = on) } }
                    }
                    Note("Public IP from api.ipify.org, checked only when your network changes.")
                    Group("Apps") {
                        ToggleRow("Recently used (24 h)", s.showRecent) { on -> vm.updateSettings { it.copy(showRecent = on) } }
                        ToggleRow("Most used today", s.homeMode == HomeMode.AUTO) { on ->
                            vm.updateSettings { it.copy(homeMode = if (on) HomeMode.AUTO else HomeMode.PINNED) }
                        }
                        Stepper("Apps on home", "${s.homeCount}",
                            onMinus = { vm.updateSettings { it.copy(homeCount = (it.homeCount - 1).coerceAtLeast(3)) } },
                            onPlus = { vm.updateSettings { it.copy(homeCount = (it.homeCount + 1).coerceAtMost(9)) } })
                    }
                    Note("Long-press an app to pin it. Most used and Recent need usage access.")
                    Group("Widgets") {
                        Text("Swipe right on home for the Shelf. Add more shelves with +, and put Stillpoint cards " +
                            "(notes, tasks, calendar, countdown...) or any app's widgets on them. Stillpoint's own widgets: Stillpoint Widget " +
                            "(clock and dates), Stillpoint Prayer (times, countdown, moon) and Stillpoint Gold. " +
                            "They work in any launcher too.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                }

                SettingsPage.APPS -> {
                    Group("Behaviour") {
                        ActionRow("Open the app list on", s.drawerStart.label.replaceFirstChar { it.uppercase() }) {
                            dialog = SettingsDialog.DRAWER_START
                        }
                    }
                    Group("Hidden apps") {
                        val hidden = vm.apps.filter { it.key in s.hidden }
                        if (hidden.isEmpty()) Text("None. Long-press an app in the list to hide it.", color = Muted,
                            fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        hidden.forEach { app ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(app.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("Unhide", color = Accent, modifier = Modifier.clickable { vm.unhide(app.key) }.padding(8.dp))
                            }
                        }
                    }
                }

                SettingsPage.GESTURES -> {
                    Group("Swipes and taps") {
                        listOf(GestureSlot.SWIPE_LEFT, GestureSlot.SWIPE_RIGHT, GestureSlot.SWIPE_UP, GestureSlot.SWIPE_DOWN,
                            GestureSlot.DOUBLE_TAP).forEach { slot ->
                            ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
                        }
                    }
                    Group("Bottom shortcuts") {
                        listOf(GestureSlot.SHORTCUT_LEFT, GestureSlot.SHORTCUT_MIDDLE, GestureSlot.SHORTCUT_RIGHT).forEach { slot ->
                            ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
                        }
                    }
                    Group("Gesture service") {
                        ActionRow(
                            "Status: ${if (a11yOn) "on" else "off"}",
                            "For the Lock screen and Notifications gestures.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    }
                }

                SettingsPage.PRAYER -> {
                    Group("Prayer times") {
                        ToggleRow("Prayer ring on home", s.dialMode == DialMode.PRAYER) { on ->
                            vm.updateSettings { it.copy(dialMode = if (on) DialMode.PRAYER else DialMode.OFF) }
                        }
                        ToggleRow("Prayer times", s.prayerOn) { on ->
                            if (on && s.city == null) dialog = SettingsDialog.PRAYER_CITY
                            else vm.updateSettings { it.copy(prayerOn = on) }
                        }
                        ActionRow("City", s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") {
                            dialog = SettingsDialog.PRAYER_CITY
                        }
                        ActionRow("Calculation method", s.prayerMethod.label) { dialog = SettingsDialog.PRAYER_METHOD }
                        ActionRow("Asr time", if (s.asrHanafi) "Hanafi (later)" else "Standard (Shafi'i, Maliki, Hanbali)") {
                            vm.updateSettings { it.copy(asrHanafi = !it.asrHanafi) }
                        }
                        ActionRow("Today's times and Qibla compass") { vm.screen = Screen.PRAYER }
                    }
                    Note("Calculated on the phone from the city's position. Nothing is sent.")
                    Group("Alerts") {
                        ToggleRow("Prayer time alert", s.adhanAlert) { on -> alertToggle(on) { it.copy(adhanAlert = on) } }
                        ToggleRow("Iqama alert", s.iqamaAlert) { on -> alertToggle(on) { it.copy(iqamaAlert = on) } }
                        if (s.adhanAlert || s.iqamaAlert) {
                            ActionRow("Alert sound and vibration", "Android settings") { ctx.safeStart(PrayerAlerts.soundSettings(ctx)) }
                        }
                    }
                    Group("Iqama after the adhan") {
                        Prayer.entries.filter { it.isPrayer }.forEach { p ->
                            Stepper(p.label, "${s.iqamaMin(p)} min",
                                onMinus = { vm.updateSettings { it.copy(iqama = it.iqama + (p to (it.iqamaMin(p) - 5).coerceAtLeast(5))) } },
                                onPlus = { vm.updateSettings { it.copy(iqama = it.iqama + (p to (it.iqamaMin(p) + 5).coerceAtMost(60))) } })
                        }
                    }
                    Note("Set your mosque's iqama times. For an adhan, pick it as the alert sound in Android settings.")
                    Group("Feel") {
                        ActionRow("Edge light", s.edgeStyle.label) { page = SettingsPage.APPEARANCE }
                        ToggleRow("Vibrate on the Qibla compass", s.compassHaptics) { on ->
                            vm.updateSettings { it.copy(compassHaptics = on) }
                        }
                    }
                    Note("The compass ticks every 10°, clicks at N, E, S and W, and taps once when you face the Qibla.")
                }

                SettingsPage.SELFHOSTED -> SelfHostedSettings(vm)

                SettingsPage.TOOLS -> {
                    Group("Tools") {
                        ToggleRow("Show Tools", s.toolsOn) { on -> vm.updateSettings { it.copy(toolsOn = on) } }
                        if (s.toolsOn) ActionRow("Open Tools", "Device, security and text, network") {
                            vm.toolsReturn = Screen.SETTINGS
                            vm.screen = Screen.TOOLS
                        }
                    }
                    Note("Device info, passwords, QR codes and network tools. Open them from the Shelf or a gesture.")
                }

                SettingsPage.WEATHER -> {
                    Group("Weather") {
                        ToggleRow("Weather card on home", s.weatherOn) { on ->
                            vm.updateSettings { it.copy(weatherOn = on) }
                            if (on && s.city == null) dialog = SettingsDialog.CITY else if (on) vm.refreshLive(force = true)
                        }
                        ActionRow("City", s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") { dialog = SettingsDialog.CITY }
                        ActionRow("Units", if (s.fahrenheit) "Fahrenheit (°F)" else "Celsius (°C)") {
                            vm.updateSettings { it.copy(fahrenheit = !it.fahrenheit) }
                        }
                    }
                    Note("From Open-Meteo. Only the city's rounded position is sent, at most every 30 minutes.")
                }

                SettingsPage.GOLD -> {
                    Group("Gold price") {
                        ActionRow("Source", s.goldSource.label) { dialog = SettingsDialog.GOLD_SOURCE }
                        ActionRow("Show in", s.goldCurrency) { dialog = SettingsDialog.CURRENCY }
                        ActionRow("Karat", "${s.goldKarat}K") { dialog = SettingsDialog.KARAT }
                        ActionRow("Unit", if (s.goldPerGram) "Per gram" else "Per troy ounce") {
                            vm.updateSettings { it.copy(goldPerGram = !it.goldPerGram) }
                        }
                    }
                    if (s.goldSource == GoldSource.CUSTOM) {
                        val g = vm.sources.gold
                        var test by remember { mutableStateOf<String?>(null) }
                        Group("Your source") {
                            ActionRow("Address", g.url.ifBlank { "Not set · tap to add" }) {
                                textEdit = TextEdit("Gold price address",
                                    "Any web address that shows a gold price: an API, a JSON file, or a page.",
                                    g.url, "https://…") { vm.setCustomGold(g.copy(url = it)); test = null }
                            }
                            ActionRow("Where the price is", g.path.ifBlank { "The first number" }) {
                                textEdit = TextEdit("Where the price is",
                                    "For JSON, the path to the number, like data.price or rates.XAU or 0.price. " +
                                        "Leave empty to use the first number in the answer.",
                                    g.path, "data.price") { vm.setCustomGold(g.copy(path = it)); test = null }
                            }
                            ActionRow("The price is per", if (g.perGram) "Gram" else "Troy ounce") {
                                vm.setCustomGold(g.copy(perGram = !g.perGram)); test = null
                            }
                            ActionRow("In currency", g.currency) { dialog = SettingsDialog.GOLD_CUSTOM_CURRENCY }
                            ActionRow("Test", test ?: "Fetch it now and show what was read") {
                                test = "Checking…"
                                settingsScope.launch { test = vm.testCustomGold() }
                            }
                        }
                        Note("The price is taken as 24K; karat, unit and the currency you show are worked out from it.")
                    }
                    Group("Widget") {
                        Text("Add \"Stillpoint Gold\" from the Shelf (swipe right on home, Add widget) " +
                            "or from any launcher. Tap the widget to refresh.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Note("Price: " + s.goldSource.detail + ". Exchange rates: European Central Bank. Jewellery adds making charges.")
                }

                SettingsPage.HUB -> {
                    Group("Project Hub") {
                        if (vm.hubConnected()) {
                            ActionRow("Server", vm.hubUrl().orEmpty()) {}
                            ActionRow(
                                "Status",
                                vm.hubError ?: vm.hub?.let { "Connected · updated ${((System.currentTimeMillis() - it.fetchedAt) / 60_000L)} min ago. Tap to update." }
                                    ?: "Connected. Tap to update.",
                            ) { vm.refreshHub(force = true) }
                            ActionRow("Disconnect", "Forget the address and key on this phone") { vm.disconnectHub() }
                        } else {
                            ActionRow("Connect", "Scan the QR code on your hub's \"Connect phone\" page, or type the address and key") {
                                dialog = SettingsDialog.HUB
                            }
                        }
                    }
                    Note("Your own server. Stillpoint connects only to this address. Use https outside your home network.")
                }

                SettingsPage.BACKUP -> {
                    Group("Backup") {
                        ActionRow("Save a backup file", "Settings, notes, tasks, projects, pinned apps, favorites, gestures (no keys or tokens)") {
                            saveBackup.launch("stillpoint-backup-${java.time.LocalDate.now()}.json")
                        }
                        ActionRow("Restore from a file", "Puts a backup's setup in place, then Stillpoint restarts") {
                            openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        }
                    }
                    backupNote?.let { Note(it) }
                    Note("Saved where you choose; nothing is uploaded. Keys and tokens are left out. Widgets aren't included.")
                }

                SettingsPage.NOTIFY -> {
                    val access = remember(vm.resumeTick) { NotifyHub.hasAccess(ctx) }
                    Group("Notification access") {
                        ActionRow(if (access) "Allowed" else "Not allowed · tap to allow",
                            "Read on the phone only. Nothing is kept or sent.") {
                            ctx.safeStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    }
                    if (!access) Note("Switch greyed out? App info › ⋮ › Allow restricted settings.")
                    Group("Show") {
                        ToggleRow("Edge light", s.notifyLight) { on -> vm.updateSettings { it.copy(notifyLight = on) } }
                        ActionRow("Style", s.notifyStyle.label) {
                            vm.updateSettings { it.copy(notifyStyle = NotifyStyle.entries[(it.notifyStyle.ordinal + 1) % NotifyStyle.entries.size]) }
                        }
                        ToggleRow("Signal dot at the top", s.notifyDot) { on -> vm.updateSettings { it.copy(notifyDot = on) } }
                        ToggleRow("Dots on apps", s.notifyAppDots) { on -> vm.updateSettings { it.copy(notifyAppDots = on) } }
                        ActionRow("Send a test notification", "Then go home to see the light and the dots") { NotifyTest.send(ctx) }
                    }
                    Note("Uses the edge style from Appearance, in each app's colour.")
                    Group("Never miss") {
                        ActionRow("Remind again while unread", if (s.remindEvery == 0) "Off" else "Every ${s.remindEvery} min, up to 3 times") {
                            vm.updateSettings { it.copy(remindEvery = when (it.remindEvery) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }) }
                        }
                        s.importantPeople.forEach { name ->
                            ActionRow(name, "Important person · tap to remove") {
                                vm.updateSettings { it.copy(importantPeople = it.importantPeople - name) }
                            }
                        }
                        ActionRow("Add a person", "A name as it shows in their notifications") {
                            textEdit = TextEdit("Important person",
                                "Type the name as it appears in notifications (for example Mum, or a group's name).", "", "Name") { n ->
                                if (n.isNotBlank()) vm.updateSettings { it.copy(importantPeople = (it.importantPeople + n.trim()).distinct()) }
                            }
                        }
                    }
                    Note("Important apps (★) and people shine brighter.")
                    Group("Apps") {
                        val seen = vm.notifySeenApps()
                        if (seen.isEmpty()) Text("Apps appear here after they show a notification.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp))
                        seen.forEach { app -> NotifyAppRow(vm, app) }
                    }
                }

                SettingsPage.FOOTPRINT -> {
                    var f by remember { mutableStateOf(footprint()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(2_000)
                            f = footprint()
                        }
                    }
                    Group("Right now") {
                        ActionRow("Memory", "${f.memMb} MB") {}
                        ActionRow("Processor", "${formatDuration(f.cpuMs).let { if (f.cpuMs < 60_000) "${f.cpuMs / 1000} s" else it }} " +
                            "of work since it started ${formatDuration(f.upMs)} ago " +
                            "(${String.format(java.util.Locale.US, "%.2f", f.cpuMs * 100.0 / f.upMs.coerceAtLeast(1))}% of the time)") {}
                    }
                    Group("Battery") {
                        ActionRow("Battery use", "Android keeps this figure. Tap to see Stillpoint's page.") {
                            ctx.safeStart(
                                Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").putExtra("package_name", ctx.packageName),
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    }
                    Note("Android shows battery use on its own page. Stillpoint works only while open and wakes briefly for alerts and syncs.")
                }

                SettingsPage.SOURCES -> {
                    Group("Addresses") {
                        SourceKey.entries.forEach { k ->
                            val custom = vm.sources.isCustom(k)
                            ActionRow(k.label, (if (custom) "Yours · " else "Default · ") +
                                vm.sources.url(k).removePrefix("https://").removePrefix("http://")) {
                                textEdit = TextEdit(k.label, k.help, if (custom) vm.sources.url(k) else "", k.default,
                                    onReset = { vm.setSource(k, null) }) { vm.setSource(k, it) }
                            }
                        }
                    }
                    Group("Set on their own pages") {
                        ActionRow("Gold price", s.goldSource.label) { page = SettingsPage.GOLD }
                        ActionRow("Calendar link", vm.icsUrl()?.let { maskUrl(it) } ?: "Not set") { page = SettingsPage.CALENDAR }
                        ActionRow("Project Hub", vm.hubUrl() ?: "Not connected") { page = SettingsPage.HUB }
                    }
                    Note("Leave empty for the default. Prayer times, Qibla and dates are calculated on the phone.")
                }

                SettingsPage.LOCK -> {
                    Group("Lock screen") {
                        ToggleRow("Show info on the lock screen", s.lockOn) { on ->
                            if (on && !LockNotification.canPost(ctx)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { vm.updateSettings { it.copy(lockOn = on) }; Refresh.all(ctx) }
                        }
                    }
                    Group("Show") {
                        ToggleRow("Next prayer with countdown", s.lockPrayer) { on ->
                            vm.updateSettings { it.copy(lockPrayer = on) }; Refresh.all(ctx)
                        }
                        ToggleRow("Hijri date", s.lockHijri) { on -> vm.updateSettings { it.copy(lockHijri = on) }; Refresh.all(ctx) }
                        ToggleRow("Tamil date", s.lockTamil) { on -> vm.updateSettings { it.copy(lockTamil = on) }; Refresh.all(ctx) }
                    }
                    Note("A silent lock-screen notification. Android keeps the countdown, so nothing runs in the background.")
                }

                SettingsPage.TASKS -> {
                    SourcePicker(TaskSource.entries, s.tasksSource, { it.label }, { src ->
                        src.detail + when {
                            src.appPackage != null && !vm.providerInstalled(src) -> " Not installed: tap to get it."
                            src == TaskSource.HUB && !vm.hubConnected() -> " Connect it under Extras first."
                            else -> ""
                        }
                    }) { src ->
                        when {
                            src == TaskSource.HUB && !vm.hubConnected() -> page = SettingsPage.HUB
                            src.appPackage != null && !vm.providerInstalled(src) -> ctx.safeStart(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${src.appPackage}")),
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/${src.appPackage}/")),
                            )
                            src.readPermission != null && !vm.providerPermitted(src) -> {
                                pendingTaskSource = src
                                taskPermission.launch(listOfNotNull(src.readPermission, src.writePermission).toTypedArray())
                            }
                            else -> vm.setTaskSource(src)
                        }
                    }
                    when (s.tasksSource) {
                        TaskSource.PHONE -> Note("Stored on this phone. Swipe a task left to delete it.")
                        TaskSource.HUB -> SyncGroup(vm, SyncFeature.TASKS, Sync.HUB, s.tasksSync) { dialog = SettingsDialog.SYNC_TASKS }
                        else -> Group("Sync") {
                            Text("${s.tasksSource.label} keeps itself in sync, for example with DAVx5. Stillpoint reads its open " +
                                "tasks when you open the Shelf; ticking one there marks it done in ${s.tasksSource.label}.",
                                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                            ActionRow("Read now", vm.providerError ?: "${vm.providerTasks.size} open tasks") { vm.loadProviderTasks() }
                        }
                    }
                    Note("Add Tasks to any shelf: Shelf › Add › Stillpoint cards.")
                }

                SettingsPage.CALENDAR -> {
                    SourcePicker(CalendarSource.entries, s.calendarSource, { it.label }, { src ->
                        src.detail + if (src == CalendarSource.HUB && !vm.hubConnected()) " Connect it under Extras first." else ""
                    }) { src ->
                        when {
                            src == CalendarSource.HUB && !vm.hubConnected() -> page = SettingsPage.HUB
                            src == CalendarSource.ICS && vm.icsUrl() == null -> {
                                vm.setCalendarSource(src)
                                dialog = SettingsDialog.ICS_URL
                            }
                            else -> vm.setCalendarSource(src)
                        }
                    }
                    when (s.calendarSource) {
                        CalendarSource.PHONE -> Note("Android syncs the accounts on this phone by itself. Stillpoint reads today's events.")
                        CalendarSource.HUB -> SyncGroup(vm, SyncFeature.CALENDAR, Sync.HUB, s.calendarSync) { dialog = SettingsDialog.SYNC_CALENDAR }
                        CalendarSource.ICS -> {
                            Group("Calendar link") {
                                ActionRow("Link", vm.icsUrl()?.let { maskUrl(it) } ?: "Not set · tap to add") { dialog = SettingsDialog.ICS_URL }
                            }
                            SyncGroup(vm, SyncFeature.CALENDAR, Sync.ICS, s.calendarSync) { dialog = SettingsDialog.SYNC_CALENDAR }
                        }
                    }
                    Group("Display") {
                        ToggleRow("Today's calendar on home", s.showAgenda) { on ->
                            val needsPermission = s.calendarSource == CalendarSource.PHONE &&
                                ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
                            if (on && needsPermission) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
                        }
                    }
                }

                SettingsPage.PRIVACY -> {
                    Group("Permissions") {
                        ActionRow(
                            "Usage access: ${if (vm.hasUsageAccess) "allowed" else "not allowed"}",
                            "Screen time, most-used apps and data usage. Data stays on the phone.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        ActionRow(
                            "Gesture service: ${if (a11yOn) "on" else "off"}",
                            "Only for the Lock screen and Notifications gestures.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    }
                    Group("Data") {
                        ActionRow("Data usage", "Per-app Wi-Fi and mobile data, from Android's own records") { vm.screen = Screen.DATA }
                    }
                }

                SettingsPage.UPDATES -> Group("GitHub releases") { UpdateSection(available) }

                SettingsPage.SYSTEM -> Group("System") {
                    SettingsPage.entries
                        .filter { it.parent == SettingsPage.SYSTEM && (it != SettingsPage.UPDATES || Updater.AVAILABLE) }
                        .forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                            MenuRow(p.icon, p.title, p.summary) { page = p }
                        }
                }

                SettingsPage.ABOUT -> {
                    Group("Stillpoint Launcher") {
                        ActionRow("Version", BuildConfig.VERSION_NAME) {}
                        ActionRow("App info", "Android's page for Stillpoint: permissions, notifications, battery, storage") {
                            ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }
                        ActionRow("Made by", "cloudit24 · github.com/cloudit24") {
                            ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cloudit24")))
                        }
                        ActionRow("Source code", "github.com/cloudit24/stillpoint · GPL-3.0") {
                            ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cloudit24/stillpoint")))
                        }
                        ActionRow("Set as default home app", "Opens the system home app picker") {
                            ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                    var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
                    crash?.let { (at, text) ->
                        Group("Last problem") {
                            ActionRow("Stillpoint closed unexpectedly", "${formatAgo(at)} · tap to share the report") {
                                ctx.safeStart(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "Stillpoint crash report")
                                        .putExtra(Intent.EXTRA_TEXT, text),
                                    "Share crash report",
                                ))
                            }
                            ActionRow("Clear report") { CrashLog.clear(ctx); crash = null }
                        }
                        Note("Kept only on this phone. Nothing is sent unless you share it.")
                    }
                    Group("Privacy") {
                        Text("No analytics and no accounts. The internet is used only for features you switch on: " +
                            "weather, gold price, public IP, city search, your own Project Hub" +
                            (if (Updater.AVAILABLE) " and update checks. " else ". ") +
                            "Prayer times, Qibla, Hijri and Tamil dates are calculated on the phone.",
                            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }

    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {
                    // Start fresh so every screen reads the restored setup.
                    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    if (launch?.component != null) ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
                    Runtime.getRuntime().exit(0)
                }) { Text("Restart") }
            },
            title = { Text("Restored") },
            text = { Text("Your setup is back. Stillpoint restarts to load it.") },
        )
    }

    textEdit?.let { t ->
        TextSettingDialog(t, onDismiss = { textEdit = null })
    }

    when (dialog) {
        SettingsDialog.ACCENT -> AccentDialog(s.accent, onDismiss = { dialog = null }) { a ->
            vm.updateSettings { it.copy(accent = a) }
        }
        SettingsDialog.HOME_STYLE -> ChoiceDialog("Home style", HomeStyle.entries, { it.label }, onDismiss = { dialog = null }) { h ->
            vm.updateSettings { it.copy(homeStyle = h) }
        }
        SettingsDialog.DRAWER_START -> ChoiceDialog("Open the app list on", DrawerTab.entries,
            { it.label.replaceFirstChar { c -> c.uppercase() } }, onDismiss = { dialog = null }) { t ->
            vm.updateSettings { it.copy(drawerStart = t) }
        }
        SettingsDialog.CLOCK -> ChoiceDialog("Clock style", ClockStyle.entries, { it.label }, onDismiss = { dialog = null }) { c ->
            vm.updateSettings { it.copy(clockStyle = c) }
        }
        SettingsDialog.GOLD_SOURCE -> ChoiceDialog("Gold price", GoldSource.entries, { it.label }, onDismiss = { dialog = null }) { g ->
            vm.updateSettings { it.copy(goldSource = g) }
            vm.refreshLive(force = true)
        }
        SettingsDialog.CURRENCY -> ChoiceDialog("Currency", GOLD_CURRENCIES, { it }, onDismiss = { dialog = null }) { c ->
            vm.updateSettings { it.copy(goldCurrency = c) }
            vm.refreshLive(force = true)
        }
        SettingsDialog.KARAT -> ChoiceDialog("Karat", listOf(24, 22, 21, 18), { "${it}K" }, onDismiss = { dialog = null }) { k ->
            vm.updateSettings { it.copy(goldKarat = k) }
        }
        SettingsDialog.CITY -> CitySearchDialog(vm, onDismiss = { dialog = null }) { vm.setCity(it, enableWeather = false) }
        SettingsDialog.PRAYER_CITY -> CitySearchDialog(vm, onDismiss = { dialog = null }) { c ->
            vm.setCity(c, enableWeather = false)
            vm.updateSettings { it.copy(prayerOn = true) }
        }
        SettingsDialog.PRAYER_METHOD -> ChoiceDialog("Calculation method", PrayerMethod.entries, { it.label },
            onDismiss = { dialog = null }) { pm -> vm.updateSettings { it.copy(prayerMethod = pm) } }
        SettingsDialog.HUB -> HubConnectDialog(vm, onDismiss = { dialog = null })
        SettingsDialog.SYNC_TASKS, SettingsDialog.SYNC_CALENDAR, SettingsDialog.SYNC_PROJECTS -> {
            val f = when (dialog) {
                SettingsDialog.SYNC_TASKS -> SyncFeature.TASKS
                SettingsDialog.SYNC_CALENDAR -> SyncFeature.CALENDAR
                else -> SyncFeature.PROJECTS
            }
            ChoiceDialog("Sync every", SYNC_INTERVALS, { intervalLabel(it) }, onDismiss = { dialog = null }) { m ->
                vm.setSync(f) { it.copy(everyMin = m) }
            }
        }
        SettingsDialog.EDGE_STYLE -> ChoiceDialog("Edge light", EdgeStyle.entries, { it.label }, onDismiss = { dialog = null }) { e ->
            vm.updateSettings { it.copy(edgeStyle = e) }
        }
        SettingsDialog.ICON_TINT -> ChoiceDialog("Icon colours", IconTint.entries, { it.label }, onDismiss = { dialog = null }) { t ->
            vm.updateSettings { it.copy(iconTint = t) }
        }
        SettingsDialog.DIAL_MODE -> ChoiceDialog("Ring by the headline", DialMode.entries, { it.label }, onDismiss = { dialog = null }) { m ->
            vm.updateSettings { it.copy(dialMode = m) }
        }
        SettingsDialog.FONT -> ChoiceDialog("Font", AppFont.entries, { it.label }, onDismiss = { dialog = null }) { f ->
            vm.updateSettings { it.copy(font = f) }
        }
        SettingsDialog.GOLD_CUSTOM_CURRENCY -> ChoiceDialog("Your source's currency", GOLD_CURRENCIES, { it }, onDismiss = { dialog = null }) { c ->
            vm.setCustomGold(vm.sources.gold.copy(currency = c))
        }
        SettingsDialog.ICS_URL -> IcsDialog(vm.icsUrl().orEmpty(), onDismiss = { dialog = null }) { vm.setIcsUrl(it) }
        null -> Unit
    }

    picking?.let { slot ->
        TargetPicker(vm, title = slot.label, onPick = { vm.setGesture(slot, it); picking = null }, onDismiss = { picking = null })
    }
}

private enum class SettingsDialog {
    ACCENT, HOME_STYLE, DRAWER_START, CLOCK, GOLD_SOURCE, CURRENCY, KARAT, CITY, PRAYER_CITY, PRAYER_METHOD, HUB,
    SYNC_TASKS, SYNC_CALENDAR, SYNC_PROJECTS, ICS_URL, EDGE_STYLE, ICON_TINT, DIAL_MODE, FONT, GOLD_CUSTOM_CURRENCY,
}

/** Type in the hub address and app key (from the hub's "Connect phone" page). Tested before it's saved. */
@Composable
fun HubConnectDialog(vm: LauncherViewModel, onDismiss: () -> Unit, url0: String = "", key0: String = "") {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(url0) }
    var key by remember { mutableStateOf(key0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val connect = {
        if (!busy) {
            busy = true
            error = null
            scope.launch {
                error = vm.connectHub(url, key)
                busy = false
                if (error == null) onDismiss()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { connect() }) { Text(if (busy) "Checking…" else "Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Connect Project Hub") },
        text = {
            Column {
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("Address") },
                    placeholder = { Text("https://hub.example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true, label = { Text("App key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    modifier = Modifier.padding(top = 8.dp))
                if (url.startsWith("http://") && !url.isLocalAddress()) {
                    Text("This address isn't encrypted. Use https unless the hub is on your home network.",
                        color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                error?.let { Text(it, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
    )
}

/** Home-network addresses (192.168.x, 10.x, 172.16-31.x, .local, localhost). */
private fun String.isLocalAddress(): Boolean {
    val host = removePrefix("http://").substringBefore('/').substringBefore(':')
    return host == "localhost" || host.endsWith(".local") || host.startsWith("192.168.") || host.startsWith("10.") ||
        Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(host)
}

/** A titled rounded card holding related rows. */
@Composable
internal fun Group(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

/** Main-menu row: tinted icon, title, one-line summary, chevron. */
@Composable
private fun MenuRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val accent = Accent
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, fontSize = 16.sp)
            Text(summary, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

/** A row of pages side by side: icon above the name. */
@Composable
private fun TileRow(pages: List<SettingsPage>, onClick: (SettingsPage) -> Unit) {
    val accent = Accent
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        pages.forEach { p ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onClick(p) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(p.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Text(p.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun Note(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("−", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onMinus).padding(horizontal = 14.dp))
        Text(value, fontSize = 17.sp)
        Text("+", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onPlus).padding(horizontal = 14.dp))
    }
}

/** The Windows Phone accent palette as a grid of squares. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentDialog(current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Accent colour") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCENTS.forEach { a ->
                    Box(
                        Modifier.size(52.dp).background(Color(a.argb))
                            .then(if (a.argb == current) Modifier.border(3.dp, Ink) else Modifier)
                            .clickable { onPick(a.argb); onDismiss() },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(a.name.lowercase(), color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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

/** Search Open-Meteo's place names. */
@Composable
private fun CitySearchDialog(vm: LauncherViewModel, onDismiss: () -> Unit, onPick: (City) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val search = {
        if (query.trim().length >= 2 && !searching) {
            searching = true
            scope.launch {
                results = vm.searchCities(query)
                searching = false
                searched = true
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { search() }) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose city") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("City name, e.g. Dubai") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                if (searching) Text("Searching…", color = Muted, modifier = Modifier.padding(top = 8.dp))
                if (searched && !searching && results.isEmpty()) {
                    Text("No match. Check the spelling or your connection.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(results) { c ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(c); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(c.name, fontSize = 17.sp)
                            Text(c.country, color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
    )
}

/** Built-in actions first, then every app. */
@Composable
private fun TargetPicker(vm: LauncherViewModel, title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(HomeAction.entries.filter { it != HomeAction.TOOLS || vm.settings.toolsOn }) { a ->
                    Text(
                        a.label + if (a.needsGestureService) "  (gesture service)" else "",
                        fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(GestureTarget.action(a)) }.padding(vertical = 10.dp),
                    )
                }
                item { Text("Open an app", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                items(vm.visibleApps(), key = { it.key }) { app ->
                    Text(
                        app.label,
                        fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(GestureTarget.app(app.key)) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
    )
}

/** Check GitHub, then download and hand the APK to the system installer. */
@Composable
private fun UpdateSection(initial: UpdateCheck? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = BuildConfig.VERSION_NAME
    var result by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    when (val r = result) {
        is UpdateCheck.Available -> ActionRow(
            "Install version ${r.version}",
            if (progress >= 0) "Downloading $progress%" else "You have $current. Tap to download from GitHub and install.",
        ) {
            if (!busy) {
                busy = true
                progress = 0
                error = null
                scope.launch {
                    error = Updater.downloadAndInstall(ctx, r.apkUrl) { progress = it }
                    if (error != null) progress = -1
                    busy = false
                }
            }
        }
        else -> ActionRow(
            if (busy) "Checking…" else "Check for updates",
            when (r) {
                is UpdateCheck.UpToDate -> "You have the latest version ($current)."
                is UpdateCheck.Failed -> r.reason
                else -> "Version $current. Downloads come from github.com/cloudit24/stillpoint."
            },
        ) {
            if (!busy) {
                busy = true
                scope.launch {
                    result = Updater.check(current)
                    busy = false
                }
            }
        }
    }
    error?.let { Text(it, color = Accent, fontSize = 13.sp) }
}

/** "Where does it come from?" list with a tick on the chosen source. */
@Composable
private fun <T> SourcePicker(options: List<T>, selected: T, label: (T) -> String, detail: (T) -> String, onPick: (T) -> Unit) {
    Group("Source") {
        options.forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label(o), fontSize = 16.sp, color = if (o == selected) Accent else Ink)
                    Text(detail(o), color = Muted, fontSize = 13.sp)
                }
                if (o == selected) {
                    Icon(Icons.Outlined.Done, contentDescription = "Selected", tint = Accent, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

/** Auto sync, interval, Wi-Fi only, last sync and Sync now for a page that uses an outside source. */
@Composable
private fun SyncGroup(vm: LauncherViewModel, feature: SyncFeature, source: String, cfg: SyncConfig, onEvery: () -> Unit) {
    val tick = vm.syncTick
    val last = remember(tick, source) { vm.syncLast(source) }
    val error = remember(tick, source) { vm.syncError(source) }
    Group("Sync") {
        ToggleRow("Auto sync", cfg.auto) { on -> vm.setSync(feature) { it.copy(auto = on) } }
        ActionRow("Sync every", intervalLabel(cfg.everyMin)) { onEvery() }
        ToggleRow("Only on Wi-Fi", cfg.wifiOnly) { on -> vm.setSync(feature) { it.copy(wifiOnly = on) } }
        ActionRow("Last sync", (if (last == 0L) "Never" else formatAgo(last)) + (error?.let { " · $it" } ?: "")) {}
        ActionRow(if (vm.syncing) "Syncing…" else "Sync now") { vm.syncNow(feature) }
    }
}

private fun formatAgo(ms: Long): String {
    val min = ((System.currentTimeMillis() - ms) / 60_000L).coerceAtLeast(0)
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 1440 -> "${min / 60} h ago"
        else -> "${min / 1440} days ago"
    }
}

/** The link is private, so only its host is shown. */
private fun maskUrl(u: String): String = runCatching { Uri.parse(u).host }.getOrNull()?.let { "$it/…" } ?: "Set"

@Composable
private fun IcsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(url); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Calendar link") },
        text = {
            Column {
                Text("Paste your calendar's private .ics address. Outlook: Settings, Calendar, Shared calendars, " +
                    "Publish a calendar. Google: the calendar's settings, Secret address in iCal format.",
                    color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://…/calendar.ics") }, modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

private class Footprint(val memMb: Int, val cpuMs: Long, val upMs: Long)

/** This app's own memory (as Android counts it) and processor time since it started. */
private fun footprint(): Footprint {
    val mi = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
    return Footprint(
        memMb = mi.totalPss / 1024,
        cpuMs = android.os.Process.getElapsedCpuTime(),
        upMs = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime(),
    )
}

/** A one-line text setting: what it is, a short help line, and an optional way back to the default. */
private class TextEdit(
    val title: String,
    val help: String,
    val initial: String,
    val placeholder: String,
    val onReset: (() -> Unit)? = null,
    val onSave: (String) -> Unit,
)

@Composable
private fun TextSettingDialog(t: TextEdit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(t.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { t.onSave(text.trim()); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                t.onReset?.let { reset -> TextButton(onClick = { reset(); onDismiss() }) { Text("Default") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = { Text(t.title) },
        text = {
            Column {
                Text(t.help, color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text(t.placeholder) }, modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

/** One app in the notification light: its colour (tap to change), important star, and whether it lights up. */
@Composable
private fun NotifyAppRow(vm: LauncherViewModel, app: AppEntry) {
    val s = vm.settings
    val pkg = app.packageName
    val color = vm.notifyColor(pkg)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(color).clickable {
            val i = ACCENTS.indexOfFirst { it.argb == s.notifyColors[pkg] }
            val next = ACCENTS[(i + 1) % ACCENTS.size].argb
            vm.updateSettings { it.copy(notifyColors = it.notifyColors + (pkg to next)) }
        })
        Text(app.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 14.dp))
        val important = pkg in s.importantApps
        Text(if (important) "★" else "☆", color = if (important) Accent else Muted, fontSize = 20.sp,
            modifier = Modifier.clip(CircleShape).clickable {
                vm.updateSettings { it.copy(importantApps = if (important) it.importantApps - pkg else it.importantApps + pkg) }
            }.padding(horizontal = 10.dp))
        Switch(checked = pkg !in s.notifyOff, onCheckedChange = { on ->
            vm.updateSettings { it.copy(notifyOff = if (on) it.notifyOff - pkg else it.notifyOff + pkg) }
        })
    }
}
