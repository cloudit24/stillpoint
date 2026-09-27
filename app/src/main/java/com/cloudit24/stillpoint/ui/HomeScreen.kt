package com.cloudit24.stillpoint.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AgendaItem
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.service.LockAccessibilityService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val now by rememberTicker(60_000L)
    val focusActive = s.focusEndsAt > now
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
    val homeApps = vm.homeApps()
    val favorites = vm.favoriteApps()

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(s.doubleTapLock) {
                detectTapGestures(
                    onDoubleTap = { if (s.doubleTapLock) LockAccessibilityService.lockScreen() },
                    onLongPress = { vm.screen = Screen.SETTINGS },
                )
            }
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -threshold) vm.screen = Screen.WIDGETS },
                    onHorizontalDrag = { change, dx ->
                        total += dx
                        change.consume()
                    },
                )
            }
            .pointerInput(s.swipeDownNotifications) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -threshold) vm.screen = Screen.DRAWER
                        else if (total > threshold && s.swipeDownNotifications) LockAccessibilityService.openNotifications()
                    },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        change.consume()
                    },
                )
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(formatClock(context, now), fontSize = 68.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp)
        Text(dateFmt.format(LocalDate.now()), color = Muted, fontSize = 16.sp)

        if (s.showUsage) {
            if (vm.hasUsageAccess) {
                Text(
                    "${formatDuration(vm.totalUsage)} on screen today",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text(
                    "Allow usage access to show screen time",
                    color = Slate, fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
            }
        }
        if (focusActive) {
            Text(
                "Focus until ${formatClock(context, s.focusEndsAt)}",
                color = Slate, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp).clickable { vm.screen = Screen.FOCUS },
            )
        }

        Column(Modifier.weight(1f).padding(top = 28.dp).verticalScroll(rememberScrollState())) {
            if (s.showAgenda) AgendaBlock(context, vm.agenda)
            if (s.showTasks) TasksBlock(vm)
        }

        if (favorites.isNotEmpty()) FavoritesFolder(vm, favorites)

        if (homeApps.isEmpty() && favorites.isEmpty()) {
            Text("Swipe up for apps, left for widgets. Long-press for settings.", color = Muted, fontSize = 14.sp)
        }
        homeApps.forEach { app ->
            AppRow(
                label = app.label,
                usageMs = if (s.showUsage) vm.usage[app.packageName] else null,
                fontSize = 24.sp,
                onClick = { vm.launch(app) },
                icon = appIcon(vm, app, 34.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Focus", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.FOCUS }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text("All apps", color = Muted, modifier = Modifier.clickable { vm.screen = Screen.DRAWER }.padding(8.dp))
        }
    }
}

/** Collapsible folder. Tap the title to open; long-press an app inside to remove it. */
@Composable
private fun FavoritesFolder(vm: LauncherViewModel, apps: List<AppEntry>) {
    var open by rememberSaveable { mutableStateOf(false) }

    Text(
        if (open) "Favorites  ▾" else "Favorites  ▸",
        fontSize = 24.sp,
        color = Slate,
        modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
    )
    if (open) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp)) {
            apps.forEach { app ->
                AppRow(
                    label = app.label,
                    usageMs = null,
                    fontSize = 20.sp,
                    onClick = { open = false; vm.launch(app) },
                    onLongClick = { vm.toggleFavorite(app) },
                    icon = appIcon(vm, app, 30.dp),
                )
            }
        }
    }
}

@Composable
private fun AgendaBlock(context: Context, items: List<AgendaItem>) {
    Text("Today", color = Muted, fontSize = 13.sp)
    if (items.isEmpty()) {
        Text("Nothing else scheduled", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
    items.forEach { e ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text(
                if (e.allDay) "All day" else formatClock(context, e.begin),
                color = Muted, fontSize = 15.sp, modifier = Modifier.width(76.dp),
            )
            Text(e.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksBlock(vm: LauncherViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Text("Tasks", color = Muted, fontSize = 13.sp)
    vm.tasks.forEach { t ->
        Text(
            t.text,
            fontSize = 16.sp,
            color = if (t.done) Muted else Ink,
            textDecoration = if (t.done) TextDecoration.LineThrough else null,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { vm.toggleTask(t.id) }, onLongClick = { vm.deleteTask(t.id) })
                .padding(vertical = 6.dp),
        )
    }
    BasicTextField(
        value = input,
        onValueChange = { input = it },
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
        cursorBrush = SolidColor(Slate),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            vm.addTask(input)
            input = ""
            focusManager.clearFocus()
        }),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        decorationBox = { inner ->
            if (input.isEmpty()) Text("Add a task", color = Muted, fontSize = 16.sp)
            inner()
        },
    )
}
