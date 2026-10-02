package com.cloudit24.stillpoint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.data.FavFolder
import kotlinx.coroutines.delay

// Tile sizes on a four-column grid, like Windows Phone: small 1x1, medium 2x2, wide 4x2.
internal const val TILE_SMALL = 0
internal const val TILE_MEDIUM = 1
internal const val TILE_WIDE = 2

private sealed interface TileItem { val key: String }
private data class AppItem(val app: AppEntry) : TileItem { override val key get() = app.key }
private data class FolderItem(val folder: FavFolder) : TileItem { override val key get() = "folder:${folder.id}" }

/** A 2x2 slot holds one medium tile or up to four small ones; a wide tile takes a whole row. */
private sealed interface Block { val key: String }
private class One(val item: TileItem) : Block { override val key get() = item.key }
private class Smalls(val items: List<TileItem>) : Block { override val key get() = items.joinToString("+") { it.key } }
private class Wide(val item: TileItem) : Block { override val key get() = item.key }

private fun blocks(items: List<TileItem>, size: (TileItem) -> Int): List<Block> {
    val out = mutableListOf<Block>()
    var smalls = mutableListOf<TileItem>()
    fun flush() {
        if (smalls.isNotEmpty()) out += Smalls(smalls)
        smalls = mutableListOf()
    }
    items.forEach {
        when (size(it)) {
            TILE_SMALL -> { smalls += it; if (smalls.size == 4) flush() }
            TILE_WIDE -> { flush(); out += Wide(it) }
            else -> { flush(); out += One(it) }
        }
    }
    flush()
    return out
}

private fun rows(blocks: List<Block>): List<List<Block>> {
    val rows = mutableListOf<List<Block>>()
    var cur = mutableListOf<Block>()
    blocks.forEach { b ->
        if (b is Wide) {
            if (cur.isNotEmpty()) rows += cur
            cur = mutableListOf()
            rows += listOf(b)
        } else {
            cur += b
            if (cur.size == 2) { rows += cur; cur = mutableListOf() }
        }
    }
    if (cur.isNotEmpty()) rows += cur
    return rows
}

/**
 * Favorites as live tiles: folders and apps on a four-column grid, in the accent style.
 * An app with unread notifications flips now and then to show how many. Hold a tile to resize or move it.
 */
