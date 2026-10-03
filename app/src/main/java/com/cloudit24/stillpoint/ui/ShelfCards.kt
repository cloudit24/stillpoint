package com.cloudit24.stillpoint.ui

import androidx.compose.ui.res.stringResource
import com.cloudit24.stillpoint.R
import android.app.ActivityManager
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.BuiltIn
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.LauncherSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** What a Stillpoint card on the Shelf shows. */
@Composable
fun BuiltInContent(vm: LauncherViewModel, kind: BuiltIn, id: Int) {
    val s = vm.settings
    when (kind) {
        BuiltIn.NOTES -> NotesCard(vm)
        BuiltIn.TASKS, BuiltIn.PROJECTS -> Unit // Retired: moved to the Project Hub app.
        BuiltIn.CALENDAR -> MonthCard(s)
        BuiltIn.CLOCKS -> {
            val now by rememberTicker(30_000)
            CardTitle(stringResource(R.string.s_world_clock))
            WorldInfo(s, now, Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
        BuiltIn.BATTERY -> BatteryCard()
        BuiltIn.WEATHER -> {
            CardTitle(stringResource(R.string.s_weather))
            if (s.weatherOn && vm.weather != null) WeatherInfo(vm, s, Modifier.padding(top = 4.dp, bottom = 4.dp))
            else Text(stringResource(R.string.s_turn_on_weather_in_settings_extras), color = Muted, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 6.dp).clickable { vm.screen = Screen.SETTINGS })
        }
        BuiltIn.PRAYER -> {
            val now by rememberTicker(30_000)
            CardTitle(stringResource(R.string.s_prayer))
            if (s.prayerOn && s.city != null) PrayerTimeline(vm, s, now, Modifier.padding(top = 6.dp, bottom = 4.dp))
            else Text(stringResource(R.string.s_turn_on_islamic_prayer_in_settings), color = Muted, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 6.dp).clickable { vm.screen = Screen.SETTINGS })
        }
        BuiltIn.COUNTDOWN -> CountdownCard(vm, id)
        BuiltIn.HOME_ASSISTANT -> HaCard(vm)
        BuiltIn.UPTIME -> KumaCard(vm)
        BuiltIn.MESSAGES -> MessagesCard(vm)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun CardHint(title: String, text: String) {
    CardTitle(title)
    Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun NotesCard(vm: LauncherViewModel) {
    NotesBlock(vm)
}

/** This month: today in the accent, a week row per line, and today in the calendars you turned on. */
@Composable
private fun MonthCard(s: LauncherSettings) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val accent = Accent
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), color = Ink, fontSize = 16.sp,
            modifier = Modifier.weight(1f).clickable { month = YearMonth.from(today) })
        Text("‹", color = Muted, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }
            .padding(horizontal = 12.dp))
        Text("›", color = Muted, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { month = month.plusMonths(1) }
            .padding(horizontal = 12.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        (0L..6L).forEach { i ->
            Text(firstDow.plus(i).getDisplayName(java.time.format.TextStyle.NARROW, locale), color = Muted, fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDow.value, 7)
    val cells = lead + month.lengthOfMonth()
    (0 until (cells + 6) / 7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { col ->
                val day = week * 7 + col - lead + 1
                Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                    if (day in 1..month.lengthOfMonth()) {
                        val d = month.atDay(day)
                        val isToday = d == today
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (isToday) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Text("$day", fontSize = 14.sp, color = when {
                                isToday -> Color(0xFF0B0B0A)
                                d.isBefore(today) -> Muted
                                else -> Ink
                            })
                        }
                    }
                }
            }
        }
    }
    val line = remember(today, s.hijriOn, s.hijriAdjust, s.sakaOn, s.malayalamOn, s.tamilOn) {
        buildList {
            if (s.hijriOn) add(Calendars.hijri(today, s.hijriAdjust))
            if (s.sakaOn) add(Calendars.saka(today))
            if (s.malayalamOn) add(Calendars.malayalam(today).substringBefore(" · "))
            if (s.tamilOn) add(Calendars.tamil(today).substringBefore(" · "))
        }.joinToString("  ·  ")
    }
    if (line.isNotEmpty()) Text("Today: $line", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
}

private class DeviceNow(val battery: Int, val charging: Boolean, val tempC: Float, val storeFree: Long, val storeTotal: Long,
                        val ramFree: Long, val ramTotal: Long)

private fun deviceNow(context: Context): DeviceNow {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val bm = context.getSystemService(BatteryManager::class.java)
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
    return DeviceNow(
        battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
        charging = bm?.isCharging == true,
        tempC = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
        storeFree = stat?.availableBytes ?: 0, storeTotal = stat?.totalBytes ?: 0,
        ramFree = mem.availMem, ramTotal = mem.totalMem,
    )
}

private fun gb(bytes: Long): String = if (bytes >= 10_000_000_000L) "${bytes / 1_000_000_000} GB"
else String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9)

/** Battery, storage and memory, each as a line and a thin bar. Read every 30 seconds while the Shelf is open. */
@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    val tick by rememberTicker(30_000)
    val d = remember(tick) { deviceNow(context) }
    CardTitle(stringResource(R.string.s_battery_and_storage))
    Meter("Battery", "${d.battery}%" + (if (d.charging) " · charging" else "") + (if (d.tempC > 0) " · ${d.tempC.toInt()}°C" else ""),
        d.battery / 100f)
    if (d.storeTotal > 0) Meter("Storage", "${gb(d.storeFree)} free of ${gb(d.storeTotal)}", 1f - d.storeFree / d.storeTotal.toFloat())
    if (d.ramTotal > 0) Meter("Memory", "${gb(d.ramFree)} free of ${gb(d.ramTotal)}", 1f - d.ramFree / d.ramTotal.toFloat())
}

@Composable
private fun Meter(label: String, value: String, frac: Float) {
    val accent = Accent
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row {
            Text(label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
        }
    }
}

/** Days to a date you choose: a trip home, Eid, Diwali, a birthday. Tap to change it. */
@Composable
private fun CountdownCard(vm: LauncherViewModel, id: Int) {
    val c = vm.countdowns[id]
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp)) {
        if (c == null) {
            CardTitle(stringResource(R.string.s_countdown))
            Text(stringResource(R.string.s_tap_to_choose_a_date), color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            val days = c.second - LocalDate.now().toEpochDay()
            CardTitle(c.first)
            Text(when {
                days > 1 -> "$days days"
                days == 1L -> "Tomorrow"
                days == 0L -> "Today"
                else -> "${-days} days ago"
            }, color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text(LocalDate.ofEpochDay(c.second).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (open) CountdownDialog(c, onDismiss = { open = false }) { title, day ->
        vm.setCountdown(id, title, day)
        open = false
    }
}

@Composable
private fun CountdownDialog(initial: Pair<String, Long>?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.first ?: "") }
    var day by remember { mutableLongStateOf(initial?.second ?: LocalDate.now().plusDays(7).toEpochDay()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(title.trim().ifEmpty { "Countdown" }, day) }) { Text(stringResource(R.string.s_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_countdown)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(R.string.s_what_for)) })
                Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                    color = Accent, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.06f)).clickable {
                            val d = LocalDate.ofEpochDay(day)
                            DatePickerDialog(context, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() },
                                d.year, d.monthValue - 1, d.dayOfMonth).show()
                        }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        },
    )
}
