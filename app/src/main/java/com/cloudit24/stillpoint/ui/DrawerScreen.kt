package com.cloudit24.stillpoint.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.data.FavFolder
import kotlinx.coroutines.launch

private enum class DrawerTab(val label: String) {
    MOST("Most used"), RECENT("Recent"), ALL("All"), FAVORITES("Favorites"),
}

/** Which small dialog is open on top of the drawer. */
private sealed interface DrawerDialog {
    data class PickFolder(val app: AppEntry) : DrawerDialog
    data class NewFolder(val moveIn: AppEntry?) : DrawerDialog
    data class Rename(val folder: FavFolder) : DrawerDialog
}

@Composable
fun DrawerScreen(vm: LauncherViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(DrawerTab.ALL) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<DrawerDialog?>(null) }
    val focusRequester = remember { FocusRequester() }
    val apps = vm.visibleApps()
    val q = query.trim()

    val filtered = remember(apps, q) {
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // One row renderer for every tab so the long-press menu is identical everywhere.
    val row: @Composable (AppEntry, String?, String) -> Unit = { app, trailing, keyPrefix ->
        val id = keyPrefix + app.key
        DrawerItem(
            app = app,
            vm = vm,
            trailing = trailing,
            menuOpen = menuFor == id,
            onMenu = { menuFor = if (it) id else null },
            onPickFolder = { dialog = DrawerDialog.PickFolder(app) },
        )
    }

    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 12.dp)) {
        Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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

        if (q.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                DrawerTab.entries.forEach { t ->
                    Text(
                        t.label,
                        color = if (t == tab) Ink else Muted,
                        fontSize = 15.sp,
                        fontWeight = if (t == tab) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.clickable { tab = t; menuFor = null }.padding(end = 18.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                q.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp)) {
                    items(filtered, key = { it.key }) { row(it, null, "") }
                }
                tab == DrawerTab.MOST -> MostUsedTab(vm, row)
                tab == DrawerTab.RECENT -> LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp)) {
                    items(vm.recentApps(), key = { it.key }) { row(it, formatAge(it.installedAt), "") }
                }
                tab == DrawerTab.ALL -> AlphabetList(filtered) { row(it, null, "") }
                else -> FavoritesTab(
                    vm = vm,
                    row = row,
                    onNewFolder = { dialog = DrawerDialog.NewFolder(null) },
                    onRename = { dialog = DrawerDialog.Rename(it) },
                )
            }
        }
    }

    when (val d = dialog) {
        is DrawerDialog.PickFolder -> FolderPicker(
            vm = vm,
            onPick = { folderId -> vm.moveToFolder(d.app, folderId); dialog = null },
            onNew = { dialog = DrawerDialog.NewFolder(d.app) },
            onDismiss = { dialog = null },
        )
        is DrawerDialog.NewFolder -> NameDialog("New folder", "") { name ->
            if (name != null) vm.createFolder(name)?.let { id -> d.moveIn?.let { vm.moveToFolder(it, id) } }
            dialog = null
        }
        is DrawerDialog.Rename -> NameDialog("Rename folder", d.folder.name) { name ->
            if (name != null) vm.renameFolder(d.folder.id, name)
            dialog = null
        }
        null -> Unit
    }
}

