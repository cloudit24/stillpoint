package com.cloudit24.stillpoint.data

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Moon phase from the average lunar month. Good to within about half a day; calculated on the phone. */
object Moon {
    private const val SYNODIC = 29.530588853
    private const val NEW_MOON_2000 = 947_182_440_000L // 6 January 2000, 18:14 UTC

    /** Days since the last new moon, 0 up to 29.5. */
    fun age(ms: Long): Double {
        val days = (ms - NEW_MOON_2000) / 86_400_000.0
        return ((days % SYNODIC) + SYNODIC) % SYNODIC
    }

    /** Lit fraction of the disc, 0 (new) to 1 (full). */
    fun illumination(age: Double): Double = (1 - cos(2 * PI * age / SYNODIC)) / 2

    fun name(age: Double): String = when {
        age < 1.0 -> "New moon"
        age < 6.4 -> "Waxing crescent"
        age < 8.4 -> "First quarter"
        age < 13.8 -> "Waxing gibbous"
        age < 15.8 -> "Full moon"
        age < 21.1 -> "Waning gibbous"
        age < 23.1 -> "Last quarter"
        age < 28.5 -> "Waning crescent"
        else -> "New moon"
    }

    /**
     * The lit part of a moon of radius [r] at ([cx], [cy]) as seen from the northern hemisphere:
     * the edge of the disc on the sunlit side, closed by the terminator (a half ellipse).
     */
    fun litPath(age: Double, cx: Float, cy: Float, r: Float): Path {
        val k = cos(2 * PI * age / SYNODIC).toFloat() // 1 at new moon, -1 at full
        val outer = RectF(cx - r, cy - r, cx + r, cy + r)
        val ex = r * abs(k)
        val terminator = RectF(cx - ex, cy - r, cx + ex, cy + r)
        val crescent = k > 0
        return Path().apply {
            if (age < SYNODIC / 2) {
                arcTo(outer, -90f, 180f, true) // lit on the right
                arcTo(terminator, 90f, if (crescent) -180f else 180f, false)
            } else {
                arcTo(outer, -90f, -180f, true) // lit on the left
                arcTo(terminator, 90f, if (crescent) 180f else -180f, false)
            }
            close()
        }
    }
}
