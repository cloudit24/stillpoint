package com.cloudit24.stillpoint.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AppEntry

private val CATEGORY_ORDER = listOf(
    "Productivity", "Social", "News", "Maps", "Audio", "Video", "Photos", "Games", "Other",
)

private fun categoryLabel(c: Int): String = when (c) {
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    ApplicationInfo.CATEGORY_GAME -> "Games"
    else -> "Other"
}

@Composable
fun DrawerScreen(vm: LauncherViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val s = vm.settings
    val apps = vm.visibleApps()
    val q = query.trim()

    val filtered = remember(apps, q) {
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }
    val groups = remember(filtered, q, s.groupDrawer) {
        if (q.isEmpty() && s.groupDrawer) {
            filtered.groupBy { categoryLabel(it.category) }
                .toList()
                .sortedBy { CATEGORY_ORDER.indexOf(it.first) }
        } else listOf("" to filtered)
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 22.sp),
                cursorBrush = SolidColor(Slate),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { vm.launch(it) } }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester).padding(vertical = 18.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search apps", color = Muted, fontSize = 22.sp)
                    inner()
                },
            )
            Text("Settings", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.SETTINGS }.padding(8.dp))
        }
        if (vm.isFocusActive()) {
            Text("Focus is on. Only allowed apps are listed.", color = Slate, fontSize = 13.sp)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            groups.forEach { (header, list) ->
                if (header.isNotEmpty()) {
                    item(key = "h_$header") { SectionHeader(header) }
                }
                items(list, key = { it.key }) { app ->
                    DrawerItem(
                        app = app,
                        vm = vm,
                        menuOpen = menuFor == app.key,
                        onMenu = { menuFor = if (it) app.key else null },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(app: AppEntry, vm: LauncherViewModel, menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
    val s = vm.settings
    Box {
        AppRow(
            label = app.label,
            usageMs = if (s.showUsage) vm.usage[app.packageName] else null,
            fontSize = 19.sp,
            onClick = { vm.launch(app) },
            onLongClick = { onMenu(true) },
            icon = appIcon(vm, app, 30.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
            DropdownMenuItem(
                text = { Text(if (app.key in s.pinned) "Unpin from home" else "Pin to home") },
                onClick = { vm.togglePin(app); onMenu(false) },
            )
            DropdownMenuItem(
                text = { Text(if (app.key in s.favorites) "Remove from Favorites" else "Add to Favorites") },
                onClick = { vm.toggleFavorite(app); onMenu(false) },
            )
            DropdownMenuItem(text = { Text("Hide") }, onClick = { vm.hide(app); onMenu(false) })
            DropdownMenuItem(text = { Text("App info") }, onClick = { vm.openAppInfo(app); onMenu(false) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { vm.uninstall(app); onMenu(false) })
        }
    }
}
