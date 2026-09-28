package com.cloudit24.stillpoint.ui

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.PrayerTimes
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val PrayerText = Color(0xFF8FC4A8)

/** "Asr 3:34 PM · in 1h 12m". Tap for all times and the Qibla compass. */
@Composable
fun PrayerLine(vm: LauncherViewModel, s: LauncherSettings, now: Long) {
    val context = LocalContext.current
    val city = s.city
    val next = remember(city, s.prayerMethod, s.asrHanafi, now) {
        city?.let { PrayerTimes.next(now, it.lat, it.lon, s.prayerMethod, s.asrHanafi) }
    }
    val text = when {
        city == null -> "Prayer times: set a city in Settings"
        next == null -> "Prayer times unavailable at this latitude today"
        else -> "${next.first.label} ${formatClock(context, next.second)}  ·  in ${formatDuration(next.second - now)}"
    }
    Text(
        text, color = PrayerText, fontSize = 14.sp,
        modifier = Modifier.padding(top = 6.dp)
            .clickable { vm.screen = if (city == null) Screen.SETTINGS else Screen.PRAYER },
    )
}

/** Today's five prayers and a live Qibla compass. The compass sensor runs only while this screen is open. */
@Composable
fun PrayerScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    val now by rememberTicker(60_000L)
    val city = s.city

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text("Prayer", fontSize = 34.sp, fontWeight = FontWeight.Light)
        if (city == null) {
            Text("Choose a city in Settings to calculate prayer times.", color = Accent, fontSize = 15.sp,
                modifier = Modifier.padding(top = 16.dp).clickable { vm.screen = Screen.SETTINGS })
            return@Column
        }
        val today = LocalDate.now()
        Text("${city.name}  ·  ${Calendars.hijri(today, s.hijriAdjust)}", color = Muted, fontSize = 14.sp)

        val times = remember(today, city, s.prayerMethod, s.asrHanafi) {
            PrayerTimes.forDate(today, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
        }
        val next = remember(city, s.prayerMethod, s.asrHanafi, now) {
            PrayerTimes.next(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi)?.first
        }
        Column(Modifier.padding(top = 16.dp)) {
            Prayer.entries.forEach { p ->
                val at = times[p]
                val isNext = p == next
                val color = when {
                    isNext -> PrayerText
                    at != null && at < now || !p.isPrayer -> Muted
                    else -> Ink
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, color = color, fontSize = 20.sp,
                        fontWeight = if (isNext) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text(p.arabic, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(end = 18.dp))
                    Text(at?.let { formatClock(context, it) } ?: "—", color = color, fontSize = 20.sp)
                }
            }
        }
        Text("${s.prayerMethod.label}, Asr ${if (s.asrHanafi) "Hanafi" else "standard"}. " +
            "Calculated on the phone; your mosque may differ by a few minutes.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

        QiblaCompass(city.lat, city.lon)
    }
}

@Composable
private fun ColumnScope.QiblaCompass(lat: Double, lon: Double) {
    val qibla = remember(lat, lon) { PrayerTimes.qibla(lat, lon) }
    val heading by rememberTrueHeading(lat, lon)
    val h = heading

    Text("Qibla", fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 28.dp))
    val delta = h?.let { ((qibla - it + 540) % 360) - 180 }
    val facing = delta != null && abs(delta) < 5
    Text(
        when {
            h == null -> "${qibla.roundToInt()}° clockwise from north. No compass sensor found on this phone."
            facing -> "You are facing the Qibla"
            else -> "${qibla.roundToInt()}° from north  ·  turn ${if (delta!! > 0) "right" else "left"} ${abs(delta).roundToInt()}°"
        },
        color = if (facing) PrayerText else Muted, fontSize = 14.sp,
    )

    Canvas(Modifier.padding(vertical = 20.dp).size(260.dp).align(Alignment.CenterHorizontally)) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Muted, r - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
        // The dial turns with the phone so its marks point to real directions.
        rotate(-(h ?: 0.0).toFloat()) {
            for (i in 0 until 72) {
                val len = if (i % 18 == 0) 14.dp.toPx() else if (i % 6 == 0) 9.dp.toPx() else 4.dp.toPx()
                rotate(i * 5f) {
                    drawLine(Muted, Offset(c.x, c.y - r + 2.dp.toPx()), Offset(c.x, c.y - r + len), strokeWidth = 1.dp.toPx())
                }
            }
            // North: small red triangle.
            val north = Path().apply {
                moveTo(c.x, c.y - r + 18.dp.toPx())
                lineTo(c.x - 7.dp.toPx(), c.y - r + 32.dp.toPx())
                lineTo(c.x + 7.dp.toPx(), c.y - r + 32.dp.toPx())
                close()
            }
            drawPath(north, Color(0xFFD9534F))
            // Qibla needle with the Kaaba as a square at its tip.
            rotate(qibla.toFloat()) {
                val needle = if (facing) PrayerText else Ink
                drawLine(needle, c, Offset(c.x, c.y - r * 0.72f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val k = 10.dp.toPx()
                drawRect(needle, topLeft = Offset(c.x - k / 2, c.y - r * 0.72f - k * 1.4f), size = androidx.compose.ui.geometry.Size(k, k))
            }
        }
        // Fixed mark: the direction the top of the phone points.
        drawLine(Slate, Offset(c.x, c.y - r - 2.dp.toPx()), Offset(c.x, c.y - r + 20.dp.toPx()), strokeWidth = 3.dp.toPx())
        drawCircle(Ink, 5.dp.toPx(), c)
    }
    Text("Hold the phone flat, away from metal and magnets. If it seems off, move the phone in a figure 8 to calibrate.",
        color = Muted, fontSize = 12.sp)
}

/** Degrees from true north the top of the phone points to; null without a compass. Sensor on only while resumed. */
@Composable
private fun rememberTrueHeading(lat: Double, lon: Double): State<Double?> {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Double?>(null) }
    LifecycleResumeEffect(lat, lon) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = (Math.toDegrees(ori[0].toDouble()) + declination + 360) % 360
                val prev = heading.value
                // Smooth the jitter, going the short way round past north.
                heading.value = if (prev == null) deg else (prev + (((deg - prev + 540) % 360) - 180) * 0.15 + 360) % 360
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
    return heading
}
