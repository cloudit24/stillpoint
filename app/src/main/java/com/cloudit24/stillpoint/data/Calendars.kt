package com.cloudit24.stillpoint.data

import java.time.Year
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.floor
import kotlin.math.sin

/** Hijri (Arabic), Indian national (Saka), Malayalam and Tamil dates, calculated on the phone. */
object Calendars {

    /** e.g. "16 Rabi al-Akhir 1448 AH". [adjust] shifts by whole days for local moon sighting. */
    fun hijri(date: LocalDate, adjust: Int): String {
        val d = date.plusDays(adjust.toLong())
        // Umm al-Qura tables built into Android; the arithmetic calendar if they are missing or out of range.
        val (y, m, day) = runCatching {
            val h = HijrahDate.from(d)
            Triple(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
        }.getOrElse { tabularHijri(d) }
        return "$day ${HIJRI_MONTHS[m - 1]} $y AH"
    }

    /**
     * e.g. "புரட்டாசி 12 · பராபவ · தி.பி. 2057".
     * Solar Tamil calendar: a month starts when the sun enters the next sidereal sign (Lahiri);
     * if that happens after sunset (at Chennai), the month starts the next day.
     */
    fun tamil(date: LocalDate): String {
        val month = signAtSunset(date)
        var start = date
        while (start.isAfter(date.minusDays(33)) && signAtSunset(start.minusDays(1)) == month) start = start.minusDays(1)
        val day = date.toEpochDay() - start.toEpochDay() + 1

        // The year starts on Chithirai 1 (mid-April); Thai to Panguni belong to the year that began last April.
        val startYear = if (date.monthValue <= 4 && month >= 8) date.year - 1 else date.year
        val yearName = TAMIL_YEARS[Math.floorMod(startYear - 1987, 60)]
        // Thiruvalluvar year changes on Thai 1 (mid-January).
        val tv = date.year + 31 - if (date.monthValue <= 2 && month == 8) 1 else 0
        return "${TAMIL_MONTHS[month]} $day · $yearName · தி.பி. $tv"
    }

    /**
     * Indian national calendar, e.g. "9 Ashvin 1948 Saka". The year starts on 22 March (21 March in leap years);
     * Chaitra has 30 days (31 in leap years), the next five months 31, the last six 30.
     */
    fun saka(date: LocalDate): String {
        fun chaitra1(y: Int) = LocalDate.of(y, 3, if (Year.isLeap(y.toLong())) 21 else 22)
        val gy = if (date.isBefore(chaitra1(date.year))) date.year - 1 else date.year
        val leap = Year.isLeap(gy.toLong())
        var left = date.toEpochDay() - chaitra1(gy).toEpochDay()
        var m = 0
        while (true) {
            val len = when {
                m == 0 -> if (leap) 31 else 30
                m <= 5 -> 31
                else -> 30
            }
            if (left < len) break
            left -= len
            m++
        }
        return "${left + 1} ${SAKA_MONTHS[m]} ${gy - 78} Saka"
    }

    /**
     * Malayalam calendar (Kollavarsham), e.g. "കന്നി 15 · കൊല്ലവർഷം 1202". Solar months like Tamil, but a month
     * starts on the day the sun changes sign if that happens before three fifths of the daytime (at Thiruvananthapuram).
     * The year starts on Chingam 1, in mid-August.
     */
    fun malayalam(date: LocalDate): String {
        val month = signAtAparahna(date)
        var start = date
        while (start.isAfter(date.minusDays(33)) && signAtAparahna(start.minusDays(1)) == month) start = start.minusDays(1)
        val day = date.toEpochDay() - start.toEpochDay() + 1
        val year = if (month >= 4 && date.monthValue >= 8) date.year - 824 else date.year - 825
        return "${MALAYALAM_MONTHS[month]} $day · കൊല്ലവർഷം $year"
    }

    private fun signAtAparahna(date: LocalDate): Int {
        val t = PrayerTimes.forDate(date, TVM_LAT, TVM_LON, PrayerMethod.MWL, false)
        val rise = t[Prayer.SUNRISE]
        val set = t[Prayer.MAGHRIB]
        val at = if (rise != null && set != null) rise + (set - rise) * 3 / 5
        else date.toEpochDay() * 86_400_000L + 8 * 3_600_000L // ~13:30 IST
        return (siderealSun(at) / 30).toInt().coerceIn(0, 11)
    }

    /** 0 = Mesha (Chithirai) ... 11 = Meena (Panguni), for the sun at Chennai sunset on [date]. */
    private fun signAtSunset(date: LocalDate): Int {
        val sunset = PrayerTimes.forDate(date, CHENNAI_LAT, CHENNAI_LON, PrayerMethod.MWL, false)[Prayer.MAGHRIB]
            ?: (date.toEpochDay() * 86_400_000L + 12 * 3_600_000L + 30 * 60_000L) // ~18:00 IST
        return (siderealSun(sunset) / 30).toInt().coerceIn(0, 11)
    }

    /** Sun's sidereal longitude in degrees (Lahiri ayanamsa), accurate to a few hundredths of a degree. */
    private fun siderealSun(ms: Long): Double {
        val jd = ms / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sinD(m) +
            (0.019993 - 0.000101 * t) * sinD(2 * m) + 0.000289 * sinD(3 * m)
        val apparent = l0 + c - 0.00569 - 0.00478 * sinD(125.04 - 1934.136 * t)
        val ayanamsa = 23.853 + 1.3969 * t
        return ((apparent - ayanamsa) % 360 + 360) % 360
    }

    /** Arithmetic (Kuwaiti) Hijri calendar: year, month, day. */
    private fun tabularHijri(d: LocalDate): Triple<Int, Int, Int> {
        val jdn = d.toEpochDay() + 2440588
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val m = (24 * l) / 709
        val day = l - (709 * m) / 24
        val y = 30 * n + j - 30
        return Triple(y.toInt(), m.toInt(), day.toInt())
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d - 360 * floor(d / 360)))

