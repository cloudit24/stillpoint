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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.HomeMode
import com.cloudit24.stillpoint.service.LockAccessibilityService

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val a11yOn = remember(vm.resumeTick) { LockAccessibilityService.isEnabled(ctx) }

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
        ToggleRow("Show screen time", s.showUsage) { on -> vm.updateSettings { it.copy(showUsage = on) } }
        ToggleRow("Show tasks", s.showTasks) { on -> vm.updateSettings { it.copy(showTasks = on) } }
        ToggleRow("Show today's calendar", s.showAgenda) { on ->
            val granted = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (on && !granted) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            else { vm.updateSettings { it.copy(showAgenda = on) }; vm.refresh() }
        }

        SectionHeader("App list")
        ToggleRow("Group by category", s.groupDrawer) { on -> vm.updateSettings { it.copy(groupDrawer = on) } }
        ToggleRow("Show app icons", s.showIcons) { on -> vm.updateSettings { it.copy(showIcons = on) } }

        SectionHeader("Gestures")
        ActionRow(
            "Gesture service: ${if (a11yOn) "on" else "off"}",
            "Needed only for double-tap lock and swipe-down notifications. " +
                "If the switch is greyed out on a sideloaded install: App info, menu, Allow restricted settings.",
        ) { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ToggleRow("Double-tap to lock", s.doubleTapLock && a11yOn, enabled = a11yOn) { on ->
            vm.updateSettings { it.copy(doubleTapLock = on) }
        }
        ToggleRow("Swipe down for notifications", s.swipeDownNotifications && a11yOn, enabled = a11yOn) { on ->
            vm.updateSettings { it.copy(swipeDownNotifications = on) }
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

        Text("Stillpoint 0.1.0. No network access, no analytics.", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 32.dp))
    }
}
