package com.cloudit24.stillpoint.ui

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

/** Settings groups, Windows Phone style: a menu of big lowercase names, each opening its own page. */
private enum class SettingsPage(val title: String, val summary: String) {
    APPEARANCE("appearance", "accent colour, clock style, app icons"),
    HOME("home screen", "apps on home, size, style, screen time"),
    INFO("info on home", "network speed, RAM, IP address, calendar"),
    WEATHER("weather", "city, units, animation"),
    PRAYER("prayer & calendars", "prayer times, Qibla, Hijri and Tamil dates"),
    GOLD("gold price", "source, currency, karat"),
    APPS("app list", "start tab, hidden apps"),
    GESTURES("gestures", "swipes, double-tap, bottom shortcuts"),
    PRIVACY("permissions & data", "usage access, gesture service, data usage"),
    UPDATES("updates", "download new versions from GitHub"),
    ABOUT("about", "version and privacy"),
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val a11yOn = remember(vm.resumeTick) { LockAccessibilityService.isEnabled(ctx) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var picking by remember { mutableStateOf<GestureSlot?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    BackHandler(enabled = page != null) { page = null }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(showAgenda = granted) }
        vm.refresh()
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(tween(260)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(180)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(180)))
        },
        label = "settings",
    ) { current ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Text(if (current == null) "STILLPOINT" else "SETTINGS", color = Muted, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Text(current?.title ?: "settings", fontSize = 44.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 12.dp))

            when (current) {
                null -> {
                    SettingsPage.entries.filter { it != SettingsPage.UPDATES || Updater.AVAILABLE }.forEach { p ->
                        Column(Modifier.fillMaxWidth().clickable { page = p }.padding(vertical = 10.dp)) {
                            Text(p.title, fontSize = 24.sp, fontWeight = FontWeight.Light)
                            Text(p.summary, color = Muted, fontSize = 13.sp)
                        }
                    }
                    ActionRow("Set as default home app", "Opens the system home app picker") {
                        ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                    }
                }

                SettingsPage.APPEARANCE -> {
                    Row(Modifier.fillMaxWidth().clickable { dialog = SettingsDialog.ACCENT }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Accent colour", fontSize = 16.sp)
                            Text(ACCENTS.firstOrNull { it.argb == s.accent }?.name ?: "Custom", color = Muted, fontSize = 13.sp)
                        }
                        Box(Modifier.size(36.dp).background(Color(s.accent)))
                    }
                    ActionRow("Clock style", s.clockStyle.label) { dialog = SettingsDialog.CLOCK }
                    ToggleRow("Show app icons in the app list", s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }
                }

                SettingsPage.HOME -> {
                    ActionRow("Home style", s.homeStyle.label) { dialog = SettingsDialog.HOME_STYLE }
                    ToggleRow("Show most-used apps", s.homeMode == HomeMode.AUTO) { on ->
                        vm.updateSettings { it.copy(homeMode = if (on) HomeMode.AUTO else HomeMode.PINNED) }
                    }
                    Stepper("Apps on home", "${s.homeCount}",
                        onMinus = { vm.updateSettings { it.copy(homeCount = (it.homeCount - 1).coerceAtLeast(3)) } },
                        onPlus = { vm.updateSettings { it.copy(homeCount = (it.homeCount + 1).coerceAtMost(9)) } })
                    Note("Pinned apps are shown when most-used is off, or before usage data exists. Pin from the app list by long-pressing.")
                    Stepper("App size", "${s.homeSize}",
                        onMinus = { vm.updateSettings { it.copy(homeSize = (it.homeSize - 2).coerceAtLeast(16)) } },
                        onPlus = { vm.updateSettings { it.copy(homeSize = (it.homeSize + 2).coerceAtMost(40)) } })
                    ToggleRow("Show screen time", s.showUsage) { on -> vm.updateSettings { it.copy(showUsage = on) } }
                    ToggleRow("Show tasks on the widget page", s.showTasks) { on -> vm.updateSettings { it.copy(showTasks = on) } }
                }

                SettingsPage.INFO -> {
                    ToggleRow("Network speed and RAM", s.showStats) { on -> vm.updateSettings { it.copy(showStats = on) } }
                    ToggleRow("Local IP address", s.showLocalIp) { on -> vm.updateSettings { it.copy(showLocalIp = on) } }
                    ToggleRow("Public IP address", s.publicIpOn) { on -> vm.updateSettings { it.copy(publicIpOn = on) } }
                    if (s.publicIpOn) Note("Asked from api.ipify.org (open source) when your network changes.")
                    ToggleRow("Today's calendar", s.showAgenda) { on ->
                        val granted = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                        if (on && !granted) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
                    }
                    Note("Weather, gold and prayer times have their own pages.")
                }

                SettingsPage.WEATHER -> {
                    ToggleRow("Show weather next to the clock", s.weatherOn) { on ->
                        if (on && s.city == null) dialog = SettingsDialog.CITY
                        else { vm.updateSettings { it.copy(weatherOn = on) }; if (on) vm.refreshLive(force = true) }
                    }
                    ActionRow("City", s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") { dialog = SettingsDialog.CITY }
                    ToggleRow("Fahrenheit", s.fahrenheit) { on -> vm.updateSettings { it.copy(fahrenheit = on) } }
                    ToggleRow("Animate weather", s.animateWeather) { on -> vm.updateSettings { it.copy(animateWeather = on) } }
                    Note("Weather data by Open-Meteo.com (CC BY 4.0). Only the city's approximate location is sent. No GPS.")
                }

                SettingsPage.PRAYER -> {
                    Header("Prayer times")
                    ToggleRow("Show next prayer on home", s.prayerOn) { on ->
                        if (on && s.city == null) dialog = SettingsDialog.PRAYER_CITY
                        else vm.updateSettings { it.copy(prayerOn = on) }
                    }
                    ActionRow("City", s.city?.let { "${it.name}, ${it.country} (shared with weather)" } ?: "Not set") {
                        dialog = SettingsDialog.PRAYER_CITY
                    }
                    ActionRow("Calculation method", s.prayerMethod.label) { dialog = SettingsDialog.PRAYER_METHOD }
                    ActionRow("Asr time", if (s.asrHanafi) "Hanafi (later)" else "Standard (Shafi'i, Maliki, Hanbali)") {
                        vm.updateSettings { it.copy(asrHanafi = !it.asrHanafi) }
                    }
                    ActionRow("Today's times and Qibla compass") { vm.screen = Screen.PRAYER }
                    Note("Calculated on the phone from the city's position. Nothing is sent.")

                    Header("Dates on home")
                    ToggleRow("Hijri date", s.hijriOn) { on -> vm.updateSettings { it.copy(hijriOn = on) } }
                    Stepper("Hijri adjustment", if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}",
                        onMinus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust - 1).coerceAtLeast(-2)) } },
                        onPlus = { vm.updateSettings { it.copy(hijriAdjust = (it.hijriAdjust + 1).coerceAtMost(2)) } })
                    ToggleRow("Tamil date", s.tamilOn) { on -> vm.updateSettings { it.copy(tamilOn = on) } }
                    Note("With both on, home switches between them every few seconds. Hijri follows the Umm al-Qura " +
                        "calendar; adjust by a day if your country's moon sighting differs. Tamil date is the solar " +
                        "calendar at Chennai sunset, with the 60-year name and Thiruvalluvar year.")
                }

                SettingsPage.GOLD -> {
                    ToggleRow("Show gold price", s.goldOn) { on ->
                        vm.updateSettings { it.copy(goldOn = on) }
                        if (on) vm.refreshLive(force = true)
                    }
                    ActionRow("Price", s.goldSource.label) { dialog = SettingsDialog.GOLD_SOURCE }
                    ActionRow("Currency", s.goldCurrency) { dialog = SettingsDialog.CURRENCY }
                    ActionRow("Karat", "${s.goldKarat}K") { dialog = SettingsDialog.KARAT }
                    ActionRow("Unit", if (s.goldPerGram) "Per gram" else "Per troy ounce") {
                        vm.updateSettings { it.copy(goldPerGram = !it.goldPerGram) }
                    }
                    Note(s.goldSource.detail + ". Other currencies use European Central Bank rates via Frankfurter; " +
                        "AED and SAR use the official fixed rate. Jewellery adds making charges on top.")
                }

                SettingsPage.APPS -> {
                    ActionRow("Open the app list on", s.drawerStart.label) { dialog = SettingsDialog.DRAWER_START }
                    ToggleRow("Show app icons", s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }
                    Header("Hidden apps")
                    val hidden = vm.apps.filter { it.key in s.hidden }
                    if (hidden.isEmpty()) Note("None. Long-press an app in the list to hide it.")
                    hidden.forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("Unhide", color = Accent, modifier = Modifier.clickable { vm.unhide(app.key) }.padding(8.dp))
                        }
                    }
                }

                SettingsPage.GESTURES -> {
                    GestureSlot.entries.forEach { slot ->
                        ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
                    }
                    ActionRow(
                        "Gesture service: ${if (a11yOn) "on" else "off"}",
                        "Needed only for Lock screen and Notifications. " +
                            "If the switch is greyed out on a sideloaded install: App info, menu, Allow restricted settings.",
                    ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }

                SettingsPage.PRIVACY -> {
                    ActionRow(
                        "Usage access: ${if (vm.hasUsageAccess) "allowed" else "not allowed"}",
                        "Used for screen time, most-used apps and data usage. Data stays on the device.",
                    ) { ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    ActionRow(
                        "Gesture service: ${if (a11yOn) "on" else "off"}",
                        "Only for the Lock screen and Notifications gestures.",
                    ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    ActionRow("Data usage", "Per-app Wi-Fi and mobile data, from Android's own records") { vm.screen = Screen.DATA }
                }

                SettingsPage.UPDATES -> UpdateSection()

                SettingsPage.ABOUT -> {
                    Text("Stillpoint ${BuildConfig.VERSION_NAME}", fontSize = 18.sp)
                    Note("Open source (GPL-3.0): github.com/cloudit24/stillpoint")
                    Note("No analytics, no accounts. The network is used only for features you switch on: " +
                        "weather, gold price, public IP" + if (Updater.AVAILABLE) " and the update check." else ".")
                    Note("Prayer times, Qibla, Hijri and Tamil dates are calculated on the phone.")
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.ACCENT -> AccentDialog(s.accent, onDismiss = { dialog = null }) { a ->
            vm.updateSettings { it.copy(accent = a) }
        }
        SettingsDialog.HOME_STYLE -> ChoiceDialog("Home style", HomeStyle.entries, { it.label }, onDismiss = { dialog = null }) { h ->
            vm.updateSettings { it.copy(homeStyle = h) }
        }
        SettingsDialog.DRAWER_START -> ChoiceDialog("Open the app list on", DrawerTab.entries, { it.label },
            onDismiss = { dialog = null }) { t -> vm.updateSettings { it.copy(drawerStart = t) } }
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
        SettingsDialog.CITY -> CitySearchDialog(vm, onDismiss = { dialog = null }) { vm.setCity(it) }
        SettingsDialog.PRAYER_CITY -> CitySearchDialog(vm, onDismiss = { dialog = null }) { c ->
            vm.setCity(c, enableWeather = false)
            vm.updateSettings { it.copy(prayerOn = true) }
        }
        SettingsDialog.PRAYER_METHOD -> ChoiceDialog("Calculation method", PrayerMethod.entries, { it.label },
            onDismiss = { dialog = null }) { pm -> vm.updateSettings { it.copy(prayerMethod = pm) } }
        null -> Unit
    }

    picking?.let { slot ->
        TargetPicker(vm, title = slot.label, onPick = { vm.setGesture(slot, it); picking = null }, onDismiss = { picking = null })
    }
}

private enum class SettingsDialog {
    ACCENT, HOME_STYLE, DRAWER_START, CLOCK, GOLD_SOURCE, CURRENCY, KARAT, CITY, PRAYER_CITY, PRAYER_METHOD,
}

/** Small accent-coloured group title inside a page. */
@Composable
private fun Header(text: String) {
    Text(text.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("−", fontSize = 22.sp, modifier = Modifier.clickable(onClick = onMinus).padding(horizontal = 14.dp))
        Text(value, fontSize = 18.sp)
        Text("+", fontSize = 22.sp, modifier = Modifier.clickable(onClick = onPlus).padding(horizontal = 14.dp))
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
                items(HomeAction.entries) { a ->
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
private fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = BuildConfig.VERSION_NAME
    var result by remember { mutableStateOf<UpdateCheck?>(null) }
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
