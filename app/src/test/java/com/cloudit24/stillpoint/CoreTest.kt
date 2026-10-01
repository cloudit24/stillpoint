package com.cloudit24.stillpoint

import com.cloudit24.stillpoint.data.SelfHosted
import com.cloudit24.stillpoint.data.HaEntity
import com.cloudit24.stillpoint.data.BuiltIn
import com.cloudit24.stillpoint.ui.dueLabel
import com.cloudit24.stillpoint.ui.subnet
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.Moon
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.PrayerMethod
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.readNumber
import com.cloudit24.stillpoint.update.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The things that must never quietly go wrong. Run on every push by GitHub Actions;
 * a failing check stops the build before anything reaches a phone.
 */
class CoreTest {
    private val dubai = ZoneId.of("Asia/Dubai")

    private fun minutesOff(ms: Long, expected: String): Long {
        val got = Instant.ofEpochMilli(ms).atZone(dubai).toLocalTime()
        val want = LocalTime.parse(expected)
        return abs(got.toSecondOfDay() - want.toSecondOfDay()) / 60L
    }

    @Test
    fun prayerTimesDubai() {
        // Dubai, 29 September 2026, UAE method, standard Asr (as shown by the app and the official timetable, ±2 min).
        val t = PrayerTimes.forDate(LocalDate.of(2026, 9, 29), 25.2048, 55.2708, PrayerMethod.UAE, false)
        mapOf(
            Prayer.FAJR to "04:53", Prayer.DHUHR to "12:09", Prayer.ASR to "15:34",
            Prayer.MAGHRIB to "18:08", Prayer.ISHA to "19:25",
        ).forEach { (p, time) ->
            val off = minutesOff(t.getValue(p), time)
            assertTrue("$p is $off min away from $time", off <= 2)
        }
    }

