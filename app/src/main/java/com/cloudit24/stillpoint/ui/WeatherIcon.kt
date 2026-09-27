package com.cloudit24.stillpoint.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class WeatherKind(val label: String) {
    CLEAR("Clear"), PARTLY("Partly cloudy"), CLOUDY("Cloudy"), FOG("Fog"),
    DRIZZLE("Drizzle"), RAIN("Rain"), SNOW("Snow"), THUNDER("Thunderstorm"),
}

/** WMO weather code (as sent by Open-Meteo) to a drawable kind. */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.THUNDER
    else -> WeatherKind.CLOUDY
}

private val SunColor = Color(0xFFFFC857)
private val MoonColor = Color(0xFFE8E6E1)
private val CloudColor = Color(0xFFB8BCC2)
private val RainColor = Color(0xFF7FB2E5)
private val BoltColor = Color(0xFFFFE066)

/** Small looping weather picture drawn in code (no image files). Static when [animate] is off. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, animate: Boolean, modifier: Modifier) {
    val kind = weatherKind(code)
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "weather")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (kind == WeatherKind.CLEAR) 12_000 else 2_400, easing = LinearEasing)),
            label = "phase",
        )
        phase
    } else 0.3f

    Canvas(modifier) {
        val s = size.minDimension
        val wave = sin(t * 2 * PI).toFloat()
        when (kind) {
            WeatherKind.CLEAR -> if (isDay) sun(center, s * 0.2f, t * 45f) else moon(s, t)
            WeatherKind.PARTLY -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f, t * 45f) else moon(s * 0.75f, t)
                cloud(s * 0.58f + wave * s * 0.03f, s * 0.6f, s * 0.62f)
            }
            WeatherKind.CLOUDY -> {
                cloud(s * 0.4f - wave * s * 0.03f, s * 0.42f, s * 0.5f, CloudColor.copy(alpha = 0.55f))
                cloud(s * 0.55f + wave * s * 0.04f, s * 0.6f, s * 0.66f)
            }
            WeatherKind.FOG -> for (i in 0..2) {
                val y = s * (0.35f + i * 0.17f)
                val dx = sin((t + i / 3f) * 2 * PI).toFloat() * s * 0.07f
                drawLine(CloudColor.copy(alpha = 0.5f + 0.2f * i), Offset(s * 0.15f + dx, y), Offset(s * 0.85f + dx, y),
                    strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
            WeatherKind.DRIZZLE, WeatherKind.RAIN -> {
                cloud(s * 0.5f, s * 0.38f, s * 0.66f)
                val drops = if (kind == WeatherKind.RAIN) 4 else 3
                val len = if (kind == WeatherKind.RAIN) 0.12f else 0.06f
                for (i in 0 until drops) {
                    val p = (t + i / drops.toFloat()) % 1f
                    val x = s * (0.3f + i * (0.4f / (drops - 1))) - p * s * 0.05f
                    val y = s * 0.58f + p * s * 0.3f
                    drawLine(RainColor.copy(alpha = 1f - p), Offset(x, y), Offset(x - s * 0.02f, y + s * len),
                        strokeWidth = s * 0.045f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(s * 0.5f, s * 0.38f, s * 0.66f)
                for (i in 0..3) {
                    val p = (t + i / 4f) % 1f
                    val x = s * (0.3f + i * 0.13f) + sin((p + i) * 2 * PI).toFloat() * s * 0.03f
                    drawCircle(Color.White.copy(alpha = 1f - p * 0.8f), s * 0.035f, Offset(x, s * 0.6f + p * s * 0.3f))
                }
            }
            WeatherKind.THUNDER -> {
                cloud(s * 0.5f, s * 0.36f, s * 0.7f)
                // Two quick flashes per loop.
                val flash = t < 0.08f || t in 0.16f..0.22f
                val bolt = Path().apply {
                    moveTo(s * 0.52f, s * 0.52f); lineTo(s * 0.4f, s * 0.72f); lineTo(s * 0.5f, s * 0.72f)
                    lineTo(s * 0.42f, s * 0.92f); lineTo(s * 0.62f, s * 0.66f); lineTo(s * 0.52f, s * 0.66f); close()
                }
                drawPath(bolt, BoltColor.copy(alpha = if (flash) 1f else 0.3f))
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, angle: Float) {
    drawCircle(SunColor, r, c)
    rotate(angle, c) {
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawLine(SunColor, Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.95f),
                    strokeWidth = r * 0.28f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.moon(s: Float, t: Float) {
    val c = Offset(s * 0.5f, s * 0.5f)
    val r = s * 0.24f
    drawCircle(MoonColor, r, c)
    drawCircle(Color.Black, r * 0.9f, Offset(c.x + r * 0.55f, c.y - r * 0.35f))
    val twinkle = 0.25f + 0.75f * abs(sin(t * 2 * PI).toFloat())
    drawCircle(MoonColor.copy(alpha = twinkle), s * 0.025f, Offset(s * 0.8f, s * 0.22f))
}

private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, color: Color = CloudColor) {
    drawCircle(color, w * 0.2f, Offset(cx - w * 0.2f, cy + w * 0.02f))
    drawCircle(color, w * 0.28f, Offset(cx + w * 0.02f, cy - w * 0.08f))
    drawCircle(color, w * 0.19f, Offset(cx + w * 0.26f, cy + w * 0.04f))
    drawRoundRect(color, topLeft = Offset(cx - w * 0.4f, cy), size = Size(w * 0.84f, w * 0.23f),
        cornerRadius = CornerRadius(w * 0.115f))
}
