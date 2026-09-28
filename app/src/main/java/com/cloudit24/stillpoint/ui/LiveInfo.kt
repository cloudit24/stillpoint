package com.cloudit24.stillpoint.ui

import androidx.compose.ui.text.style.TextAlign
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.net.TrafficStats
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.GoldSource
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.LocalIp
import com.cloudit24.stillpoint.data.NetInfo
import com.cloudit24.stillpoint.data.priceFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val GoldText = Color(0xFFE0C068)

/** Animated icon, temperature and city, shown to the right of the clock. */
@Composable
fun WeatherBadge(vm: LauncherViewModel, s: LauncherSettings, onSetup: () -> Unit) {
    val city = s.city
    val w = vm.weather
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.clickable { if (city == null) onSetup() else vm.refreshLive(force = true) },
    ) {
        if (city == null) {
            Text("Set city", color = Accent, fontSize = 13.sp)
            return@Column
        }
        if (w == null) {
            Text("…", color = Muted, fontSize = 22.sp)
        } else {
            WeatherIcon(w.code, w.isDay, s.animateWeather, Modifier.size(46.dp))
            val temp = if (s.fahrenheit) w.tempC * 9 / 5 + 32 else w.tempC
            Text("${temp.roundToInt()}°", fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
        Text(city.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Gold 24K  516.50 AED/g · Dubai". Tap to refresh. */
@Composable
fun GoldLine(vm: LauncherViewModel, s: LauncherSettings) {
    val g = vm.gold?.takeIf { it.currency == s.goldCurrency && it.source == s.goldSource }
    val price = g?.priceFor(s.goldKarat, s.goldPerGram)
    val text = if (g == null || price == null) "Gold price loading…" else {
        val tag = when {
            price.dubai -> "Dubai"
            s.goldSource == GoldSource.DUBAI -> "spot (Dubai rate unavailable)"
            else -> "spot"
        }
        "Gold ${s.goldKarat}K  " + String.format(Locale.US, "%,.2f", price.value) +
            " ${g.currency}/${if (s.goldPerGram) "g" else "oz"}  · $tag"
    }
    Text(
        text, color = GoldText, fontSize = 14.sp,
        modifier = Modifier.padding(top = 6.dp).clickable { vm.refreshLive(force = true) },
    )
}

/** Download/upload speed and memory use, sampled every second while the home screen is on top. */
@Composable
fun SystemStatsLine(onClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var text by remember { mutableStateOf("↓ …") }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo()
            var lastRx = TrafficStats.getTotalRxBytes()
            var lastTx = TrafficStats.getTotalTxBytes()
            var lastT = SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000)
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val t = SystemClock.elapsedRealtime()
                val secs = (t - lastT).coerceAtLeast(1) / 1000.0
                val net = if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
                    "Network n/a"
                } else {
                    "↓ ${rate((rx - lastRx) / secs)}  ↑ ${rate((tx - lastTx) / secs)}"
                }
                am.getMemoryInfo(mem)
                val usedPct = ((1 - mem.availMem.toDouble() / mem.totalMem) * 100).roundToInt()
                text = "$net\nRAM $usedPct% of ${(mem.totalMem / 1_073_741_824.0).roundToInt()} GB"
                lastRx = rx; lastTx = tx; lastT = t
            }
        }
    }
    // Monospace so the line doesn't jitter as numbers change every second.
    Text(text, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
        modifier = Modifier.padding(top = 6.dp).clickable(onClick = onClick))
}

/** "Wi-Fi 192.168.1.23 · Public 94.200.1.2". Local address re-read every 3 s while home is on top; tap to copy. */
@Composable
fun IpLine(vm: LauncherViewModel, s: LauncherSettings) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var local by remember { mutableStateOf<LocalIp?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, s.publicIpOn) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val l = withContext(Dispatchers.IO) { NetInfo.localIp(context) }
                local = l
                loaded = true
                vm.refreshPublicIp(l?.address)
                delay(3_000)
            }
        }
    }

    val l = local
    val parts = listOfNotNull(
        if (s.showLocalIp) (if (!loaded) "IP …" else l?.let { "${it.kind} ${it.address}" } ?: "Offline") else null,
        if (s.publicIpOn && (l != null || !s.showLocalIp)) "Public ${vm.publicIp ?: "…"}" else null,
    )
    Text(
        parts.joinToString("\n"), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
        modifier = Modifier.padding(top = 4.dp).clickable {
            val copy = listOfNotNull(l?.address, vm.publicIp).joinToString("\n")
            if (copy.isNotEmpty()) {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("IP address", copy))
                Toast.makeText(context, "IP copied", Toast.LENGTH_SHORT).show()
            }
            vm.refreshPublicIp(l?.address, force = true)
        },
    )
}

private fun rate(bytesPerSec: Double): String {
    val b = bytesPerSec.coerceAtLeast(0.0)
    return when {
        b >= 1_048_576 -> String.format(Locale.US, "%.1f MB/s", b / 1_048_576)
        else -> String.format(Locale.US, "%.0f KB/s", b / 1024)
    }
}
