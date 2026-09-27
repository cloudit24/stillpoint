package com.cloudit24.stillpoint.ui

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
import com.cloudit24.stillpoint.data.City
import com.cloudit24.stillpoint.data.ClockStyle
import com.cloudit24.stillpoint.data.GOLD_CURRENCIES
import com.cloudit24.stillpoint.data.GestureSlot
import com.cloudit24.stillpoint.data.GoldSource
import com.cloudit24.stillpoint.data.GestureTarget
import com.cloudit24.stillpoint.data.HomeAction
import com.cloudit24.stillpoint.data.HomeStyle
import com.cloudit24.stillpoint.update.UpdateCheck
import com.cloudit24.stillpoint.update.Updater
import com.cloudit24.stillpoint.data.HomeMode
import com.cloudit24.stillpoint.service.LockAccessibilityService
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val a11yOn = remember(vm.resumeTick) { LockAccessibilityService.isEnabled(ctx) }
    var picking by remember { mutableStateOf<GestureSlot?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(showAgenda = granted) }
        vm.refresh()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Light)

        ActionRow("Set as default home app", "Opens the system home app picker") {
            ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }

        SectionHeader("Home screen")
        ToggleRow("Show most-used apps", s.homeMode == HomeMode.AUTO) { on ->
            vm.updateSettings { it.copy(homeMode = if (on) HomeMode.AUTO else HomeMode.PINNED) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Apps on home", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("−", fontSize = 22.sp, modifier = Modifier
                .clickable { vm.updateSettings { it.copy(homeCount = (it.homeCount - 1).coerceAtLeast(3)) } }
                .padding(horizontal = 14.dp))
            Text("${s.homeCount}", fontSize = 18.sp)
            Text("+", fontSize = 22.sp, modifier = Modifier
                .clickable { vm.updateSettings { it.copy(homeCount = (it.homeCount + 1).coerceAtMost(8)) } }
                .padding(horizontal = 14.dp))
        }
        Text("Pinned apps are shown when most-used is off, or before usage data exists. Pin from the app list by long-pressing.",
            color = Muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Home app size", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("−", fontSize = 22.sp, modifier = Modifier
                .clickable { vm.updateSettings { it.copy(homeSize = (it.homeSize - 2).coerceAtLeast(16)) } }
                .padding(horizontal = 14.dp))
            Text("${s.homeSize}", fontSize = 18.sp)
            Text("+", fontSize = 22.sp, modifier = Modifier
                .clickable { vm.updateSettings { it.copy(homeSize = (it.homeSize + 2).coerceAtMost(40)) } }
                .padding(horizontal = 14.dp))
        }
        ToggleRow("Icons only on home", s.homeStyle == HomeStyle.ICONS) { on ->
            vm.updateSettings { it.copy(homeStyle = if (on) HomeStyle.ICONS else HomeStyle.LIST) }
        }
        ToggleRow("Show screen time", s.showUsage) { on -> vm.updateSettings { it.copy(showUsage = on) } }
        ToggleRow("Show tasks on the widget page", s.showTasks) { on -> vm.updateSettings { it.copy(showTasks = on) } }
        ActionRow("Clock style", s.clockStyle.label) { dialog = SettingsDialog.CLOCK }
        ToggleRow("Show network speed and RAM", s.showStats) { on -> vm.updateSettings { it.copy(showStats = on) } }
        ToggleRow("Show today's calendar", s.showAgenda) { on ->
            val granted = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (on && !granted) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
        }

        SectionHeader("Weather")
        ToggleRow("Show weather next to the clock", s.weatherOn) { on ->
            if (on && s.city == null) dialog = SettingsDialog.CITY
            else { vm.updateSettings { it.copy(weatherOn = on) }; if (on) vm.refreshLive(force = true) }
        }
        ActionRow("City", s.city?.let { "${it.name}, ${it.country}" } ?: "Not set") { dialog = SettingsDialog.CITY }
        ToggleRow("Fahrenheit", s.fahrenheit) { on -> vm.updateSettings { it.copy(fahrenheit = on) } }
        ToggleRow("Animate weather", s.animateWeather) { on -> vm.updateSettings { it.copy(animateWeather = on) } }
        Text("Weather data by Open-Meteo.com (CC BY 4.0). Only the city's approximate location is sent. No GPS.",
            color = Muted, fontSize = 12.sp)

        SectionHeader("Gold price")
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
        Text(s.goldSource.detail + ". Other currencies use European Central Bank rates via Frankfurter; " +
            "AED and SAR use the official fixed rate. Jewellery adds making charges on top.",
            color = Muted, fontSize = 12.sp)

        SectionHeader("App list")
        ToggleRow("Show app icons", s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }

        SectionHeader("Gestures and shortcuts")
        GestureSlot.entries.forEach { slot ->
            ActionRow(slot.label, vm.targetLabel(s.gesture(slot)) ?: "Nothing") { picking = slot }
        }
        ActionRow(
            "Gesture service: ${if (a11yOn) "on" else "off"}",
            "Needed only for Lock screen and Notifications. " +
                "If the switch is greyed out on a sideloaded install: App info, menu, Allow restricted settings.",
        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        if (Updater.AVAILABLE) {
            SectionHeader("Updates")
            UpdateSection()
        }

        SectionHeader("Permissions")
        ActionRow(
            "Usage access: ${if (vm.hasUsageAccess) "allowed" else "not allowed"}",
            "Used for screen time and most-used apps. Data stays on the device.",
        ) { ctx.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        SectionHeader("Hidden apps")
        val hidden = vm.apps.filter { it.key in s.hidden }
        if (hidden.isEmpty()) Text("None. Long-press an app in the list to hide it.", color = Muted, fontSize = 14.sp)
        hidden.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Unhide", color = Slate, modifier = Modifier.clickable { vm.unhide(app.key) }.padding(8.dp))
            }
        }

        Text(
            "Stillpoint ${BuildConfig.VERSION_NAME}. No analytics. " +
                "Network is used only for features you switch on (weather, gold price" +
                if (Updater.AVAILABLE) ", update check)." else ").",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 32.dp),
        )
    }

    when (dialog) {
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
        SettingsDialog.CITY -> CitySearchDialog(vm, onDismiss = { dialog = null })
        null -> Unit
    }

    picking?.let { slot ->
        TargetPicker(vm, title = slot.label, onPick = { vm.setGesture(slot, it); picking = null }, onDismiss = { picking = null })
    }
}

private enum class SettingsDialog { CLOCK, GOLD_SOURCE, CURRENCY, KARAT, CITY }

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

/** Search Open-Meteo's place names; picking one also switches weather on. */
@Composable
private fun CitySearchDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
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
                        Column(Modifier.fillMaxWidth().clickable { vm.setCity(c); onDismiss() }.padding(vertical = 8.dp)) {
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
    error?.let { Text(it, color = Slate, fontSize = 13.sp) }
}