    private const val TVM_LAT = 8.5241
    private const val TVM_LON = 76.9366

    private val SAKA_MONTHS = listOf(
        "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadra",
        "Ashvin", "Kartika", "Agrahayana", "Pausha", "Magha", "Phalguna",
    )

    private val MALAYALAM_MONTHS = listOf(
        "മേടം", "ഇടവം", "മിഥുനം", "കർക്കടകം", "ചിങ്ങം", "കന്നി",
        "തുലാം", "വൃശ്ചികം", "ധനു", "മകരം", "കുംഭം", "മീനം",
    )

    private const val CHENNAI_LAT = 13.0827
    private const val CHENNAI_LON = 80.2707

    private val HIJRI_MONTHS = listOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Akhir", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
    )

    private val TAMIL_MONTHS = listOf(
        "சித்திரை", "வைகாசி", "ஆனி", "ஆடி", "ஆவணி", "புரட்டாசி",
        "ஐப்பசி", "கார்த்திகை", "மார்கழி", "தை", "மாசி", "பங்குனி",
    )

    /** The 60-year cycle; 1987 began Prabhava. */
    private val TAMIL_YEARS = listOf(
        "பிரபவ", "விபவ", "சுக்ல", "பிரமோதூத", "பிரசோற்பத்தி", "ஆங்கீரச", "ஸ்ரீமுக", "பவ", "யுவ", "தாது",
        "ஈஸ்வர", "வெகுதானிய", "பிரமாதி", "விக்கிரம", "விஷு", "சித்திரபானு", "சுபானு", "தாரண", "பார்த்திப", "விய",
        "சர்வசித்து", "சர்வதாரி", "விரோதி", "விக்ருதி", "கர", "நந்தன", "விஜய", "ஜய", "மன்மத", "துன்முகி",
        "ஹேவிளம்பி", "விளம்பி", "விகாரி", "சார்வரி", "பிலவ", "சுபகிருது", "சோபகிருது", "குரோதி", "விசுவாவசு", "பராபவ",
        "பிலவங்க", "கீலக", "சௌமிய", "சாதாரண", "விரோதகிருது", "பரிதாபி", "பிரமாதீச", "ஆனந்த", "ராட்சச", "நள",
        "பிங்கள", "காளயுக்தி", "சித்தார்த்தி", "ரௌத்திரி", "துன்மதி", "துந்துபி", "ருத்ரோத்காரி", "ரக்தாட்சி", "குரோதன", "அட்சய",
    )
}
