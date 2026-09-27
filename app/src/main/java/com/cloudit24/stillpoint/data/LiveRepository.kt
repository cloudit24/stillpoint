package com.cloudit24.stillpoint.data

import com.cloudit24.stillpoint.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Opt-in live data. No keys, no accounts, no cookies.
 *  - Weather and city search: Open-Meteo (open source, CC BY 4.0), coordinates rounded to ~10 km.
 *  - Gold: Swissquote public quotes (XAU/USD mid price).
 *  - Currency: fixed pegs for AED/SAR, otherwise Frankfurter (open source, European Central Bank rates).
 * Call from a background thread.
 */
class LiveRepository {

    fun searchCity(query: String): List<City> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val body = get("https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&name=" +
            URLEncoder.encode(q, "UTF-8")) ?: return emptyList()
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            List(results.length()) { i ->
                val o = results.getJSONObject(i)
                val region = o.optString("admin1").takeIf { it.isNotBlank() }
                City(
                    name = o.getString("name"),
                    country = listOfNotNull(region, o.optString("country").takeIf { it.isNotBlank() }).joinToString(", "),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun weather(city: City): WeatherNow? {
        val body = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${round1(city.lat)}&longitude=${round1(city.lon)}" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto",
        ) ?: return null
        return runCatching {
            val c = JSONObject(body).getJSONObject("current")
            WeatherNow(
                tempC = c.getDouble("temperature_2m"),
                code = c.getInt("weather_code"),
                isDay = c.optInt("is_day", 1) == 1,
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    fun gold(currency: String): GoldQuote? {
        val usd = goldUsdPerOz() ?: return null
        val fx = usdTo(currency) ?: return null
        return GoldQuote(usd, currency, fx, System.currentTimeMillis())
    }

    /** Mid of bid/ask from the first platform that reports a price. */
    private fun goldUsdPerOz(): Double? {
        val body = get("https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAU/USD") ?: return null
        return runCatching {
            val platforms = JSONArray(body)
            (0 until platforms.length()).firstNotNullOfOrNull { i ->
                val prices = platforms.getJSONObject(i).optJSONArray("spreadProfilePrices") ?: return@firstNotNullOfOrNull null
                if (prices.length() == 0) return@firstNotNullOfOrNull null
                val p = prices.getJSONObject(0)
                val bid = p.optDouble("bid")
                val ask = p.optDouble("ask")
                if (bid.isNaN() || ask.isNaN() || bid <= 0) null else (bid + ask) / 2
            }
        }.getOrNull()
    }

    private fun usdTo(currency: String): Double? {
        PEGS[currency]?.let { return it }
        // Frankfurter moved to frankfurter.dev; the old host is kept as a fallback.
        val body = get("https://api.frankfurter.dev/v1/latest?base=USD&symbols=$currency")
            ?: get("https://api.frankfurter.app/latest?from=USD&to=$currency")
            ?: return null
        return runCatching { JSONObject(body).getJSONObject("rates").getDouble(currency) }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Stillpoint/${BuildConfig.VERSION_NAME} (github.com/cloudit24/stillpoint)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** One decimal place (~11 km) is enough for a city forecast and says less about where you are. */
    private fun round1(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

    private companion object {
        /** Official fixed exchange rates to the US dollar. */
        val PEGS = mapOf("USD" to 1.0, "AED" to 3.6725, "SAR" to 3.75)
    }
}

const val GRAMS_PER_TROY_OUNCE = 31.1034768

/** Price for the chosen karat and unit, in the quote's currency. */
fun GoldQuote.priceFor(karat: Int, perGram: Boolean): Double {
    val perOz = usdPerOz * fxRate * (karat / 24.0)
    return if (perGram) perOz / GRAMS_PER_TROY_OUNCE else perOz
}
