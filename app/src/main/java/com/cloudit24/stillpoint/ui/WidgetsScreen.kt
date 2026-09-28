package com.cloudit24.stillpoint.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen

/** Swipe-left page. Widgets stack vertically; Edit shows a Remove link above each. */
@Composable
fun WidgetsScreen(vm: LauncherViewModel, onAddWidget: (AppWidgetProviderInfo) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        WidgetPicker(vm) { picking = false; onAddWidget(it) }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (kotlin.math.abs(total) > threshold) vm.screen = Screen.HOME },
                    onHorizontalDrag = { change, dx ->
                        total += dx
                        change.consume()
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (vm.widgetIds.isNotEmpty()) {
                Text(if (editing) "Done" else "Edit", color = Muted,
                    modifier = Modifier.clickable { editing = !editing }.padding(8.dp))
            }
        }

        Column(Modifier.weight(1f).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
            if (vm.settings.showTasks) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 20.dp)) { TasksBlock(vm) }
            }
            if (vm.widgetIds.isEmpty()) {
                Text("No widgets yet. Tap Add widget below.", color = Muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp))
            }
            vm.widgetIds.forEach { id -> key(id) { WidgetItem(vm, id, editing) } }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp)) {
            Text("Home", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.HOME }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("Add widget", color = Muted, modifier = Modifier.clickable { editing = false; picking = true }.padding(8.dp))
        }
    }
}

@Composable
private fun WidgetItem(vm: LauncherViewModel, id: Int, editing: Boolean) {
    val context = LocalContext.current
    val info = remember(id) { vm.widgetManager.getAppWidgetInfo(id) }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (editing || info == null) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(info?.loadLabel(context.packageManager) ?: "Widget no longer available",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Remove", color = Accent, modifier = Modifier.clickable { vm.removeWidget(id) }.padding(8.dp))
            }
        }
        if (info != null) {
            // minHeight is already in pixels.
            val minHeight = with(LocalDensity.current) { info.minHeight.toDp() }
            AndroidView(
                factory = { ctx -> vm.widgetHost.createView(ctx, id, info) },
                modifier = Modifier.fillMaxWidth().height(maxOf(minHeight, 72.dp)),
            )
        }
    }
}

@Composable
private fun WidgetPicker(vm: LauncherViewModel, onPick: (AppWidgetProviderInfo) -> Unit) {
    val pm = LocalContext.current.packageManager
    val providers = remember { vm.widgetProviders() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item { Text("Add widget", fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 24.dp)) }
        if (providers.isEmpty()) {
            item { Text("No widgets found on this phone.", color = Muted, fontSize = 14.sp) }
        }
        items(providers, key = { "${it.provider.flattenToString()}#${it.profile.hashCode()}" }) { p ->
            Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp)) {
                Text(p.loadLabel(pm), fontSize = 18.sp)
                Text(appLabel(pm, p.provider.packageName), color = Muted, fontSize = 13.sp)
            }
        }
    }
}

private fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
