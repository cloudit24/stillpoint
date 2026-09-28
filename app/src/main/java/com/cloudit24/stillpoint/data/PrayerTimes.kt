package com.cloudit24.stillpoint.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Sun angles below the horizon for Fajr and Isha; [ishaMinutes] > 0 means Isha is a fixed time after Maghrib. */
enum class PrayerMethod(val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
    UAE("UAE (Awqaf, 18.2°)", 18.2, 18.2),
    UMM_AL_QURA("Umm al-Qura, Makkah", 18.5, 0.0, 90),
    MWL("Muslim World League", 18.0, 17.0),
    KARACHI("Karachi (India, Pakistan, Sri Lanka)", 18.0, 18.0),
    EGYPT("Egyptian authority", 19.5, 17.5),
    KUWAIT("Kuwait", 18.0, 17.5),
    QATAR("Qatar", 18.0, 0.0, 90),
    SINGAPORE("Singapore, Malaysia", 20.0, 18.0),
    TURKEY("Turkey (Diyanet)", 18.0, 17.0),
    ISNA("North America (ISNA)", 15.0, 15.0),
}

enum class Prayer(val label: String, val arabic: String, val isPrayer: Boolean = true) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق", isPrayer = false),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء"),
}

/**
 * Prayer times and Qibla, calculated on the phone from latitude and longitude.
 * Standard astronomical method (same formulas as PrayTimes.org). Nothing goes online.
 */
object PrayerTimes {

    /** Epoch ms per prayer for [date]. A prayer is missing when the sun never reaches its angle (far north in summer). */
    fun forDate(date: LocalDate, lat: Double, lon: Double, method: PrayerMethod, hanafi: Boolean): Map<Prayer, Long> {
        val jd = julian(date) - lon / (15 * 24.0)
        // Rough guesses in hours, refined twice with the sun's position at each guess.
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            t = doubleArrayOf(
                sunAngleTime(jd, method.fajr, t[0], lat, before = true),
                sunAngleTime(jd, RISE_SET, t[1], lat, before = true),
                midDay(jd, t[2]),
                asrTime(jd, if (hanafi) 2.0 else 1.0, t[3], lat),
                sunAngleTime(jd, RISE_SET, t[4], lat, before = false),
                if (method.ishaMinutes > 0) 18.0 else sunAngleTime(jd, method.isha, t[5], lat, before = false),
            )
        }
        if (method.ishaMinutes > 0) t[5] = t[4] + method.ishaMinutes / 60.0

        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return Prayer.entries.mapIndexedNotNull { i, p ->
            val hours = t[i]
            if (hours.isNaN()) null
            else {
                val ms = midnightUtc + ((hours - lon / 15) * 3_600_000).roundToLong()
                p to (ms + 30_000) / 60_000 * 60_000 // nearest minute
            }
        }.toMap()
    }

    /** The next of the five prayers after [now] (sunrise skipped), looking into tomorrow if needed. */
    fun next(now: Long, lat: Double, lon: Double, method: PrayerMethod, hanafi: Boolean): Pair<Prayer, Long>? {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        for (d in listOf(today, today.plusDays(1))) {
            forDate(d, lat, lon, method, hanafi).entries
                .filter { it.key.isPrayer && it.value > now }
                .minByOrNull { it.value }
                ?.let { return it.key to it.value }
        }
        return null
    }

    /** Degrees clockwise from true north towards the Kaaba. */
    fun qibla(lat: Double, lon: Double): Double {
        val dLon = KAABA_LON - lon
        val deg = Math.toDegrees(atan2(sinD(dLon), cosD(lat) * tanD(KAABA_LAT) - sinD(lat) * cosD(dLon)))
        return (deg + 360) % 360
    }

    // ---- Astronomy ----

    private fun midDay(jd: Double, t: Double): Double = fixHour(12 - sun(jd + t / 24).second)

    private fun sunAngleTime(jd: Double, angle: Double, t: Double, lat: Double, before: Boolean): Double {
        val decl = sun(jd + t / 24).first
        val noon = midDay(jd, t)
        val x = (-sinD(angle) - sinD(decl) * sinD(lat)) / (cosD(decl) * cosD(lat))
        if (x < -1 || x > 1) return Double.NaN
        val span = Math.toDegrees(acos(x)) / 15
        return noon + if (before) -span else span
    }

    /** Asr: when an object's shadow is [factor] times its length plus its noon shadow. */
    private fun asrTime(jd: Double, factor: Double, t: Double, lat: Double): Double {
        val decl = sun(jd + t / 24).first
        val angle = -Math.toDegrees(atan(1 / (factor + tanD(abs(lat - decl)))))
        return sunAngleTime(jd, angle, t, lat, before = false)
    }

    /** Sun declination (degrees) and equation of time (hours). */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixDeg(357.529 + 0.98560028 * d)
        val q = fixDeg(280.459 + 0.98564736 * d)
        val l = fixDeg(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(Math.toDegrees(atan2(cosD(e) * sinD(l), cosD(l))) / 15)
        val decl = Math.toDegrees(asin(sinD(e) * sinD(l)))
        return decl to (q / 15 - ra)
    }

    fun julian(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    private const val RISE_SET = 0.833
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun fixDeg(a: Double) = ((a % 360) + 360) % 360
    private fun fixHour(h: Double) = ((h % 24) + 24) % 24
}