@Composable
fun FavoritesTiles(vm: LauncherViewModel, onNewFolder: () -> Unit, onRename: (FavFolder) -> Unit, onPickFolder: (AppEntry) -> Unit) {
    var openId by remember { mutableStateOf<Long?>(null) }
    val folder = vm.settings.folders.firstOrNull { it.id == openId }
    BackHandler(enabled = folder != null) { openId = null }
    val items: List<TileItem> = if (folder != null) vm.folderApps(folder).map { AppItem(it) }
    else vm.settings.folders.map { FolderItem(it) } + vm.favoriteApps().map { AppItem(it) }
    val sizes = vm.settings.tileSizes
    val grid = remember(items, sizes) { rows(blocks(items) { sizes[it.key] ?: TILE_MEDIUM }) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(end = 16.dp)) {
        val gap = 8.dp
        val unit = (maxWidth - gap * 3) / 4
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (folder != null) item(key = "back") {
                Text("‹  ${folder.name}", fontSize = 22.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable { openId = null }.padding(vertical = 6.dp))
            }
            if (items.isEmpty()) item(key = "empty") {
                Text(if (folder != null) "Empty. Long-press an app and choose Move to folder."
                    else "No favorites yet. Long-press any app and choose Add to Favorites.",
                    color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            grid.forEach { row ->
                item(key = row.joinToString("|") { it.key }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { b ->
                            when (b) {
                                is Wide -> Tile(vm, b.item, TILE_WIDE, unit * 4 + gap * 3, unit * 2 + gap, { openId = it }, onRename, onPickFolder)
                                is One -> Tile(vm, b.item, TILE_MEDIUM, unit * 2 + gap, unit * 2 + gap, { openId = it }, onRename, onPickFolder)
                                is Smalls -> Column(Modifier.size(unit * 2 + gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                                    b.items.chunked(2).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            pair.forEach { Tile(vm, it, TILE_SMALL, unit, unit, { id -> openId = id }, onRename, onPickFolder) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (folder == null) item(key = "new_folder") {
                Text("+ New folder", color = Muted, fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onNewFolder).padding(vertical = 14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    vm: LauncherViewModel, item: TileItem, kind: Int, w: Dp, h: Dp,
    onOpenFolder: (Long) -> Unit, onRename: (FavFolder) -> Unit, onPickFolder: (AppEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (kind == TILE_SMALL) 12.dp else 16.dp)
    Box(Modifier.size(w, h)) {
        Box(
            Modifier.size(w, h).clip(shape).combinedClickable(
                onClick = {
                    when (item) {
                        is AppItem -> vm.launch(item.app)
                        is FolderItem -> onOpenFolder(item.folder.id)
                    }
                },
                onLongClick = { menu = true },
            ),
        ) {
            when (item) {
                is AppItem -> AppFace(vm, item.app, kind)
                is FolderItem -> FolderFace(vm, item.folder, kind)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(TILE_SMALL to "Small", TILE_MEDIUM to "Medium", TILE_WIDE to "Wide")
                .filter { (k, _) -> k != kind && !(item is FolderItem && k == TILE_SMALL) }
                .forEach { (k, label) ->
                    DropdownMenuItem(text = { Text("Size: $label") }, onClick = { menu = false; vm.setTileSize(item.key, k) })
                }
            when (item) {
                is AppItem -> {
                    DropdownMenuItem(text = { Text("Move to folder…") }, onClick = { menu = false; onPickFolder(item.app) })
                    DropdownMenuItem(text = { Text("Remove from Favorites") }, onClick = { menu = false; vm.removeFavorite(item.app) })
                    DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; vm.openAppInfo(item.app) })
                }
                is FolderItem -> {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename(item.folder) })
                    DropdownMenuItem(text = { Text("Delete folder (apps stay in Favorites)") },
                        onClick = { menu = false; vm.deleteFolder(item.folder.id) })
                }
            }
        }
    }
}

/** An app tile. With unread notifications it flips every few seconds: the icon, then the count. */
@Composable
private fun AppFace(vm: LauncherViewModel, app: AppEntry, kind: Int) {
    val count = vm.notifyCount(app.packageName)
    var back by remember { mutableStateOf(false) }
    LaunchedEffect(count > 0) {
        back = false
        while (count > 0) {
            delay(4_000)
            back = !back
        }
    }
    val turn by animateFloatAsState(if (back && count > 0) 180f else 0f, tween(600), label = "flip")
    val fill = AccentFill
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationX = turn
            cameraDistance = 14 * density
        }.background(fill),
    ) {
        if (turn <= 90f) {
            Box(Modifier.align(Alignment.Center)) { AppIcon(vm, app, if (kind == TILE_SMALL) 30.dp else 44.dp) }
            if (kind != TILE_SMALL) TileLabel(app.label)
        } else {
            // The back is drawn turned over, so it reads the right way up at the end of the flip.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationX = 180f }) {
                if (kind == TILE_SMALL) {
                    Text("$count", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(Modifier.padding(12.dp)) {
                        Text("$count", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
                        Text("new", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    TileLabel(app.label)
                }
            }
        }
    }
}

/** A folder tile: up to four of its apps, with its name and how many are inside. */
@Composable
private fun FolderFace(vm: LauncherViewModel, f: FavFolder, kind: Int) {
    val inside = vm.folderApps(f)
    Box(Modifier.fillMaxSize().background(AccentFill)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            inside.take(4).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { AppIcon(vm, it, 26.dp) }
                }
            }
        }
        TileLabel("${f.name} · ${inside.size}")
    }
}

@Composable
private fun BoxScope.TileLabel(text: String) {
    Text(text, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 9.dp))
}