    @Test
    fun prayerOrderIsAlwaysRight() {
        // Every day of a year, in a few cities: Fajr < Sunrise < Dhuhr < Asr < Maghrib < Isha.
        // Dubai, Chennai, Makkah, Jakarta, Cape Town. (Far north, Fajr and Isha can be missing in summer.)
        val cities = listOf(25.2048 to 55.2708, 13.0827 to 80.2707, 21.4225 to 39.8262, -6.2088 to 106.8456, -33.9249 to 18.4241)
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            for ((lat, lon) in cities) {
                val t = PrayerTimes.forDate(d, lat, lon, PrayerMethod.MWL, false)
                val seq = Prayer.entries.mapNotNull { t[it] }
                assertEquals("missing time on $d at $lat", Prayer.entries.size, seq.size)
                assertEquals("order on $d at $lat", seq.sorted(), seq)
            }
            d = d.plusDays(1)
        }
    }

    @Test
    fun spanFindsCurrentAndNext() {
        val at = ZonedDateTime.of(2026, 9, 29, 16, 18, 0, 0, dubai).toInstant().toEpochMilli()
        val span = PrayerTimes.span(at, 25.2048, 55.2708, PrayerMethod.UAE, false)!!
        assertEquals(Prayer.ASR, span.current)
        assertEquals(Prayer.MAGHRIB, span.next)
        assertTrue(span.nextAt > at && span.currentAt <= at)
    }

    @Test
    fun qiblaFromDubai() {
        val q = PrayerTimes.qibla(25.2048, 55.2708)
        assertTrue("qibla $q", abs(q - 258.0) < 1.5)
    }

    @Test
    fun hijriDate() {
        assertEquals("18 Rabi al-Akhir 1448 AH", Calendars.hijri(LocalDate.of(2026, 9, 29), 0))
    }

    @Test
    fun tamilNewYear() {
        assertTrue(Calendars.tamil(LocalDate.of(2026, 4, 14)).startsWith("சித்திரை 1 "))
        assertTrue(Calendars.tamil(LocalDate.of(2026, 4, 13)).startsWith("பங்குனி"))
    }

    @Test
    fun moonPhase() {
        val ms = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val age = Moon.age(ms)
        assertTrue("age $age", abs(age - 17.6) < 0.5)
        assertEquals("Waning gibbous", Moon.name(age))
        assertTrue(abs(Moon.illumination(age) - 0.91) < 0.03)
    }

    @Test
    fun readNumberFromYourOwnSource() {
        assertEquals(2648.3, readNumber("""{"data":{"price":2648.3}}""", "data.price")!!, 0.001)
        assertEquals(517.25, readNumber("""[{"price":"517.25"}]""", "0.price")!!, 0.001)
        assertEquals(2648.3, readNumber("Gold: USD 2,648.30 per ounce", "")!!, 0.001)
        assertNull(readNumber("""{"data":{}}""", "data.price"))
    }

    @Test
    fun selfHostedParsing() {
        val ntfy = "{\"id\":\"a\",\"time\":1700000000,\"event\":\"open\",\"topic\":\"x\"}\n" +
            "{\"id\":\"b\",\"time\":1700000100,\"event\":\"message\",\"topic\":\"alerts\",\"message\":\"Disk full\",\"priority\":5}\n"
        val n = SelfHosted.parseNtfy(ntfy)
        assertEquals(1, n.size)
        assertEquals("alerts", n[0].title)
        assertEquals(1700000100000L, n[0].time)

        val g = SelfHosted.parseGotify("{\"messages\":[{\"id\":7,\"title\":\"Backup\",\"message\":\"Done\",\"date\":\"2026-10-01T10:00:00+04:00\",\"priority\":8}]}")
        assertEquals("Backup", g[0].title)
        assertEquals(5, g[0].priority)

        val page = "{\"config\":{\"title\":\"Home\"},\"publicGroupList\":[{\"name\":\"S\",\"monitorList\":[{\"id\":1,\"name\":\"NAS\"},{\"id\":2,\"name\":\"Router\"}]}]}"
        val beats = "{\"heartbeatList\":{\"1\":[{\"status\":1},{\"status\":0}],\"2\":[{\"status\":1}]},\"uptimeList\":{\"1_24\":0.98}}"
        val k = SelfHosted.parseKuma(page, beats)
        assertEquals("Home", k.title)
        assertEquals(listOf(0, 1), k.monitors.map { it.status })
        assertEquals(0.98, k.monitors[0].uptime24!!, 0.0001)

        assertEquals("unlock", SelfHosted.haService(HaEntity("lock.door", "Door", "locked", "")))
        assertEquals("turn_on", SelfHosted.haService(HaEntity("scene.night", "Night", "scening", "")))
        assertEquals("toggle", SelfHosted.haService(HaEntity("light.desk", "Desk", "on", "")))
    }

    @Test
    fun shelfCardIds() {
        BuiltIn.entries.forEach { b ->
            (0..99).forEach { shelf ->
                assertTrue(b.id(shelf) < 0)
                assertEquals(b, BuiltIn.of(b.id(shelf)))
            }
        }
        assertNull(BuiltIn.of(42))
        assertEquals(BuiltIn.entries.size, BuiltIn.entries.map { it.code }.toSet().size)
    }

    @Test
    fun sakaCalendar() {
        assertEquals("9 Ashvin 1948 Saka", Calendars.saka(LocalDate.of(2026, 10, 1)))
        assertEquals("1 Chaitra 1946 Saka", Calendars.saka(LocalDate.of(2024, 3, 21)))
        assertEquals("30 Phalguna 1947 Saka", Calendars.saka(LocalDate.of(2026, 3, 21)))
        assertEquals("1 Chaitra 1948 Saka", Calendars.saka(LocalDate.of(2026, 3, 22)))
    }

    @Test
    fun malayalamCalendar() {
        val m = Calendars.malayalam(LocalDate.of(2026, 10, 1))
        assertTrue(m, m.startsWith("കന്നി") && m.endsWith("1202"))
        assertTrue(Calendars.malayalam(LocalDate.of(2026, 9, 1)).startsWith("ചിങ്ങം"))
    }

    @Test
    fun dueDayInWords() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals("today" to false, dueLabel(today.toEpochDay(), today))
        assertEquals("tomorrow" to false, dueLabel(today.toEpochDay() + 1, today))
        assertEquals("overdue" to true, dueLabel(today.toEpochDay() - 3, today))
        assertNull(dueLabel(-1, today))
    }

    @Test
    fun subnetCalculator() {
        val r = subnet("192.168.1.10/24")!!.toMap()
        assertEquals("192.168.1.0/24", r["Network"])
        assertEquals("255.255.255.0", r["Mask"])
        assertEquals("192.168.1.255", r["Broadcast"])
        assertEquals("254", r["Hosts"])
        assertEquals("10.0.3.255", subnet("10.0.0.5/22")!!.toMap()["Broadcast"])
        assertNull(subnet("300.1.1.1/24"))
    }

    @Test
    fun versionsCompareAsNumbers() {
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("0.27.1", "0.27.0"))
        assertFalse(isNewerVersion("0.27.0", "0.27.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }

    @Test
    fun taskAlertTime() {
        val day = java.time.LocalDate.of(2026, 10, 2).toEpochDay()
        val t = com.cloudit24.stillpoint.data.TaskItem(1, "Pay bill", false, day, 18 * 60)
        val at = com.cloudit24.stillpoint.widget.TaskAlerts.at(t)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 2, 18, 0),
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault()))
        assertEquals(-1L, com.cloudit24.stillpoint.widget.TaskAlerts.at(t.copy(remind = -1)))
        assertEquals(-1L, com.cloudit24.stillpoint.widget.TaskAlerts.at(t.copy(done = true)))
    }
}
