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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cloudit24.stillpoint.data.Camera
import com.cloudit24.stillpoint.service.CarService
import androidx.compose.runtime.collectAsState
import com.cloudit24.stillpoint.data.SpeedCameras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.cloudit24.stillpoint.notify.NotifyItem
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

    // The drive runs in CarService, so it carries on if this screen is left by accident. Start it once we may use the GPS.
    var hasGps by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val askGps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasGps = it }
    LaunchedEffect(Unit) { if (!hasGps) askGps.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    LaunchedEffect(hasGps) { if (hasGps) CarService.start(context) }
    val st by CarService.state.collectAsState()
    val speedMs = st.speedMs; val accuracy = st.accuracy; val sats = st.sats; val here = st.here; val osmLimit = st.osmLimit; val nearest = st.nearest

    val limitKmh = osmLimit ?: s.carLimit
    val limit = if (limitKmh <= 0) 0 else if (s.carMph) (limitKmh / 1.609f).toInt() else limitKmh
    val kmh = if (speedMs < 0) -1 else (speedMs * 3.6f).toInt()
    val shown = if (kmh < 0) -1 else if (s.carMph) (kmh / 1.609f).toInt() else kmh
    val near = limit > 0 && shown >= limit - 3 && shown <= limit
    val over = limit > 0 && shown > limit

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

    val log = if (s.carAlert <= 0) emptyList() else vm.carLog.filter { s.carAlert >= 2 || it.important || it.special }
    fun say(n: NotifyItem): String {
        val app = vm.apps.firstOrNull { it.packageName == n.pkg }
        return listOf(app?.label, n.title, n.text).filter { !it.isNullOrBlank() }.joinToString(". ")
    }
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

        // Messages on this drive: all of them, important people and apps only, or none (with passengers). Then the voice and the tones.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarSwitch("All", s.carAlert >= 2, Modifier.weight(1f)) { vm.updateSettings { it.copy(carAlert = 2) } }
            CarSwitch("Important", s.carAlert == 1, Modifier.weight(1f)) { vm.updateSettings { it.copy(carAlert = 1) } }
            CarSwitch("Mute", s.carAlert <= 0, Modifier.weight(1f)) { vm.updateSettings { it.copy(carAlert = 0) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarSwitch("Read aloud", s.carReadAloud, Modifier.weight(1f)) { vm.updateSettings { it.copy(carReadAloud = !it.carReadAloud) } }
            CarSwitch("Say limit", s.carSpeak, Modifier.weight(1f)) { vm.updateSettings { it.copy(carSpeak = !it.carSpeak) } }
            CarSwitch("Tones", s.carTone > 0, Modifier.weight(1f)) { vm.updateSettings { it.copy(carTone = if (it.carTone > 0) 0 else 100) } }
        }

        Column(Modifier.fillMaxWidth().weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { zen = !zen },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (s.carGlass) SpeedDial(shown, limit, if (s.carMph) "MPH" else "KM/H", near, over)
            else {
                Text(if (shown < 0) "–" else "$shown", color = if (limit > 0) speedTone(shown, limit, near, over, Accent) else Color.White,
                    fontSize = 132.sp, fontWeight = if (over) FontWeight.Normal else FontWeight.ExtraLight, lineHeight = 132.sp)
                Text(if (s.carMph) "MPH" else "KM/H", color = Muted, fontSize = 15.sp, letterSpacing = 3.sp)
                if (limit > 0) LimitSign(limit, over, Modifier.padding(top = 14.dp))
            }
            if (limit <= 0) Text(
                when {
                    !s.carOsmLimit && s.carLimit <= 0 -> "No limit set · Settings, Car mode"
                    here == null -> "Limit from the map once there is a position"
                    else -> "No limit known for this road"
                }, color = Muted.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            if (s.carCameras && nearest == null) Text(
                if (here == null) "Cameras from the map once there is a position" else "No camera within 1 km",
                color = Muted.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            nearest?.let { (cam, d) ->
                val close = d < 300f
                Text("CAMERA · ${(d / 10).toInt() * 10} m" + (if (cam.limitKmh > 0) " · ${if (s.carMph) (cam.limitKmh / 1.609f).toInt() else cam.limitKmh}" else ""),
                    color = if (close) Color.White else Color(0xFFE5A53A), fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp).glassPill(GlassLevel.PANEL, if (close) Color(0xFFE53935) else Color(0xFFE5A53A))
                        .padding(horizontal = 14.dp, vertical = 6.dp))
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
            if (log.isNotEmpty()) {
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.fillMaxWidth().glass(shape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${log.size} on this drive", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        if (s.carVoice) Text("Read all", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable {
                            voice.speak(log.take(10).joinToString(". Next. ") { say(it) })
                        }.padding(horizontal = 8.dp, vertical = 4.dp))
                        Text("Clear", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { vm.clearCarLog() }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                    }
                    Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                        log.forEach { n ->
                            val app = vm.apps.firstOrNull { it.packageName == n.pkg }
                            Row(Modifier.fillMaxWidth().clickable { app?.let { vm.launchNow(it) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (n.special) SpecialPink else vm.notifyColor(n.pkg)))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(n.title.ifEmpty { app?.label ?: n.pkg }, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(n.text.ifEmpty { app?.label.orEmpty() }, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatClock(context, n.postTime), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                                if (s.carVoice) Text("Read", color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { voice.speak(say(n)) }.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
            val apps = s.carApps.mapNotNull { k -> vm.apps.firstOrNull { it.key == k } }
            if (apps.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.take(4).forEach { app ->
                    val shape = RoundedCornerShape(16.dp)
                    Box(Modifier.weight(1f).height(64.dp).glass(shape)
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
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Keeps running when you leave · swipe down for home", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("End drive", color = Accent, fontSize = 13.sp, modifier = Modifier.glassPill().clickable { CarService.stop(context); vm.screen = Screen.HOME }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** The posted limit as a road sign: a red ring, the number inside; brighter red when you are over it. */
@Composable
private fun LimitSign(limit: Int, over: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = if (over) 0.95f else 0.9f))
        .border(4.dp, if (over) Color(0xFFFF3B30) else Color(0xFFC62828), CircleShape), contentAlignment = Alignment.Center) {
        Text("$limit", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The glass dial: a ring that fills with your speed toward the limit (or 120 when there is none), in the accent,
 * amber as you near the limit and red over it; the speed in the middle, the limit sign in the ring's gap.
 * Nothing animates: it redraws once a second with the GPS.
 */
@Composable
private fun SpeedDial(shown: Int, limit: Int, unit: String, near: Boolean, over: Boolean) {
    val accent = Accent
    val tone = speedTone(shown, limit, near, over, accent)
    val top = if (limit > 0) maxOf(limit + 20, (limit * 1.3f).toInt()) else 120
    val frac = if (shown < 0) 0f else (shown.toFloat() / top).coerceIn(0f, 1f)
    Box(Modifier.size(250.dp).glass(CircleShape, GlassLevel.SOFT, tone), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(tone, 135f, 270f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (limit > 0) {
                // A tick where the limit sits on the ring.
                val a = Math.toRadians((135f + 270f * (limit.toFloat() / top).coerceIn(0f, 1f)).toDouble())
                val r = arcSize.width / 2
                val c = Offset(size.width / 2, size.height / 2)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x + (r - stroke) * Math.cos(a).toFloat(), c.y + (r - stroke) * Math.sin(a).toFloat()),
                    Offset(c.x + (r + stroke) * Math.cos(a).toFloat(), c.y + (r + stroke) * Math.sin(a).toFloat()), 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (shown < 0) "–" else "$shown", color = if (limit > 0) tone else Color.White, fontSize = 92.sp,
                fontWeight = if (over) FontWeight.Normal else FontWeight.ExtraLight, lineHeight = 92.sp)
            Text(unit, color = Muted, fontSize = 13.sp, letterSpacing = 3.sp)
        }
        if (limit > 0) LimitSign(limit, over, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

/**
 * One warning tone, on the media volume (the one the car's speakers carry), so it is heard in the car.
 * Soft: nearing the limit. Otherwise: over it, or a camera.
 */
fun carBeep(soft: Boolean = false, loudness: Int = 100) {
    if (loudness <= 0) return
    runCatching {
        val t = ToneGenerator(AudioManager.STREAM_MUSIC, (if (soft) loudness * 7 / 10 else loudness).coerceIn(1, 100))
        t.startTone(if (soft) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, if (soft) 220 else 500)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ t.release() }, 1200)
    }
}

/** The colour of the speed against the limit: the accent, blending to amber from 85% of the limit, red over it. */
private fun speedTone(shown: Int, limit: Int, near: Boolean, over: Boolean, accent: Color): Color {
    if (limit <= 0 || shown < 0) return accent
    val amber = Color(0xFFE5A53A)
    return when {
        over -> Color(0xFFFF3B30)
        near -> amber
        else -> {
            val ratio = shown.toFloat() / limit
            if (ratio < 0.85f) accent else androidx.compose.ui.graphics.lerp(accent, amber, ((ratio - 0.85f) / 0.15f).coerceIn(0f, 1f))
        }
    }
}

/** A switch as a glass pill: lit in the accent when on, dim when off. Big enough for a finger in a moving car. */
@Composable
private fun CarSwitch(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = if (on) Accent else glassTint
    Text(label, color = if (on) Color.White else Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.glassPill(if (on) GlassLevel.RAISED else GlassLevel.SOFT, tint).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp))
}
