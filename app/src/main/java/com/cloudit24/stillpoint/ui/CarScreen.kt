package com.cloudit24.stillpoint.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.batteryNow
import com.cloudit24.stillpoint.llm.Voice
import com.cloudit24.stillpoint.notify.NotifyHub

/**
 * Car mode: the speed from GPS, big; one notification; your few apps; the assistant by voice.
 * Pure black, nothing animated. Swipe down to leave.
 */
@Composable
fun CarScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val now by rememberTicker(15_000)
    val view = LocalView.current
    DisposableEffect(s.carKeepOn) { view.keepScreenOn = s.carKeepOn; onDispose { view.keepScreenOn = false } }

    // Speed from GPS, one reading a second.
    var speedMs by remember { mutableFloatStateOf(-1f) }
    var accuracy by remember { mutableFloatStateOf(-1f) }     // metres; how good the fix is
    var sats by remember { mutableIntStateOf(-1) }            // satellites used in the fix
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var osmLimit by remember { mutableStateOf<Int?>(null) }
    var hasGps by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val askGps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasGps = it }
    LaunchedEffect(Unit) { if (!hasGps) askGps.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    DisposableEffect(hasGps) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                speedMs = if (l.hasSpeed()) l.speed else -1f
                accuracy = if (l.hasAccuracy()) l.accuracy else -1f
                here = l.latitude to l.longitude
            }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { speedMs = -1f }
        }
        val gnss = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                sats = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
            override fun onStopped() { sats = -1 }
        }
        if (hasGps && lm != null) runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            lm.registerGnssStatusCallback(gnss, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        onDispose { runCatching { lm?.removeUpdates(listener); lm?.unregisterGnssStatusCallback(gnss) } }
    }
    // The road's posted limit from OpenStreetMap, only if you turned that on: asked again after ~150 m.
    var asked by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(here, s.carOsmLimit) {
        val h = here ?: return@LaunchedEffect
        if (!s.carOsmLimit) { osmLimit = null; return@LaunchedEffect }
        val a = asked
        if (a != null && Math.abs(a.first - h.first) < 0.0013 && Math.abs(a.second - h.second) < 0.0015) return@LaunchedEffect
        asked = h
        osmLimit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.cloudit24.stillpoint.data.SpeedLimits.at(h.first, h.second) } ?: osmLimit
    }
    val limitKmh = osmLimit ?: s.carLimit
    val limit = if (limitKmh <= 0) 0 else if (s.carMph) (limitKmh / 1.609f).toInt() else limitKmh
    val kmh = if (speedMs < 0) -1 else (speedMs * 3.6f).toInt()
    val shown = if (kmh < 0) -1 else if (s.carMph) (kmh / 1.609f).toInt() else kmh

    // Over the limit: a short tone, at most every ten seconds.
    var lastTone by remember { mutableStateOf(0L) }
    LaunchedEffect(shown) {
        if (limit > 0 && shown > limit && System.currentTimeMillis() - lastTone > 10_000) {
            lastTone = System.currentTimeMillis()
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 300) }
        }
    }

    // Voice: tap the mic, speak, the assistant answers aloud.
    val voice = remember { Voice(context) }
    DisposableEffect(Unit) { onDispose { voice.release() } }
    val a = vm.assistant
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it; if (it) voice.listen { q -> a.ask(q, s, statusFacts(vm, s, System.currentTimeMillis(), context), vm.assistantApps()) } }
    fun talk() {
        if (!hasMic) { askMic.launch(Manifest.permission.RECORD_AUDIO); return }
        voice.listen { q -> a.ask(q, s, statusFacts(vm, s, System.currentTimeMillis(), context), vm.assistantApps()) }
    }
    // In the car, "open maps" opens it; no chip to tap.
    LaunchedEffect(a.suggestions) {
        a.suggestions.firstOrNull { it.open != null }?.let { c -> vm.apps.firstOrNull { it.label == c.open }?.let { vm.launchNow(it) }; a.suggestions = a.suggestions - c }
    }
    var spoken by remember { mutableIntStateOf(a.turns.size) }
    LaunchedEffect(a.turns.size, a.busy) {
        if (!a.busy && a.turns.size > spoken) {
            val last = a.turns.last()
            // Hands-free: after the answer is spoken, listen again for a follow-up.
            if (last.role == "assistant" && s.carVoice) voice.speak(last.text) { if (vm.screen == Screen.CAR) talk() }
            spoken = a.turns.size
        }
    }

    val latest = vm.litNotifications().filter { !s.carImportantOnly || it.important || it.special }.maxByOrNull { it.postTime }
    val battery = remember(now) { batteryNow(context) }
    val next = remember(now) { s.city?.takeIf { s.prayerOn }?.let { c -> PrayerTimes.next(now, c.lat, c.lon, s.prayerMethod, s.asrHanafi) } }
    var zen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 24f) vm.screen = Screen.HOME } }
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(context, now), color = Muted, fontSize = 15.sp)
            next?.let { (p, at) -> Text("${p.label} in ${formatDuration(at - now)}", color = Muted, fontSize = 15.sp) }
            Text(if (battery.percent >= 0) "${battery.percent}%" + (if (battery.charging) " ⚡" else "") else "", color = Muted, fontSize = 15.sp)
        }

        Column(Modifier.fillMaxWidth().weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { zen = !zen },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (shown < 0) "–" else "$shown", color = Color.White, fontSize = 132.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 132.sp)
            Text(if (s.carMph) "MPH" else "KM/H", color = Muted, fontSize = 15.sp, letterSpacing = 3.sp)
            if (limit > 0) Box(Modifier.padding(top = 14.dp).size(40.dp).clip(CircleShape)
                .border(2.dp, if (shown > limit) Color(0xFFE53935) else Color(0xFF8B2E2E), CircleShape), contentAlignment = Alignment.Center) {
                Text("$limit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            if (limit > 0 && osmLimit != null) Text("limit from OpenStreetMap", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            // GPS strength: the fix's accuracy in metres and satellites in use.
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val good = when { !hasGps || accuracy < 0 -> Color(0xFF8B2E2E); accuracy <= 10f -> Color(0xFF3FA66B); accuracy <= 25f -> Color(0xFFC9A227); else -> Color(0xFFE53935) }
                Box(Modifier.size(8.dp).clip(CircleShape).background(good))
                Text(
                    when {
                        !hasGps -> "  Location permission is needed for the speed"
                        accuracy < 0 -> "  GPS: looking for satellites" + (if (sats > 0) " · $sats in view" else "")
                        else -> "  GPS ±${accuracy.toInt()} m" + (if (sats >= 0) " · $sats satellites" else "")
                    }, color = Muted, fontSize = 13.sp,
                )
            }
        }

        if (!zen) {
            latest?.let { n ->
                val app = vm.apps.firstOrNull { it.packageName == n.pkg }
                val shape = RoundedCornerShape(14.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0D0D0C)).border(1.dp, Color(0xFF1B1B1A), shape)
                    .clickable { app?.let { vm.launchNow(it) } }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (n.special) SpecialPink else vm.notifyColor(n.pkg)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(n.title.ifEmpty { app?.label ?: n.pkg }, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.text.ifEmpty { app?.label.orEmpty() }, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.carVoice) Text("Read", color = Accent, fontSize = 14.sp, modifier = Modifier.clickable {
                        voice.speak(listOf(app?.label, n.title, n.text).filter { !it.isNullOrBlank() }.joinToString(". "))
                    }.padding(start = 10.dp))
                }
            }
            val apps = s.carApps.mapNotNull { k -> vm.apps.firstOrNull { it.key == k } }
            if (apps.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.take(4).forEach { app ->
                    val shape = RoundedCornerShape(16.dp)
                    Box(Modifier.weight(1f).height(64.dp).clip(shape).background(Color(0xFF0D0D0C)).border(1.dp, Color(0xFF1B1B1A), shape)
                        .clickable { vm.launchNow(app) }, contentAlignment = Alignment.Center) {
                        Text(app.label, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
            if (s.carVoice && s.assistantOn) Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, if (voice.listening) Color.White else Accent, CircleShape)
                    .background(if (voice.listening) Accent.copy(alpha = 0.25f) else Color.Transparent).clickable { talk() }, contentAlignment = Alignment.Center) {
                    Text("mic", color = if (voice.listening) Color.White else Accent, fontSize = 12.sp)
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    when {
                        voice.listening -> voice.heard.ifEmpty { "Listening…" }
                        a.busy -> a.draft.ifEmpty { "Thinking…" }
                        voice.error.isNotEmpty() -> voice.error
                        a.turns.lastOrNull()?.role == "assistant" -> a.turns.last().text
                        else -> "Tap the mic and ask"
                    }, color = Muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("GPS · once a second · swipe down to leave", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
