package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.AppDataUsage
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private enum class DataPeriod(val label: String) { TODAY("Today"), WEEK("7 days"), MONTH("30 days") }

/** Per-app data use from Android's own records, read once when the screen or period changes. */
@Composable
fun DataUsageScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(DataPeriod.TODAY) }

    val rows by produceState<List<AppDataUsage>?>(null, period, vm.resumeTick) {
        value = null
        val end = System.currentTimeMillis()
        val start = when (period) {
            DataPeriod.TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            DataPeriod.WEEK -> end - 7 * 86_400_000L
            DataPeriod.MONTH -> end - 30 * 86_400_000L
        }
        value = vm.dataUsage(start, end)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.s_data_usage), fontSize = 34.sp, fontWeight = FontWeight.Light)

        if (!vm.hasUsageAccess) {
            Text(
                stringResource(R.string.s_allow_usage_access_to_see_data),
                color = Accent, fontSize = 15.sp,
                modifier = Modifier.padding(top = 16.dp)
                    .clickable { context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
            return@Column
        }

        Row(Modifier.padding(top = 12.dp)) {
            DataPeriod.entries.forEach { p ->
                Text(
                    p.label,
                    color = if (p == period) Ink else Muted,
                    fontWeight = if (p == period) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clickable { period = p }.padding(end = 18.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }

        val list = rows
        if (list == null) {
            Text(stringResource(R.string.s_reading), color = Muted, modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        Text(
            "Wi-Fi ${formatBytes(list.sumOf { it.wifi })}  ·  Mobile ${formatBytes(list.sumOf { it.mobile })}",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (list.isEmpty()) Text(stringResource(R.string.s_no_data_used_in_this_period), color = Muted)

        LazyColumn(Modifier.weight(1f)) {
            items(list.take(60), key = { it.label }) { u ->
                val app = u.packageName?.let { pkg -> vm.apps.find { it.packageName == pkg } }
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (app != null) Modifier.clickable { vm.openAppInfo(app) } else Modifier)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app != null) {
                        appIcon(vm, app, 30.dp)?.let { icon ->
                            icon()
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(u.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                u.wifi.takeIf { it > 0 }?.let { "Wi-Fi ${formatBytes(it)}" },
                                u.mobile.takeIf { it > 0 }?.let { "Mobile ${formatBytes(it)}" },
                            ).joinToString("  ·  "),
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                    Text(formatBytes(u.total), fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Text(stringResource(R.string.s_from_android_s_own_records_mobile),
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", b / 1_073_741_824)
        b >= 1_048_576 -> String.format(Locale.US, "%.0f MB", b / 1_048_576)
        else -> String.format(Locale.US, "%.0f KB", b / 1024)
    }
}
