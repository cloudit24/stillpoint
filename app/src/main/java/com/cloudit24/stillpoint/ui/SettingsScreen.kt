package com.cloudit24.stillpoint.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.outlined.Edit
import com.cloudit24.stillpoint.data.Lang
import android.app.Activity
import android.content.ContextWrapper
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import androidx.compose.material.icons.outlined.Person
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
private enum class SettingsPage(@StringRes val section: Int, @StringRes val title: Int, @StringRes val summary: Int, val icon: ImageVector) {
    APPEARANCE(R.string.sec_personalization, R.string.pg_appearance, R.string.pg_appearance_sum, Icons.Outlined.Face),
    HOME(R.string.sec_personalization, R.string.pg_home, R.string.pg_home_sum, Icons.Outlined.Home),
    APPS(R.string.sec_personalization, R.string.pg_apps, R.string.pg_apps_sum, Icons.Outlined.Menu),
    GESTURES(R.string.sec_personalization, R.string.pg_gestures, R.string.pg_gestures_sum, Icons.Outlined.ThumbUp),
    NOTIFY(R.string.sec_personalization, R.string.pg_notify, R.string.pg_notify_sum, Icons.Outlined.Star),
    SENIOR(R.string.sec_personalization, R.string.pg_senior, R.string.pg_senior_sum, Icons.Outlined.Person),
    LANGUAGE(R.string.sec_personalization, R.string.pg_language, R.string.pg_language_sum, Icons.Outlined.Edit),
    LOCK(R.string.sec_personalization, R.string.pg_lock, R.string.pg_lock_sum, Icons.Outlined.Notifications),
    TASKS(R.string.sec_productivity, R.string.pg_tasks, R.string.pg_tasks_sum, Icons.Outlined.Done),
    CALENDAR(R.string.sec_productivity, R.string.pg_calendar, R.string.pg_calendar_sum, Icons.Outlined.DateRange),
    PRAYER(R.string.sec_extras, R.string.pg_prayer, R.string.pg_prayer_sum, Icons.Outlined.Place),
    WEATHER(R.string.sec_extras, R.string.pg_weather, R.string.pg_weather_sum, Icons.Outlined.LocationOn),
    GOLD(R.string.sec_extras, R.string.pg_gold, R.string.pg_gold_sum, Icons.Outlined.Star),
    HUB(R.string.sec_extras, R.string.pg_hub, R.string.pg_hub_sum, Icons.Outlined.CheckCircle),
    SELFHOSTED(R.string.sec_extras, R.string.pg_selfhosted, R.string.pg_selfhosted_sum, Icons.Outlined.Home),
    TOOLS(R.string.sec_extras, R.string.pg_tools, R.string.pg_tools_sum, Icons.Outlined.Build),
    PRIVACY(R.string.sec_system, R.string.pg_privacy, R.string.pg_privacy_sum, Icons.Outlined.Lock),
    BACKUP(R.string.sec_system, R.string.pg_backup, R.string.pg_backup_sum, Icons.Outlined.Send),
    FOOTPRINT(R.string.sec_system, R.string.pg_footprint, R.string.pg_footprint_sum, Icons.Outlined.Settings),
    SOURCES(R.string.sec_system, R.string.pg_sources, R.string.pg_sources_sum, Icons.Outlined.Share),
    UPDATES(R.string.sec_system, R.string.pg_updates, R.string.pg_updates_sum, Icons.Outlined.Refresh),
    SYSTEM(R.string.sec_system, R.string.pg_system, R.string.pg_system_sum, Icons.Outlined.Settings),
    ABOUT(R.string.sec_system, R.string.pg_about, R.string.pg_about_sum, Icons.Outlined.Info),
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
    var seniorPick by remember { mutableStateOf<Int?>(null) }
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
                Text(stringResource(R.string.s_settings), fontSize = 34.sp, fontWeight = FontWeight.Light,
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
                    Text(stringResource(current.title), fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 8.dp))
                }
            }

            when (current) {
                null -> {
                    SettingsPage.entries
                        .filter { it.parent == null }
                        .groupBy { it.section }
                        .forEach { (section, pages) ->
                            Group(stringResource(section)) {
                                if (section == R.string.sec_productivity) TileRow(pages) { page = it }
                                else pages.forEachIndexed { i, p ->
                                    if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                                    MenuRow(p.icon, stringResource(p.title), stringResource(p.summary)) { page = p }
                                }
                            }
                        }
                    Text("Stillpoint Launcher ${BuildConfig.VERSION_NAME} · by cloudit24", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = TextAlign.Center)
                }

                SettingsPage.APPEARANCE -> {
                    Group(stringResource(R.string.s_theme)) {
                        Row(Modifier.fillMaxWidth().clickable { dialog = SettingsDialog.ACCENT }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.s_accent_colour), fontSize = 16.sp)
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
                        ActionRow(stringResource(R.string.s_icon_colours), s.iconTint.label) { dialog = SettingsDialog.ICON_TINT }
                        ActionRow(stringResource(R.string.s_font), s.font.label) { dialog = SettingsDialog.FONT }
                    }
                    Group(stringResource(R.string.s_apps)) {
                        ActionRow(stringResource(R.string.s_home_apps_as), s.homeStyle.label) { dialog = SettingsDialog.HOME_STYLE }
                        Stepper("App size on home", "${s.homeSize}",
                            onMinus = { vm.updateSettings { it.copy(homeSize = (it.homeSize - 2).coerceAtLeast(16)) } },
                            onPlus = { vm.updateSettings { it.copy(homeSize = (it.homeSize + 2).coerceAtMost(40)) } })
                        ToggleRow(stringResource(R.string.s_icons_in_lists), s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }
                    }
                    Group(stringResource(R.string.s_edge_light)) {
                        ActionRow(stringResource(R.string.s_style), s.edgeStyle.label) { dialog = SettingsDialog.EDGE_STYLE }
                        if (s.edgeStyle == EdgeStyle.CURVED || s.edgeStyle == EdgeStyle.FLAT) {
                            ActionRow(stringResource(R.string.s_side), if (s.edgeRight) "Right" else "Left") {
                                vm.updateSettings { it.copy(edgeRight = !it.edgeRight) }
                            }
                        }
                        if (s.edgeStyle != EdgeStyle.OFF) {
                            ActionRow(stringResource(R.string.s_brightness), listOf("Low", "Medium", "High")[s.edgeBright.coerceIn(1, 3) - 1]) {
                                vm.updateSettings { it.copy(edgeBright = it.edgeBright % 3 + 1) }
                            }
                            Stepper("Blink before a prayer ends", "${s.edgeWarnMin} min",
                                onMinus = { vm.updateSettings { it.copy(edgeWarnMin = (it.edgeWarnMin - 5).coerceAtLeast(5)) } },
                                onPlus = { vm.updateSettings { it.copy(edgeWarnMin = (it.edgeWarnMin + 5).coerceAtMost(60)) } })
                        }
                    }
                    Note(s.edgeStyle.detail + if (s.edgeStyle == EdgeStyle.OFF) "" else " Bright: the next prayer. Dim: what's left of this one." +
                        if (!s.prayerOn || s.city == null) " Needs Islamic prayer with a city." else "")
                    Group(stringResource(R.string.s_motion)) {
                        ToggleRow(stringResource(R.string.s_animations), s.edgeMotion) { on -> vm.updateSettings { it.copy(edgeMotion = on) } }
                    }
                    Note(stringResource(R.string.s_spark_blink_and_pulse_they_run))
                }

                SettingsPage.HOME -> {
                    var worldOpen by remember { mutableStateOf(false) }
                    Group(stringResource(R.string.s_gestures)) {
                        SettingsPage.GESTURES.let { MenuRow(it.icon, stringResource(it.title), stringResource(it.summary)) { page = it } }
                    }
                    Group(stringResource(R.string.s_headline)) {
                        ToggleRow(stringResource(R.string.s_screen_time_card), s.showUsage) { on -> vm.updateSettings { it.copy(showUsage = on) } }
                        ActionRow(stringResource(R.string.s_ring_beside_it), s.dialMode.label) { dialog = SettingsDialog.DIAL_MODE }
                    }
                    Group(stringResource(R.string.s_calendars)) {
                        ToggleRow(stringResource(R.string.s_hijri), s.hijriOn) { on -> vm.updateSettings { it.copy(hijriOn = on) } }
                        if (s.hijriOn) {
                            Stepper("Hijri adjustment (days)", if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}",
                                onMinus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust - 1).coerceAtLeast(-2)) } },
                                onPlus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust + 1).coerceAtMost(2)) } })
                        }
                        ToggleRow(stringResource(R.string.s_indian_national_saka), s.sakaOn) { on -> vm.updateSettings { it.copy(sakaOn = on) } }
                        ToggleRow(stringResource(R.string.s_malayalam_kollavarsham), s.malayalamOn) { on -> vm.updateSettings { it.copy(malayalamOn = on) } }
                        ToggleRow(stringResource(R.string.s_tamil), s.tamilOn) { on -> vm.updateSettings { it.copy(tamilOn = on) } }
                    }
                    Note(stringResource(R.string.s_shown_in_the_headline_and_on))
                    Group(stringResource(R.string.s_cards_under_the_headline)) {
                        InfoPanel.entries.filter { it != InfoPanel.PRAYER }.forEach { panel ->
                            ToggleRow(panel.label, panel in s.infoPanels) { on ->
                                vm.updateSettings { it.copy(infoPanels = if (on) it.infoPanels + panel else it.infoPanels - panel) }
                                if (on && panel == InfoPanel.AGENDA) vm.refresh()
                                if (on && panel == InfoPanel.WORLD && s.worldClocks.isEmpty()) worldOpen = true
                            }
                        }
                        if (InfoPanel.WORLD in s.infoPanels) {
                            ActionRow(stringResource(R.string.s_world_clock_cities),
                                s.worldClocks.joinToString(", ") { it.substringBefore("|") }.ifEmpty { "Choose up to three" }) { worldOpen = true }
                        }
                    }
                    Note(stringResource(R.string.s_swipe_between_cards_on_home_weather))
                    if (worldOpen) {
                        WorldCitiesDialog(s.worldClocks, onDismiss = { worldOpen = false }) { list ->
                            vm.updateSettings { it.copy(worldClocks = list) }
                            worldOpen = false
                        }
                    }
                    Group(stringResource(R.string.s_footer)) {
                        ToggleRow(stringResource(R.string.s_network_speed_and_memory), s.showStats) { on -> vm.updateSettings { it.copy(showStats = on) } }
                        ToggleRow(stringResource(R.string.s_local_ip_address), s.showLocalIp) { on -> vm.updateSettings { it.copy(showLocalIp = on) } }
                        ToggleRow(stringResource(R.string.s_public_ip_address), s.publicIpOn) { on -> vm.updateSettings { it.copy(publicIpOn = on) } }
                    }
                    Note(stringResource(R.string.s_public_ip_from_api_ipify_org))
                    Group(stringResource(R.string.s_apps)) {
                        ToggleRow(stringResource(R.string.s_recently_used_24_h), s.showRecent) { on -> vm.updateSettings { it.copy(showRecent = on) } }
                        ToggleRow(stringResource(R.string.s_most_used_today), s.homeMode == HomeMode.AUTO) { on ->
                            vm.updateSettings { it.copy(homeMode = if (on) HomeMode.AUTO else HomeMode.PINNED) }
                        }
                        Stepper("Apps on home", "${s.homeCount}",
                            onMinus = { vm.updateSettings { it.copy(homeCount = (it.homeCount - 1).coerceAtLeast(3)) } },
                            onPlus = { vm.updateSettings { it.copy(homeCount = (it.homeCount + 1).coerceAtMost(9)) } })
                    }
                    Note(stringResource(R.string.s_long_press_an_app_to_pin))
                    Group(stringResource(R.string.s_widgets)) {
                        Text("Swipe right on home for the Shelf. Add more shelves with +, and put Stillpoint cards " +
                            "(notes, tasks, calendar, countdown...) or any app's widgets on them. Stillpoint's own widgets: Stillpoint Widget " +
                            "(clock and dates), Stillpoint Prayer (times, countdown, moon) and Stillpoint Gold. " +
                            "They work in any launcher too.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                }

                SettingsPage.APPS -> {
                    Group(stringResource(R.string.s_behaviour)) {
                        ActionRow(stringResource(R.string.s_open_the_app_list_on), s.drawerStart.label.replaceFirstChar { it.uppercase() }) {
                            dialog = SettingsDialog.DRAWER_START
                        }
                    }
                    Group(stringResource(R.string.s_hidden_apps)) {
                        val hidden = vm.apps.filter { it.key in s.hidden }
                        if (hidden.isEmpty()) Text(stringResource(R.string.s_none_long_press_an_app_in), color = Muted,
                            fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        hidden.forEach { app ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(app.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.s_unhide), color = Accent, modifier = Modifier.clickable { vm.unhide(app.key) }.padding(8.dp))
                            }
                        }
                    }
                }

                SettingsPage.GESTURES -> {
                    Group(stringResource(R.string.s_swipes_and_taps)) {
                        listOf(GestureSlot.SWIPE_LEFT, GestureSlot.SWIPE_RIGHT, GestureSlot.SWIPE_UP, GestureSlot.SWIPE_DOWN,
                            GestureSlot.DOUBLE_TAP).forEach { slot ->
                            ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
                        }
                    }
                    Group(stringResource(R.string.s_bottom_shortcuts)) {
                        listOf(GestureSlot.SHORTCUT_LEFT, GestureSlot.SHORTCUT_MIDDLE, GestureSlot.SHORTCUT_RIGHT).forEach { slot ->
                            ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
                        }
                    }
                    Group(stringResource(R.string.s_gesture_service)) {
                        ActionRow(
                            "Status: ${if (a11yOn) "on" else "off"}",
                            "For the Lock screen and Notifications gestures.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    }
                }

                SettingsPage.PRAYER -> {
                    Group(stringResource(R.string.s_prayer_times)) {
                        ToggleRow(stringResource(R.string.s_prayer_ring_on_home), s.dialMode == DialMode.PRAYER) { on ->
                            vm.updateSettings { it.copy(dialMode = if (on) DialMode.PRAYER else DialMode.OFF) }
                        }
                        ToggleRow(stringResource(R.string.s_prayer_times), s.prayerOn) { on ->
                            if (on && s.city == null) dialog = SettingsDialog.PRAYER_CITY
                            else vm.updateSettings { it.copy(prayerOn = on) }
                        }
                        ActionRow(stringResource(R.string.s_city), s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") {
                            dialog = SettingsDialog.PRAYER_CITY
                        }
                        ActionRow(stringResource(R.string.s_calculation_method), s.prayerMethod.label) { dialog = SettingsDialog.PRAYER_METHOD }
                        ActionRow(stringResource(R.string.s_asr_time), if (s.asrHanafi) "Hanafi (later)" else "Standard (Shafi'i, Maliki, Hanbali)") {
                            vm.updateSettings { it.copy(asrHanafi = !it.asrHanafi) }
                        }
                        ActionRow(stringResource(R.string.s_today_s_times_and_qibla_compass)) { vm.screen = Screen.PRAYER }
                    }
                    Note(stringResource(R.string.s_calculated_on_the_phone_from_the))
                    Group(stringResource(R.string.s_alerts)) {
                        ToggleRow(stringResource(R.string.s_prayer_time_alert), s.adhanAlert) { on -> alertToggle(on) { it.copy(adhanAlert = on) } }
                        ToggleRow(stringResource(R.string.s_iqama_alert), s.iqamaAlert) { on -> alertToggle(on) { it.copy(iqamaAlert = on) } }
                        if (s.adhanAlert || s.iqamaAlert) {
                            ActionRow(stringResource(R.string.s_alert_sound_and_vibration), "Android settings") { ctx.safeStart(PrayerAlerts.soundSettings(ctx)) }
                        }
                    }
                    Group(stringResource(R.string.s_iqama_after_the_adhan)) {
                        Prayer.entries.filter { it.isPrayer }.forEach { p ->
                            Stepper(p.label, "${s.iqamaMin(p)} min",
                                onMinus = { vm.updateSettings { it.copy(iqama = it.iqama + (p to (it.iqamaMin(p) - 5).coerceAtLeast(5))) } },
                                onPlus = { vm.updateSettings { it.copy(iqama = it.iqama + (p to (it.iqamaMin(p) + 5).coerceAtMost(60))) } })
                        }
                    }
                    Note(stringResource(R.string.s_set_your_mosque_s_iqama_times))
                    Group(stringResource(R.string.s_feel)) {
                        ActionRow(stringResource(R.string.s_edge_light), s.edgeStyle.label) { page = SettingsPage.APPEARANCE }
                        ToggleRow(stringResource(R.string.s_vibrate_on_the_qibla_compass), s.compassHaptics) { on ->
                            vm.updateSettings { it.copy(compassHaptics = on) }
                        }
                    }
                    Note(stringResource(R.string.s_the_compass_ticks_every_10_clicks))
                }

                SettingsPage.SENIOR -> {
                    Group(stringResource(R.string.s_senior_mode)) {
                        ToggleRow(stringResource(R.string.s_senior_mode), s.seniorMode) { on -> vm.setSeniorMode(on) }
                    }
                    Note(stringResource(R.string.s_big_text_six_large_tiles_and))
                    Group(stringResource(R.string.s_tiles)) {
                        (0 until 6).forEach { i ->
                            ActionRow("Tile ${i + 1}", vm.seniorApp(i)?.label ?: "Empty") { seniorPick = i }
                        }
                    }
                    Group(stringResource(R.string.s_call_button)) {
                        ActionRow(stringResource(R.string.s_name), s.seniorCallName.ifBlank { "Not set" }) {
                            textEdit = TextEdit("Name on the button", "For example Mum or Ahmed.", s.seniorCallName, "Name") { v ->
                                vm.updateSettings { it.copy(seniorCallName = v) }
                            }
                        }
                        ActionRow(stringResource(R.string.s_phone_number), s.seniorCallNumber.ifBlank { "Not set" }) {
                            textEdit = TextEdit("Phone number", "Opens the phone app with this number.", s.seniorCallNumber, "+971 50 123 4567") { v ->
                                vm.updateSettings { it.copy(seniorCallNumber = v) }
                            }
                        }
                    }
                }

                SettingsPage.LANGUAGE -> {
                    val chosen = remember { Lang.saved(ctx) }
                    Group(stringResource(R.string.s_language)) {
                        Lang.CHOICES.forEachIndexed { i, (code, name) ->
                            if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (code != chosen) {
                                        Lang.save(ctx, code)
                                        ctx.findActivity()?.recreate()
                                    }
                                }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (code.isEmpty()) stringResource(R.string.s_system_default) else name, fontSize = 16.sp,
                                    modifier = Modifier.weight(1f))
                                if (code == chosen) Text("✓", color = Accent, fontSize = 16.sp)
                            }
                        }
                    }
                    Note(stringResource(R.string.s_language_note))
                }

                SettingsPage.SELFHOSTED -> SelfHostedSettings(vm)

                SettingsPage.TOOLS -> {
                    Group(stringResource(R.string.s_tools)) {
                        ToggleRow(stringResource(R.string.s_show_tools), s.toolsOn) { on -> vm.updateSettings { it.copy(toolsOn = on) } }
                        if (s.toolsOn) ActionRow(stringResource(R.string.s_open_tools), "Device, security and text, network") {
                            vm.toolsReturn = Screen.SETTINGS
                            vm.screen = Screen.TOOLS
                        }
                    }
                    Note(stringResource(R.string.s_device_info_passwords_qr_codes_and))
                }

                SettingsPage.WEATHER -> {
                    Group(stringResource(R.string.s_weather)) {
                        ToggleRow(stringResource(R.string.s_weather_card_on_home), s.weatherOn) { on ->
                            vm.updateSettings { it.copy(weatherOn = on) }
                            if (on && s.city == null) dialog = SettingsDialog.CITY else if (on) vm.refreshLive(force = true)
                        }
                        ActionRow(stringResource(R.string.s_city), s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") { dialog = SettingsDialog.CITY }
                        ActionRow(stringResource(R.string.s_units), if (s.fahrenheit) "Fahrenheit (°F)" else "Celsius (°C)") {
                            vm.updateSettings { it.copy(fahrenheit = !it.fahrenheit) }
                        }
                    }
                    Note(stringResource(R.string.s_from_open_meteo_only_the_city))
                }

                SettingsPage.GOLD -> {
                    Group(stringResource(R.string.s_gold_price)) {
                        ActionRow(stringResource(R.string.s_source), s.goldSource.label) { dialog = SettingsDialog.GOLD_SOURCE }
                        ActionRow(stringResource(R.string.s_show_in), s.goldCurrency) { dialog = SettingsDialog.CURRENCY }
                        ActionRow(stringResource(R.string.s_karat), "${s.goldKarat}K") { dialog = SettingsDialog.KARAT }
                        ActionRow(stringResource(R.string.s_unit), if (s.goldPerGram) "Per gram" else "Per troy ounce") {
                            vm.updateSettings { it.copy(goldPerGram = !it.goldPerGram) }
                        }
                    }
                    if (s.goldSource == GoldSource.CUSTOM) {
                        val g = vm.sources.gold
                        var test by remember { mutableStateOf<String?>(null) }
                        Group(stringResource(R.string.s_your_source)) {
                            ActionRow(stringResource(R.string.s_address), g.url.ifBlank { "Not set · tap to add" }) {
                                textEdit = TextEdit("Gold price address",
                                    "Any web address that shows a gold price: an API, a JSON file, or a page.",
                                    g.url, "https://…") { vm.setCustomGold(g.copy(url = it)); test = null }
                            }
                            ActionRow(stringResource(R.string.s_where_the_price_is), g.path.ifBlank { "The first number" }) {
                                textEdit = TextEdit("Where the price is",
                                    "For JSON, the path to the number, like data.price or rates.XAU or 0.price. " +
                                        "Leave empty to use the first number in the answer.",
                                    g.path, "data.price") { vm.setCustomGold(g.copy(path = it)); test = null }
                            }
                            ActionRow(stringResource(R.string.s_the_price_is_per), if (g.perGram) "Gram" else "Troy ounce") {
                                vm.setCustomGold(g.copy(perGram = !g.perGram)); test = null
                            }
                            ActionRow(stringResource(R.string.s_in_currency), g.currency) { dialog = SettingsDialog.GOLD_CUSTOM_CURRENCY }
                            ActionRow(stringResource(R.string.s_test), test ?: "Fetch it now and show what was read") {
                                test = "Checking…"
                                settingsScope.launch { test = vm.testCustomGold() }
                            }
                        }
                        Note(stringResource(R.string.s_the_price_is_taken_as_24k))
                    }
                    Group(stringResource(R.string.s_widget)) {
                        Text("Add \"Stillpoint Gold\" from the Shelf (swipe right on home, Add widget) " +
                            "or from any launcher. Tap the widget to refresh.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Note("Price: " + s.goldSource.detail + ". Exchange rates: European Central Bank. Jewellery adds making charges.")
                }

                SettingsPage.HUB -> {
                    Group(stringResource(R.string.s_project_hub)) {
                        if (vm.hubConnected()) {
                            ActionRow(stringResource(R.string.s_server), vm.hubUrl().orEmpty()) {}
                            ActionRow(
                                stringResource(R.string.s_status),
                                vm.hubError ?: vm.hub?.let { "Connected · updated ${((System.currentTimeMillis() - it.fetchedAt) / 60_000L)} min ago. Tap to update." }
                                    ?: "Connected. Tap to update.",
                            ) { vm.refreshHub(force = true) }
                            ActionRow(stringResource(R.string.s_disconnect), "Forget the address and key on this phone") { vm.disconnectHub() }
                        } else {
                            ActionRow(stringResource(R.string.s_connect), "Scan the QR code on your hub's \"Connect phone\" page, or type the address and key") {
                                dialog = SettingsDialog.HUB
                            }
                        }
                    }
                    Note(stringResource(R.string.s_your_own_server_stillpoint_connects_only))
                }

                SettingsPage.BACKUP -> {
                    Group(stringResource(R.string.s_backup)) {
                        ActionRow(stringResource(R.string.s_save_a_backup_file), "Settings, notes, tasks, projects, pinned apps, favorites, gestures (no keys or tokens)") {
                            saveBackup.launch("stillpoint-backup-${java.time.LocalDate.now()}.json")
                        }
                        ActionRow(stringResource(R.string.s_restore_from_a_file), "Puts a backup's setup in place, then Stillpoint restarts") {
                            openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        }
                    }
                    backupNote?.let { Note(it) }
                    Note(stringResource(R.string.s_saved_where_you_choose_nothing_is))
                }

                SettingsPage.NOTIFY -> {
                    val access = remember(vm.resumeTick) { NotifyHub.hasAccess(ctx) }
                    Group(stringResource(R.string.s_notification_access)) {
                        ActionRow(if (access) "Allowed" else "Not allowed · tap to allow",
                            "Read on the phone only. Nothing is kept or sent.") {
                            ctx.safeStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    }
                    if (!access) Note(stringResource(R.string.s_switch_greyed_out_app_info_allow))
                    Group(stringResource(R.string.s_show)) {
                        ToggleRow(stringResource(R.string.s_edge_light), s.notifyLight) { on -> vm.updateSettings { it.copy(notifyLight = on) } }
                        ActionRow(stringResource(R.string.s_style), s.notifyStyle.label) {
                            vm.updateSettings { it.copy(notifyStyle = NotifyStyle.entries[(it.notifyStyle.ordinal + 1) % NotifyStyle.entries.size]) }
                        }
                        ToggleRow(stringResource(R.string.s_signal_dot_at_the_top), s.notifyDot) { on -> vm.updateSettings { it.copy(notifyDot = on) } }
                        ToggleRow(stringResource(R.string.s_dots_on_apps), s.notifyAppDots) { on -> vm.updateSettings { it.copy(notifyAppDots = on) } }
                        ActionRow(stringResource(R.string.s_send_a_test_notification), "Then go home to see the light and the dots") { NotifyTest.send(ctx) }
                    }
                    Note(stringResource(R.string.s_uses_the_edge_style_from_appearance))
                    Group(stringResource(R.string.s_never_miss)) {
                        ActionRow(stringResource(R.string.s_remind_again_while_unread), if (s.remindEvery == 0) "Off" else "Every ${s.remindEvery} min, up to 3 times") {
                            vm.updateSettings { it.copy(remindEvery = when (it.remindEvery) { 0 -> 5; 5 -> 10; 10 -> 15; else -> 0 }) }
                        }
                        s.importantPeople.forEach { name ->
                            ActionRow(name, "Important person · tap to remove") {
                                vm.updateSettings { it.copy(importantPeople = it.importantPeople - name) }
                            }
                        }
                        ActionRow(stringResource(R.string.s_add_a_person), "A name as it shows in their notifications") {
                            textEdit = TextEdit("Important person",
                                "Type the name as it appears in notifications (for example Mum, or a group's name).", "", "Name") { n ->
                                if (n.isNotBlank()) vm.updateSettings { it.copy(importantPeople = (it.importantPeople + n.trim()).distinct()) }
                            }
                        }
                    }
                    Note(stringResource(R.string.s_important_apps_and_people_shine_brighter))
                    Group(stringResource(R.string.s_apps)) {
                        val seen = vm.notifySeenApps()
                        if (seen.isEmpty()) Text(stringResource(R.string.s_apps_appear_here_after_they_show), color = Muted, fontSize = 14.sp,
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
                    Group(stringResource(R.string.s_right_now)) {
                        ActionRow(stringResource(R.string.s_memory), "${f.memMb} MB") {}
                        ActionRow(stringResource(R.string.s_processor), "${formatDuration(f.cpuMs).let { if (f.cpuMs < 60_000) "${f.cpuMs / 1000} s" else it }} " +
                            "of work since it started ${formatDuration(f.upMs)} ago " +
                            "(${String.format(java.util.Locale.US, "%.2f", f.cpuMs * 100.0 / f.upMs.coerceAtLeast(1))}% of the time)") {}
                    }
                    Group(stringResource(R.string.s_battery)) {
                        ActionRow(stringResource(R.string.s_battery_use), "Android keeps this figure. Tap to see Stillpoint's page.") {
                            ctx.safeStart(
                                Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").putExtra("package_name", ctx.packageName),
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    }
                    Note(stringResource(R.string.s_android_shows_battery_use_on_its))
                }

                SettingsPage.SOURCES -> {
                    Group(stringResource(R.string.s_addresses)) {
                        SourceKey.entries.forEach { k ->
                            val custom = vm.sources.isCustom(k)
                            ActionRow(k.label, (if (custom) "Yours · " else "Default · ") +
                                vm.sources.url(k).removePrefix("https://").removePrefix("http://")) {
                                textEdit = TextEdit(k.label, k.help, if (custom) vm.sources.url(k) else "", k.default,
                                    onReset = { vm.setSource(k, null) }) { vm.setSource(k, it) }
                            }
                        }
                    }
                    Group(stringResource(R.string.s_set_on_their_own_pages)) {
                        ActionRow(stringResource(R.string.s_gold_price), s.goldSource.label) { page = SettingsPage.GOLD }
                        ActionRow(stringResource(R.string.s_calendar_link), vm.icsUrl()?.let { maskUrl(it) } ?: "Not set") { page = SettingsPage.CALENDAR }
                        ActionRow(stringResource(R.string.s_project_hub), vm.hubUrl() ?: "Not connected") { page = SettingsPage.HUB }
                    }
                    Note(stringResource(R.string.s_leave_empty_for_the_default_prayer))
                }

                SettingsPage.LOCK -> {
                    Group(stringResource(R.string.s_lock_screen)) {
                        ToggleRow(stringResource(R.string.s_show_info_on_the_lock_screen), s.lockOn) { on ->
                            if (on && !LockNotification.canPost(ctx)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { vm.updateSettings { it.copy(lockOn = on) }; Refresh.all(ctx) }
                        }
                    }
                    Group(stringResource(R.string.s_show)) {
                        ToggleRow(stringResource(R.string.s_next_prayer_with_countdown), s.lockPrayer) { on ->
                            vm.updateSettings { it.copy(lockPrayer = on) }; Refresh.all(ctx)
                        }
                        ToggleRow(stringResource(R.string.s_hijri_date), s.lockHijri) { on -> vm.updateSettings { it.copy(lockHijri = on) }; Refresh.all(ctx) }
                        ToggleRow(stringResource(R.string.s_tamil_date), s.lockTamil) { on -> vm.updateSettings { it.copy(lockTamil = on) }; Refresh.all(ctx) }
                    }
                    Note(stringResource(R.string.s_a_silent_lock_screen_notification_android))
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
                        TaskSource.PHONE -> Note(stringResource(R.string.s_stored_on_this_phone_swipe_a))
                        TaskSource.HUB -> SyncGroup(vm, SyncFeature.TASKS, Sync.HUB, s.tasksSync) { dialog = SettingsDialog.SYNC_TASKS }
                        else -> Group(stringResource(R.string.s_sync)) {
                            Text("${s.tasksSource.label} keeps itself in sync, for example with DAVx5. Stillpoint reads its open " +
                                "tasks when you open the Shelf; ticking one there marks it done in ${s.tasksSource.label}.",
                                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                            ActionRow(stringResource(R.string.s_read_now), vm.providerError ?: "${vm.providerTasks.size} open tasks") { vm.loadProviderTasks() }
                        }
                    }
                    Note(stringResource(R.string.s_add_tasks_to_any_shelf_shelf))
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
                        CalendarSource.PHONE -> Note(stringResource(R.string.s_android_syncs_the_accounts_on_this))
                        CalendarSource.HUB -> SyncGroup(vm, SyncFeature.CALENDAR, Sync.HUB, s.calendarSync) { dialog = SettingsDialog.SYNC_CALENDAR }
                        CalendarSource.ICS -> {
                            Group(stringResource(R.string.s_calendar_link)) {
                                ActionRow(stringResource(R.string.s_link), vm.icsUrl()?.let { maskUrl(it) } ?: "Not set · tap to add") { dialog = SettingsDialog.ICS_URL }
                            }
                            SyncGroup(vm, SyncFeature.CALENDAR, Sync.ICS, s.calendarSync) { dialog = SettingsDialog.SYNC_CALENDAR }
                        }
                    }
                    Group(stringResource(R.string.s_display)) {
                        ToggleRow(stringResource(R.string.s_today_s_calendar_on_home), s.showAgenda) { on ->
                            val needsPermission = s.calendarSource == CalendarSource.PHONE &&
                                ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
                            if (on && needsPermission) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
                        }
                    }
                }

                SettingsPage.PRIVACY -> {
                    Group(stringResource(R.string.s_permissions)) {
                        ActionRow(
                            "Usage access: ${if (vm.hasUsageAccess) "allowed" else "not allowed"}",
                            "Screen time, most-used apps and data usage. Data stays on the phone.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        ActionRow(
                            "Gesture service: ${if (a11yOn) "on" else "off"}",
                            "Only for the Lock screen and Notifications gestures.",
                        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    }
                    Group(stringResource(R.string.s_data)) {
                        ActionRow(stringResource(R.string.s_data_usage), "Per-app Wi-Fi and mobile data, from Android's own records") { vm.screen = Screen.DATA }
                    }
                }

                SettingsPage.UPDATES -> Group(stringResource(R.string.s_github_releases)) { UpdateSection(available) }

                SettingsPage.SYSTEM -> Group(stringResource(R.string.s_system)) {
                    SettingsPage.entries
                        .filter { it.parent == SettingsPage.SYSTEM && (it != SettingsPage.UPDATES || Updater.AVAILABLE) }
                        .forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                            MenuRow(p.icon, stringResource(p.title), stringResource(p.summary)) { page = p }
                        }
                }

                SettingsPage.ABOUT -> {
                    Group(stringResource(R.string.s_stillpoint_launcher)) {
                        ActionRow(stringResource(R.string.s_version), BuildConfig.VERSION_NAME) {}
                        ActionRow(stringResource(R.string.s_app_info), "Android's page for Stillpoint: permissions, notifications, battery, storage") {
                            ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }
                        ActionRow(stringResource(R.string.s_made_by), "cloudit24 · github.com/cloudit24") {
                            ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cloudit24")))
                        }
                        ActionRow(stringResource(R.string.s_source_code), "github.com/cloudit24/stillpoint · GPL-3.0") {
                            ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cloudit24/stillpoint")))
                        }
                        ActionRow(stringResource(R.string.s_set_as_default_home_app), "Opens the system home app picker") {
                            ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                    var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
                    crash?.let { (at, text) ->
                        Group(stringResource(R.string.s_last_problem)) {
                            ActionRow(stringResource(R.string.s_stillpoint_closed_unexpectedly), "${formatAgo(at)} · tap to share the report") {
                                ctx.safeStart(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "Stillpoint crash report")
                                        .putExtra(Intent.EXTRA_TEXT, text),
                                    "Share crash report",
                                ))
                            }
                            ActionRow(stringResource(R.string.s_clear_report)) { CrashLog.clear(ctx); crash = null }
                        }
                        Note(stringResource(R.string.s_kept_only_on_this_phone_nothing))
                    }
                    Group(stringResource(R.string.s_privacy)) {
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
                }) { Text(stringResource(R.string.s_restart)) }
            },
            title = { Text(stringResource(R.string.s_restored)) },
            text = { Text(stringResource(R.string.s_your_setup_is_back_stillpoint_restarts)) },
        )
    }

    seniorPick?.let { i ->
        SeniorAppPicker(vm, onDismiss = { seniorPick = null }) { app -> vm.setSeniorApp(i, app?.key); seniorPick = null }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_connect_project_hub)) },
        text = {
            Column {
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text(stringResource(R.string.s_address)) },
                    placeholder = { Text(stringResource(R.string.s_https_hub_example_com)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true, label = { Text(stringResource(R.string.s_app_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    modifier = Modifier.padding(top = 8.dp))
                if (url.startsWith("http://") && !url.isLocalAddress()) {
                    Text(stringResource(R.string.s_this_address_isn_t_encrypted_use),
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
                Text(stringResource(p.title), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_close)) } },
        title = { Text(stringResource(R.string.s_accent_colour)) },
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
        confirmButton = { TextButton(onClick = { search() }) { Text(stringResource(R.string.s_search_2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_choose_city)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.s_city_name_e_g_dubai)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                if (searching) Text(stringResource(R.string.s_searching), color = Muted, modifier = Modifier.padding(top = 8.dp))
                if (searched && !searching && results.isEmpty()) {
                    Text(stringResource(R.string.s_no_match_check_the_spelling_or), color = Muted, modifier = Modifier.padding(top = 8.dp))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
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
                item { Text(stringResource(R.string.s_open_an_app), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
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
    Group(stringResource(R.string.s_source)) {
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
    Group(stringResource(R.string.s_sync)) {
        ToggleRow(stringResource(R.string.s_auto_sync), cfg.auto) { on -> vm.setSync(feature) { it.copy(auto = on) } }
        ActionRow(stringResource(R.string.s_sync_every), intervalLabel(cfg.everyMin)) { onEvery() }
        ToggleRow(stringResource(R.string.s_only_on_wi_fi), cfg.wifiOnly) { on -> vm.setSync(feature) { it.copy(wifiOnly = on) } }
        ActionRow(stringResource(R.string.s_last_sync), (if (last == 0L) "Never" else formatAgo(last)) + (error?.let { " · $it" } ?: "")) {}
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
        confirmButton = { TextButton(onClick = { onSave(url); onDismiss() }) { Text(stringResource(R.string.s_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_calendar_link)) },
        text = {
            Column {
                Text("Paste your calendar's private .ics address. Outlook: Settings, Calendar, Shared calendars, " +
                    "Publish a calendar. Google: the calendar's settings, Secret address in iCal format.",
                    color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.s_https_calendar_ics)) }, modifier = Modifier.padding(top = 12.dp),
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
        confirmButton = { TextButton(onClick = { t.onSave(text.trim()); onDismiss() }) { Text(stringResource(R.string.s_save)) } },
        dismissButton = {
            Row {
                t.onReset?.let { reset -> TextButton(onClick = { reset(); onDismiss() }) { Text(stringResource(R.string.s_default)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) }
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

/** The activity behind a context, to restart it after the language changes. */
private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
