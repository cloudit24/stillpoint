@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.cloudit24.stillpoint.ui

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import com.cloudit24.stillpoint.data.DrawerTab
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

/** Which small dialog is open on top of the drawer. */
private sealed interface DrawerDialog {
    data class PickFolder(val app: AppEntry) : DrawerDialog
    data class NewFolder(val moveIn: AppEntry?) : DrawerDialog
    data class Rename(val folder: FavFolder) : DrawerDialog
}

/**
 * App list in Windows Phone style: swipeable pivot tabs with big lowercase headers.
 * Search is a round button at the bottom right; the keyboard only opens when it is tapped.
 */
@Composable
fun DrawerScreen(vm: LauncherViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(vm.openSearch.also { vm.openSearch = false }) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<DrawerDialog?>(null) }
    val tabs = DrawerTab.entries
    val pager = rememberPagerState(initialPage = vm.settings.drawerStart.ordinal) { tabs.size }
    val apps = vm.visibleApps()
    val q = query.trim()

    val filtered = remember(apps, q) {
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }

    BackHandler(enabled = searching) { searching = false; query = "" }

    // Swipe right past the first tab, or pull down at the top of a list, to go back home.
    val backThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val backToHome = remember(pager) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.x > 0f && pager.currentPage == 0) pull += available.x
                if (available.y > 0f) pull += available.y
                if (pull > backThreshold) {
                    pull = 0f
                    vm.screen = Screen.HOME
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pull = 0f
                return Velocity.Zero
            }
        }
    }

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

    Box(Modifier.fillMaxSize()) {
        if (searching) {
            SearchPanel(
                query = query,
                onQuery = { query = it },
                results = filtered,
                onGo = { filtered.firstOrNull()?.let { vm.launch(it) } },
                onClose = { searching = false; query = "" },
                row = { row(it, null, "s_") },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                PivotHeaders(tabs.map { it.label }, pager)
                if (vm.isFocusActive()) {
                    Text("Focus is on. Only allowed apps are listed.", color = Accent, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp))
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).nestedScroll(backToHome)) { page ->
                    Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 12.dp)) {
                        when (tabs[page]) {
                            DrawerTab.MOST -> MostUsedTab(vm, row)
                            DrawerTab.RECENT -> LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp), contentPadding = ListBottom) {
                                // Used in the last 24 hours; newly installed apps when there's no usage access yet.
                                val used = vm.recentlyUsed()
                                if (used.isNotEmpty()) items(used, key = { it.key }) { row(it, usedAgo(vm.lastUsed[it.packageName] ?: 0L), "") }
                                else items(vm.recentApps(), key = { it.key }) { row(it, formatAge(it.installedAt), "") }
                            }
                            DrawerTab.ALL -> AlphabetList(apps) { row(it, null, "") }
                            DrawerTab.FAVORITES -> FavoritesTab(
                                vm = vm,
                                row = row,
                                onNewFolder = { dialog = DrawerDialog.NewFolder(null) },
                                onRename = { dialog = DrawerDialog.Rename(it) },
                            )
                        }
                    }
                }
            }
            Text("settings", color = Muted, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 20.dp)
                    .clickable { vm.screen = Screen.SETTINGS }.padding(12.dp))
            RoundButton(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp), filled = true,
                onClick = { searching = true; menuFor = null }) { SearchGlyph() }
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

/** Room under each list so the last app isn't hidden behind the search button. */
private val ListBottom = PaddingValues(bottom = 96.dp)

