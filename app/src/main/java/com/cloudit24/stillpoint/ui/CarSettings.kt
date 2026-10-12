package com.cloudit24.stillpoint.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.Screen

/** Settings → Productivity → Car mode. */
@Composable
fun CarSettings(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var pick by remember { mutableStateOf<String?>(null) }

    Group(stringResource(R.string.c_start)) {
        ToggleRow(stringResource(R.string.c_on), s.carOn) { on -> vm.updateSettings { it.copy(carOn = on) } }
        if (s.carOn) {
            ActionRow(stringResource(R.string.c_bluetooth), s.carBluetoothName.ifEmpty { stringResource(R.string.c_bluetooth_none) }) { pick = "bt" }
            ActionRow(stringResource(R.string.c_open_now)) { vm.screen = Screen.CAR }
        }
        Note(stringResource(R.string.c_start_note))
    }

    if (s.carOn) Group(stringResource(R.string.c_on_screen)) {
        ToggleRow(stringResource(R.string.c_mph), s.carMph) { on -> vm.updateSettings { it.copy(carMph = on) } }
        Stepper(stringResource(R.string.c_limit), if (s.carLimit > 0) "${s.carLimit} km/h" else stringResource(R.string.c_limit_off),
            onMinus = { vm.updateSettings { it.copy(carLimit = (it.carLimit - 10).coerceAtLeast(0)) } },
            onPlus = { vm.updateSettings { it.copy(carLimit = (it.carLimit + 10).coerceAtMost(200)) } })
        ToggleRow(stringResource(R.string.c_osm), s.carOsmLimit) { on -> vm.updateSettings { it.copy(carOsmLimit = on) } }
        Note(stringResource(R.string.c_osm_note))
        ToggleRow(stringResource(R.string.c_cameras), s.carCameras) { on -> vm.updateSettings { it.copy(carCameras = on) } }
        Note(stringResource(R.string.c_cameras_note))
        ToggleRow(stringResource(R.string.c_glass), s.carGlass) { on -> vm.updateSettings { it.copy(carGlass = on) } }
        Note(stringResource(R.string.c_glass_note))
        val names = s.carApps.mapNotNull { k -> vm.apps.firstOrNull { it.key == k }?.label }
        ActionRow(stringResource(R.string.c_apps), names.ifEmpty { listOf(stringResource(R.string.c_apps_none)) }.joinToString(", ")) { pick = "apps" }
        ToggleRow(stringResource(R.string.c_important_only), s.carImportantOnly) { on -> vm.updateSettings { it.copy(carImportantOnly = on) } }
        ToggleRow(stringResource(R.string.c_voice), s.carVoice) { on -> vm.updateSettings { it.copy(carVoice = on) } }
        if (s.carVoice && !s.assistantOn) Note(stringResource(R.string.c_voice_needs_assistant))
        ToggleRow(stringResource(R.string.c_keep_on), s.carKeepOn) { on -> vm.updateSettings { it.copy(carKeepOn = on) } }
        Note(stringResource(R.string.c_battery_note))
    }

    when (pick) {
        "apps" -> AppMultiPicker(vm, stringResource(R.string.c_apps), s.carApps.toSet(), onDismiss = { pick = null }) { k, on ->
            vm.updateSettings { it.copy(carApps = if (on) (it.carApps + k).distinct().take(4) else it.carApps - k) }
        }
        "bt" -> BluetoothPicker(onDismiss = { pick = null }) { name, addr -> vm.updateSettings { it.copy(carBluetoothName = name, carBluetoothAddr = addr) } }
    }
}

/** Your paired Bluetooth devices; the car is one of them. Needs the Bluetooth permission on Android 12+. */
@Composable
private fun BluetoothPicker(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val needs = Build.VERSION.SDK_INT >= 31
    var allowed by remember { mutableStateOf(!needs || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val devices = remember(allowed) {
        if (!allowed) emptyList() else runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.map { (it.name ?: it.address) to it.address }.orEmpty().sortedBy { it.first }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        dismissButton = { TextButton(onClick = { onPick("", ""); onDismiss() }) { Text(stringResource(R.string.c_bluetooth_none)) } },
        title = { Text(stringResource(R.string.c_bluetooth)) },
        text = {
            if (!allowed) Text(stringResource(R.string.c_bluetooth_permission), modifier = Modifier.clickable { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) })
            else if (devices.isEmpty()) Text(stringResource(R.string.c_bluetooth_empty), color = Muted)
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(devices) { (name, addr) ->
                    Text(name, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(name, addr); onDismiss() }.padding(vertical = 10.dp))
                }
            }
        },
    )
}
