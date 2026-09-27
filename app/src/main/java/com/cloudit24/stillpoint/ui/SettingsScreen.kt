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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.BuildConfig
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.GestureSlot
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
        ToggleRow("Show tasks", s.showTasks) { on -> vm.updateSettings { it.copy(showTasks = on) } }
        ToggleRow("Show today's calendar", s.showAgenda) { on ->
            val granted = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (on && !granted) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
        }

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
                if (Updater.AVAILABLE) "Network is used only when you check for updates." else "No network access.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 32.dp),
        )
    }

    picking?.let { slot ->
        TargetPicker(vm, title = slot.label, onPick = { vm.setGesture(slot, it); picking = null }, onDismiss = { picking = null })
    }
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