/** Big lowercase tab titles; the current one is bright and scrolled to the left edge. Tap to jump. */
@Composable
private fun PivotHeaders(labels: List<String>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val offsets = remember { mutableStateMapOf<Int, Int>() }
    val inset = with(LocalDensity.current) { 28.dp.roundToPx() }
    LaunchedEffect(pager.currentPage) {
        offsets[pager.currentPage]?.let { scroll.animateScrollTo((it - inset).coerceAtLeast(0)) }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(start = 28.dp, top = 20.dp, bottom = 10.dp, end = 200.dp)) {
        labels.forEachIndexed { i, label ->
            val color by animateColorAsState(if (i == pager.currentPage) Ink else Muted.copy(alpha = 0.55f), label = "pivot")
            Text(
                label, color = color, fontSize = 40.sp, fontWeight = FontWeight.Light, maxLines = 1,
                modifier = Modifier
                    .onGloballyPositioned { offsets[i] = it.positionInParent().x.toInt() }
                    .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    .padding(end = 22.dp),
            )
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    results: List<AppEntry>,
    onGo: () -> Unit,
    onClose: () -> Unit,
    row: @Composable (AppEntry) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val close = { keyboard?.hide(); onClose() }

    Column(Modifier.fillMaxSize()) {
        Text("search", fontSize = 40.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 10.dp))
        LazyColumn(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp)) {
            if (query.isNotBlank() && results.isEmpty()) {
                item { Text("No apps match.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            if (query.isNotBlank()) items(results, key = { it.key }) { row(it) }
        }
        // Search box sits at the bottom, just above the keyboard, within thumb reach.
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 22.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    .border(1.dp, Muted.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search apps", color = Muted, fontSize = 22.sp)
                    inner()
                },
            )
            Spacer(Modifier.width(14.dp))
            RoundButton(filled = false, onClick = close) { CloseGlyph() }
        }
    }
}

/** Windows Phone style round app-bar button. */
@Composable
fun RoundButton(modifier: Modifier = Modifier, filled: Boolean, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val accent = Accent
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(accent) else Modifier.border(2.dp, Ink, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

@Composable
private fun SearchGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.4.dp.toPx()
        val r = size.minDimension * 0.33f
        val c = Offset(r + stroke, r + stroke)
        drawCircle(Color.White, r, c, style = Stroke(stroke))
        drawLine(Color.White, Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun CloseGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(Ink, Offset.Zero, Offset(size.width, size.height), stroke, cap = StrokeCap.Round)
        drawLine(Ink, Offset(size.width, 0f), Offset(0f, size.height), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun MostUsedTab(vm: LauncherViewModel, row: @Composable (AppEntry, String?, String) -> Unit) {
    val context = LocalContext.current
    if (!vm.hasUsageAccess) {
        Text(
            "Allow usage access to see your most used apps",
            color = Accent, fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 12.dp)
                .clickable { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
        return
    }
    val list = vm.mostUsedApps()
    LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp), contentPadding = ListBottom) {
        item { Text("Last 7 days", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        if (list.isEmpty()) item { Text("No usage recorded yet.", color = Muted, fontSize = 15.sp) }
        items(list, key = { it.key }) { row(it, formatDuration(vm.weekUsage[it.packageName] ?: 0L), "") }
    }
}

// ---- All: letter tiles, jump grid and index bar ----

/** First letter in any script (so Arabic names get their own letters); digits and symbols go under "#". */
private fun sectionOf(label: String): String {
    val c = label.trimStart().firstOrNull() ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlphabetList(apps: List<AppEntry>, row: @Composable (AppEntry) -> Unit) {
    val accent = Accent
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
    var jumpOpen by remember { mutableStateOf(false) }
    val scrolledSection by remember(headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headerIndex.entries.lastOrNull { it.value <= first }?.key
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 28.dp), contentPadding = ListBottom) {
            sections.forEach { (letter, list) ->
                item(key = "h_$letter") {
                    // Tap a letter tile for the jump grid, like Windows Phone.
                    Box(
                        Modifier.padding(top = 14.dp, bottom = 4.dp).size(42.dp).background(accent)
                            .clickable { jumpOpen = true },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(letter.lowercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(start = 6.dp, bottom = 1.dp))
                    }
                }
                items(list, key = { it.key }) { row(it) }
            }
        }

        if (sections.size > 1) {
            IndexBar(
                letters = sections.map { it.first },
                modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 88.dp),
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
        if (bubble != null && !jumpOpen) {
            Box(
                Modifier.align(Alignment.Center).size(88.dp).background(accent),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(bubble.lowercase(), fontSize = 48.sp, fontWeight = FontWeight.Light, color = Color.White,
                    modifier = Modifier.padding(start = 10.dp))
            }
        }

        if (jumpOpen) {
            BackHandler { jumpOpen = false }
            val letters = remember(sections) {
                val present = sections.map { it.first }
                (listOf("#") + ('A'..'Z').map { it.toString() } + present.filter { it != "#" && it.single() !in 'A'..'Z' }).distinct()
            }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
                    .pointerInput(Unit) { detectTapGestures { jumpOpen = false } },
            ) {
                FlowRow(
                    Modifier.padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    letters.forEach { l ->
                        val has = l in headerIndex
                        Box(
                            Modifier.size(62.dp).background(if (has) accent else Color(0xFF1E1E1C))
                                .clickable(enabled = has) {
                                    jumpOpen = false
                                    headerIndex[l]?.let { scope.launch { listState.scrollToItem(it) } }
                                },
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(l.lowercase(), fontSize = 30.sp, fontWeight = FontWeight.Light,
                                color = if (has) Color.White else Muted.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                        }
                    }
                }
            }
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

    LazyColumn(Modifier.fillMaxSize().padding(end = 16.dp), contentPadding = ListBottom) {
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
                        color = Accent,
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

private fun usedAgo(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000L
    return when {
        min < 1 -> "now"
        min < 60 -> "${min}m ago"
        else -> "${min / 60}h ago"
    }
}