@Composable
private fun MostUsedTab(vm: LauncherViewModel, row: @Composable (AppEntry, String?, String) -> Unit) {
    val context = LocalContext.current
    if (!vm.hasUsageAccess) {
        Text(
            "Allow usage access to see your most used apps",
            color = Slate, fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 12.dp)
                .clickable { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
        return
    }
    val list = vm.mostUsedApps()
    LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp)) {
        item { Text("Last 7 days", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        if (list.isEmpty()) item { Text("No usage recorded yet.", color = Muted, fontSize = 15.sp) }
        items(list, key = { it.key }) { row(it, formatDuration(vm.weekUsage[it.packageName] ?: 0L), "") }
    }
}

// ---- All: alphabetical sections + index bar ----

/** First letter in any script (so Arabic names get their own letters); digits and symbols go under "#". */
private fun sectionOf(label: String): String {
    val c = label.trimStart().firstOrNull() ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

@Composable
private fun AlphabetList(apps: List<AppEntry>, row: @Composable (AppEntry) -> Unit) {
    val sections = remember(apps) {
        apps.groupBy { sectionOf(it.label) }.toList().sortedBy { (k, _) -> if (k == "#") "" else k }
    }
    // Index of each section header inside the lazy list.
    val headerIndex = remember(sections) {
        var i = 0
        sections.associate { (k, list) -> (k to i).also { i += 1 + list.size } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf<String?>(null) }
    val scrolledSection by remember(headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headerIndex.entries.lastOrNull { it.value <= first }?.key
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 28.dp)) {
            sections.forEach { (letter, list) ->
                item(key = "h_$letter") {
                    Text(letter, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                }
                items(list, key = { it.key }) { row(it) }
            }
        }

        if (sections.size > 1) {
            IndexBar(
                letters = sections.map { it.first },
                modifier = Modifier.align(Alignment.CenterEnd),
                onLetter = { letter ->
                    if (letter != touched) {
                        touched = letter
                        headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                    }
                },
                onRelease = { touched = null },
            )
        }

        // Big letter while dragging the index bar or flinging the list.
        val bubble = touched ?: scrolledSection?.takeIf { listState.isScrollInProgress }
        if (bubble != null) {
            Box(
                Modifier.align(Alignment.Center).size(88.dp).background(Color(0xFF1E1E1C), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(bubble, fontSize = 44.sp, fontWeight = FontWeight.Light, color = Ink) }
        }
    }
}

@Composable
private fun IndexBar(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit, onRelease: () -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    fun letterAt(y: Float): String =
        letters[((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)]

    Column(
        modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onLetter(letterAt(down.position.y))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        onLetter(letterAt(change.position.y))
                    }
                    onRelease()
                }
            },
    ) {
        letters.forEach { l ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(l, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

// ---- Favorites: folders + loose apps ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesTab(
    vm: LauncherViewModel,
    row: @Composable (AppEntry, String?, String) -> Unit,
    onNewFolder: () -> Unit,
    onRename: (FavFolder) -> Unit,
) {
    var open by remember { mutableStateOf(setOf<Long>()) }
    var folderMenu by remember { mutableStateOf<Long?>(null) }
    val folders = vm.settings.folders
    val loose = vm.favoriteApps()

    LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp)) {
        if (folders.isEmpty() && loose.isEmpty()) {
            item {
                Text("No favorites yet. Long-press any app and choose Add to Favorites.",
                    color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        folders.forEach { f ->
            val isOpen = f.id in open
            val inside = vm.folderApps(f)
            item(key = "folder_${f.id}") {
                Box {
                    Text(
                        (if (isOpen) "▾  " else "▸  ") + f.name + "  · " + inside.size,
                        fontSize = 19.sp,
                        color = Slate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { open = if (isOpen) open - f.id else open + f.id },
                                onLongClick = { folderMenu = f.id },
                            )
                            .padding(vertical = 10.dp),
                    )
                    DropdownMenu(expanded = folderMenu == f.id, onDismissRequest = { folderMenu = null }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { folderMenu = null; onRename(f) })
                        DropdownMenuItem(
                            text = { Text("Delete folder (apps stay in Favorites)") },
                            onClick = { folderMenu = null; vm.deleteFolder(f.id) },
                        )
                    }
                }
            }
            if (isOpen) {
                if (inside.isEmpty()) {
                    item(key = "empty_${f.id}") {
                        Text("Empty. Long-press an app and choose Move to folder.", color = Muted, fontSize = 14.sp,
                            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                    }
                }
                items(inside, key = { "f${f.id}_${it.key}" }) { app ->
                    Box(Modifier.padding(start = 20.dp)) { row(app, null, "f${f.id}_") }
                }
            }
        }
        items(loose, key = { "fav_${it.key}" }) { row(it, null, "fav_") }
        item(key = "new_folder") {
            Text("+ New folder", color = Muted, fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onNewFolder).padding(vertical = 14.dp))
        }
    }
}

@Composable
private fun DrawerItem(
    app: AppEntry,
    vm: LauncherViewModel,
    trailing: String?,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
) {
    val s = vm.settings
    val favorite = vm.isFavorite(app)
    Box {
        AppRow(
            label = app.label,
            usageMs = if (s.showUsage) vm.usage[app.packageName] else null,
            fontSize = 19.sp,
            onClick = { vm.launch(app) },
            onLongClick = { onMenu(true) },
            icon = appIcon(vm, app, 30.dp),
            trailing = trailing,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
            DropdownMenuItem(
                text = { Text(if (app.key in s.pinned) "Unpin from home" else "Pin to home") },
                onClick = { vm.togglePin(app); onMenu(false) },
            )
            DropdownMenuItem(
                text = { Text(if (favorite) "Remove from Favorites" else "Add to Favorites") },
                onClick = { if (favorite) vm.removeFavorite(app) else vm.addFavorite(app); onMenu(false) },
            )
            DropdownMenuItem(text = { Text("Move to folder…") }, onClick = { onMenu(false); onPickFolder() })
            DropdownMenuItem(text = { Text("Hide") }, onClick = { vm.hide(app); onMenu(false) })
            DropdownMenuItem(text = { Text("App info") }, onClick = { vm.openAppInfo(app); onMenu(false) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { vm.uninstall(app); onMenu(false) })
        }
    }
}

@Composable
private fun FolderPicker(vm: LauncherViewModel, onPick: (Long?) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onNew) { Text("New folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Move to") },
        text = {
            Column {
                Text("Favorites (no folder)", fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp))
                vm.settings.folders.forEach { f ->
                    Text(f.name, fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(f.id) }.padding(vertical = 10.dp))
                }
            }
        },
    )
}

/** Calls [onDone] with the entered name, or null when cancelled. */
@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Name") }) },
    )
}
