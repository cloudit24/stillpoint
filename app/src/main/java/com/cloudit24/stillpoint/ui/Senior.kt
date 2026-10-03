package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.AppEntry
import com.cloudit24.stillpoint.data.PrayerTimes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TileColor = Color(0xFF1C1C1A)
private val Soft = Color(0xFFC9C6BF)

/**
 * Senior mode home: a big clock and date, six large tiles, an optional call button and a big All apps button.
 * No hidden gestures, and holding a tile does nothing, so nothing moves by accident. Hold the clock for Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeniorHome(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val now by rememberTicker(15_000)
    val battery = remember(now) {
        context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    val next = remember(now / 60_000, s.city, s.prayerOn) {
        s.city?.takeIf { s.prayerOn }?.let { c -> PrayerTimes.next(now, c.lat, c.lon, s.prayerMethod, s.asrHanafi) }
    }
    var picking by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text(formatClock(context, now), fontSize = 64.sp, fontWeight = FontWeight.Light, color = Color.White,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { vm.screen = Screen.SETTINGS }))
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), fontSize = 22.sp, color = Soft)
        val line = listOfNotNull(
            next?.let { "${it.first.label} at ${formatClock(context, it.second)}" },
            if (battery >= 0) "Battery $battery%" else null,
        ).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, fontSize = 18.sp, color = Accent, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(20.dp))
        (0 until 6).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { i ->
                    val app = vm.seniorApp(i)
                    Column(
                        Modifier.weight(1f).height(118.dp).clip(RoundedCornerShape(20.dp)).background(TileColor)
                            .clickable { if (app != null) vm.launch(app) else picking = i }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (app != null) {
                            AppIcon(vm, app, 48.dp)
                            Text(app.label, fontSize = 18.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("+", fontSize = 34.sp, color = Muted)
                            Text(stringResource(R.string.s_add_an_app), fontSize = 15.sp, color = Muted)
                        }
                    }
                }
            }
        }
        if (s.seniorCallNumber.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A1D1B))
                    .clickable { context.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.seniorCallNumber.filter { it.isDigit() || it == '+' }))) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color(0xFFF08A7A))
                Text("Call ${s.seniorCallName.ifBlank { s.seniorCallNumber }}", fontSize = 20.sp, color = Color(0xFFF6C4BB),
                    modifier = Modifier.padding(start = 14.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        BigButton(stringResource(R.string.s_all_apps)) { vm.screen = Screen.DRAWER }
        Text(stringResource(R.string.s_hold_the_clock_for_settings), fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
    picking?.let { i ->
        SeniorAppPicker(vm, onDismiss = { picking = null }) { app -> vm.setSeniorApp(i, app?.key); picking = null }
    }
}

/** Senior mode app list: a big search box and one plain A to Z list with large rows. */
@Composable
fun SeniorApps(vm: LauncherViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val apps = vm.visibleApps()
    val q = query.trim()
    val shown = remember(apps, q) { if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text(stringResource(R.string.s_all_apps), fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color.White)
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp), cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(TileColor)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(stringResource(R.string.s_search_2), color = Muted, fontSize = 20.sp)
                    inner()
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { vm.launch(app) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(vm, app, 40.dp)
                    Text(app.label, fontSize = 20.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton(stringResource(R.string.s_back_to_home)) { vm.screen = Screen.HOME }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, Color(0xFF55544F), RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, color = Color.White) }
}

/** Choose the app for a senior-mode tile, or leave it empty. */
@Composable
fun SeniorAppPicker(vm: LauncherViewModel, onDismiss: () -> Unit, onPick: (AppEntry?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.s_empty)) } },
        title = { Text(stringResource(R.string.s_choose_an_app)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(vm.visibleApps(), key = { it.key }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(vm, app, 32.dp)
                        Text(app.label, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
    )
}
