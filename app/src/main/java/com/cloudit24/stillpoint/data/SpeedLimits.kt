package com.cloudit24.stillpoint.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The posted speed limit of the road you're on, from OpenStreetMap (its "maxspeed" tag) through the
 * Overpass API. Only when you turn it on: it sends your position to overpass-api.de. Cached by place so
 * the same stretch of road isn't asked twice.
 */
object SpeedLimits {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private val cache = HashMap<String, Int>()   // "lat,lon" rounded to ~100 m -> km/h (0 = none known)

    /** km/h, or null when nothing is known here. Call off the main thread. */
    fun at(lat: Double, lon: Double): Int? {
        val key = "%.3f,%.3f".format(lat, lon)
        cache[key]?.let { return it.takeIf { v -> v > 0 } }
        val q = "[out:json][timeout:8];way(around:25,$lat,$lon)[highway][maxspeed];out tags 5;"
        val limit = runCatching {
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 8_000
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", "Stillpoint-Launcher")
            c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            val body = c.inputStream.bufferedReader().readText()
            val els = JSONObject(body).optJSONArray("elements") ?: return@runCatching 0
            var best = 0
            for (i in 0 until els.length()) {
                val v = parse(els.getJSONObject(i).optJSONObject("tags")?.optString("maxspeed").orEmpty())
                if (v > 0 && (best == 0 || v < best)) best = v   // several roads nearby: the lowest is the safe guess
            }
            best
        }.getOrDefault(0)
        if (limit >= 0) cache[key] = limit
        return limit.takeIf { it > 0 }
    }

    /** "80", "80 km/h", "50 mph" -> km/h. */
    private fun parse(raw: String): Int {
        val n = Regex("""\d+""").find(raw)?.value?.toIntOrNull() ?: return 0
        return if (raw.contains("mph", true)) (n * 1.609f).toInt() else n
    }
}
