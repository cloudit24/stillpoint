package com.cloudit24.stillpoint.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Every outside address Stillpoint may use. Each can be swapped for your own server or another service
 * (Settings, System, Online sources). Prayer times, Qibla, moon and dates are calculated on the phone.
 */
enum class SourceKey(val label: String, val default: String, val help: String) {
    WEATHER("Weather", "https://api.open-meteo.com",
        "An Open-Meteo server: the public one, a mirror, or your own (it's open source). /v1/forecast is added."),
    CITY("City search", "https://geocoding-api.open-meteo.com",
        "An Open-Meteo geocoding server. /v1/search is added."),
    RATES("Currency rates", "https://api.frankfurter.dev",
        "A Frankfurter server (open source, easy to run yourself). /v1/latest is added."),
    IP("Public IP", "https://api.ipify.org",
        "Any address that answers with just your IP address as plain text."),
    DNS("DNS lookup (Tools)", "https://cloudflare-dns.com/dns-query",
        "A DNS-over-HTTPS server that answers in JSON, like Cloudflare, Google (https://dns.google/resolve) or your own."),
}

/** Your own gold price: any web address, and where in its answer the price is (taken as 24K). */
data class CustomGold(val url: String = "", val path: String = "", val perGram: Boolean = false, val currency: String = "USD")

data class SourceConfig(val urls: Map<SourceKey, String> = emptyMap(), val gold: CustomGold = CustomGold()) {
    fun url(k: SourceKey): String = urls[k]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: k.default
    fun isCustom(k: SourceKey): Boolean = urls[k]?.isNotBlank() == true
}

/**
 * A number from a web answer: a dotted path into JSON ("data.price", "rates.XAU", "0.price"),
 * or with no path, the first number in the text.
 */
fun readNumber(body: String, path: String): Double? {
    val p = path.trim()
    if (p.isEmpty()) return Regex("""\d[\d,]*(\.\d+)?""").find(body)?.value?.replace(",", "")?.toDoubleOrNull()
    return runCatching {
        var cur: Any? = JSONTokener(body.trim()).nextValue()
        for (part in p.split('.')) {
            cur = when (val c = cur) {
                is JSONObject -> c.opt(part)
                is JSONArray -> c.opt(part.toInt())
                else -> null
            }
        }
        when (val c = cur) {
            is Number -> c.toDouble()
            is String -> c.replace(",", "").trim().toDoubleOrNull()
            else -> null
        }
    }.getOrNull()
}
