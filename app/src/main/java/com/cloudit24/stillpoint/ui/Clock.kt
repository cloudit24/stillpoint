package com.cloudit24.stillpoint.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.data.ClockStyle
import java.util.Calendar

private val Amber = Color(0xFFFFB000)
private val FlipCard = Color(0xFF1C1C1A)

/** Home clock in the chosen style. [now] ticks once a minute. Respects the 12/24-hour system setting. */
@Composable
fun Clock(style: ClockStyle, now: Long) {
    val context = LocalContext.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val is24 = DateFormat.is24HourFormat(context)
    val hour = cal.get(Calendar.HOUR_OF_DAY).let { h -> if (is24) h else (h % 12).let { if (it == 0) 12 else it } }
    val hh = if (is24) "%02d".format(hour) else hour.toString()
    val mm = "%02d".format(cal.get(Calendar.MINUTE))
    val amPm = if (is24) null else if (cal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"

    when (style) {
        ClockStyle.MINIMAL ->
            Text(formatClock(context, now), fontSize = 46.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp)
        ClockStyle.BOLD ->
            Text(formatClock(context, now), fontSize = 43.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp)
        ClockStyle.SERIF ->
            Text(formatClock(context, now), fontSize = 43.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light)
        ClockStyle.FLIP -> Row(verticalAlignment = Alignment.CenterVertically) {
            FlipCard(hh)
            Text(":", fontSize = 26.sp, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
            FlipCard(mm)
            amPm?.let { Text(it, fontSize = 14.sp, color = Muted, modifier = Modifier.padding(start = 8.dp)) }
        }
        ClockStyle.LCD -> Row(verticalAlignment = Alignment.Bottom) {
            val density = LocalDensity.current
            val digitCell = with(density) { (38.sp * 0.64f).toDp() }
            val colonCell = with(density) { (38.sp * 0.36f).toDp() }
            (hh.padStart(2, ' ') + ":" + mm).forEach { ch ->
                Box(Modifier.width(if (ch == ':') colonCell else digitCell), contentAlignment = Alignment.Center) {
                    // Unlit segments behind the digit, like an old LCD.
                    Text(if (ch == ':') ":" else "8", fontSize = 38.sp, fontFamily = FontFamily.Monospace,
                        color = Amber.copy(alpha = 0.10f), softWrap = false, maxLines = 1)
                    Text(ch.toString(), fontSize = 38.sp, fontFamily = FontFamily.Monospace, color = Amber,
                        softWrap = false, maxLines = 1)
                }
            }
            amPm?.let { Text(it, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Amber, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)) }
        }
        ClockStyle.ANALOG -> AnalogClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

@Composable
private fun FlipCard(digits: String) {
    Box(
        Modifier.background(FlipCard, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(digits, fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Ink)
        // The hinge line across the middle of a flip card.
        Canvas(Modifier.matchParentSize()) {
            drawLine(Color.Black, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun AnalogClock(hour24: Int, minute: Int) {
    Canvas(Modifier.size(72.dp)) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Muted, r - 1.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            rotate(i * 30f, c) {
                val long = i % 3 == 0
                drawLine(
                    if (long) Ink else Muted,
                    Offset(c.x, c.y - r + 6.dp.toPx()),
                    Offset(c.x, c.y - r + (if (long) 14 else 10).dp.toPx()),
                    strokeWidth = (if (long) 2.5f else 1.5f).dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        val hourAngle = ((hour24 % 12) + minute / 60f) * 30f
        rotate(hourAngle, c) {
            drawLine(Ink, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        rotate(minute * 6f, c) {
            drawLine(Ink, c, Offset(c.x, c.y - r * 0.78f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Slate, 3.5.dp.toPx(), c)
    }
}
