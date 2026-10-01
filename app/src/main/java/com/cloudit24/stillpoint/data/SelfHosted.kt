package com.cloudit24.stillpoint.data

import com.cloudit24.stillpoint.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.OffsetDateTime
import javax.net.ssl.SSLException

/** Where messages come from: ntfy or Gotify, both servers you can run yourself. */
enum class MsgKind(val label: String) { OFF("Off"), NTFY("ntfy"), GOTIFY("Gotify") }

/** Your own servers. Addresses are settings; tokens are kept apart and never go into backup files. */
data class SelfHostedConfig(
    val haUrl: String = "",
    val haToken: String = "",
    val haEntities: List<String> = emptyList(),
    val kumaUrl: String = "",
    val kumaSlug: String = "",
    val msgKind: MsgKind = MsgKind.OFF,
    val msgUrl: String = "",
    val msgTopic: String = "",
    val msgToken: String = "",
) {
    val haReady get() = haUrl.isNotBlank() && haToken.isNotBlank()
    val kumaReady get() = kumaUrl.isNotBlank() && kumaSlug.isNotBlank()
    val msgReady get() = msgUrl.isNotBlank() && when (msgKind) {
        MsgKind.NTFY -> msgTopic.isNotBlank()
        MsgKind.GOTIFY -> msgToken.isNotBlank()
        MsgKind.OFF -> false
    }
}

data class HaEntity(val id: String, val name: String, val state: String, val unit: String) {
    val domain get() = id.substringBefore('.')
    val actionable get() = domain in ACTIONABLE
    val on get() = state in setOf("on", "open", "unlocked", "playing", "home", "heat", "cool")
    fun stateText(): String = when {
        domain in setOf("scene", "script", "button", "input_button") -> "Run"
        unit.isNotEmpty() -> "$state $unit"
        else -> state.replace('_', ' ')
    }

    companion object {
        val ACTIONABLE = setOf("light", "switch", "fan", "input_boolean", "cover", "lock", "scene", "script",
            "button", "input_button", "automation", "media_player")
        val SHOWN = ACTIONABLE + setOf("sensor", "binary_sensor", "climate", "person", "device_tracker", "weather", "alarm_control_panel")
    }
}

/** status: 1 up, 0 down, 2 pending, 3 maintenance, -1 no data yet. */
data class KumaMonitor(val id: Int, val name: String, val status: Int, val uptime24: Double?)
data class KumaStatus(val title: String, val monitors: List<KumaMonitor>)
data class SelfMessage(val id: String, val title: String, val text: String, val time: Long, val priority: Int)

/** An error worded for people. */
class Friendly(message: String) : IOException(message)

/** Home Assistant, Uptime Kuma, ntfy and Gotify. Each talks only to the address you entered. Call off the main thread. */
object SelfHosted {

    private fun base(u: String) = u.trim().trimEnd('/')

    private fun request(method: String, url: String, headers: Map<String, String>, body: String? = null): String {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw Friendly("That address doesn't look right. Start it with http:// or https://")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Stillpoint/${BuildConfig.VERSION_NAME}")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            return when (val code = conn.responseCode) {
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                401, 403 -> throw Friendly("It didn't accept the token. Check it in Settings.")
                404 -> throw Friendly("Nothing found at that address.")
                else -> throw Friendly("The server answered with an error ($code).")
            }
        } catch (e: Friendly) {
            throw e
        } catch (e: SSLException) {
            throw Friendly("The secure connection failed. A self-signed certificate? Try the http:// address on your home network.")
        } catch (e: UnknownHostException) {
            throw Friendly("Can't find that server. Offline, or the address is wrong.")
        } catch (e: ConnectException) {
            throw Friendly("Can't reach that server. Is it running, and are you on the right network?")
        } catch (e: SocketTimeoutException) {
            throw Friendly("The server took too long to answer.")
        } catch (e: IOException) {
            throw Friendly("Can't reach that server.")
        } finally {
            conn.disconnect()
        }
    }

    // ---- Home Assistant (REST API with a long-lived access token) ----

    private fun haHeaders(c: SelfHostedConfig) = mapOf("Authorization" to "Bearer ${c.haToken.trim()}")

    internal fun parseHa(o: JSONObject): HaEntity {
        val id = o.getString("entity_id")
        val attrs = o.optJSONObject("attributes")
        val name = attrs?.optString("friendly_name")?.takeIf { it.isNotBlank() } ?: id
        return HaEntity(id, name, o.optString("state"), attrs?.optString("unit_of_measurement").orEmpty())
    }

    /** The entities you chose, in your order. One that no longer exists is left out. */
    fun haStates(c: SelfHostedConfig): List<HaEntity> = c.haEntities.mapNotNull { id ->
        try {
            parseHa(JSONObject(request("GET", "${base(c.haUrl)}/api/states/${URLEncoder.encode(id, "UTF-8")}", haHeaders(c))))
        } catch (e: Friendly) {
            if (e.message?.startsWith("Nothing found") == true) null else throw e
        }
    }

    /** Everything worth showing, A to Z, for choosing in Settings. */
    fun haAll(c: SelfHostedConfig): List<HaEntity> {
        val a = JSONArray(request("GET", "${base(c.haUrl)}/api/states", haHeaders(c)))
        return List(a.length()) { parseHa(a.getJSONObject(it)) }.filter { it.domain in HaEntity.SHOWN }.sortedBy { it.name.lowercase() }
    }

    internal fun haService(e: HaEntity): String = when (e.domain) {
        "scene", "script" -> "turn_on"
        "button", "input_button" -> "press"
        "lock" -> if (e.state == "locked") "unlock" else "lock"
        "media_player" -> "media_play_pause"
        else -> "toggle"
    }

    fun haToggle(c: SelfHostedConfig, e: HaEntity) {
        request("POST", "${base(c.haUrl)}/api/services/${e.domain}/${haService(e)}", haHeaders(c),
            JSONObject().put("entity_id", e.id).toString())
    }

    // ---- Uptime Kuma (a public status page, no login) ----

    internal fun parseKuma(page: String, beats: String): KumaStatus {
        val p = JSONObject(page)
        val b = JSONObject(beats)
        val hb = b.optJSONObject("heartbeatList")
        val up = b.optJSONObject("uptimeList")
        val monitors = ArrayList<KumaMonitor>()
        val groups = p.optJSONArray("publicGroupList") ?: JSONArray()
        for (g in 0 until groups.length()) {
            val list = groups.getJSONObject(g).optJSONArray("monitorList") ?: continue
            for (i in 0 until list.length()) {
                val m = list.getJSONObject(i)
                val id = m.optInt("id")
                val beatsOf = hb?.optJSONArray("$id")
                val status = if (beatsOf != null && beatsOf.length() > 0) beatsOf.getJSONObject(beatsOf.length() - 1).optInt("status", -1) else -1
                val key = "${id}_24"
                monitors += KumaMonitor(id, m.optString("name"), status, if (up != null && up.has(key)) up.optDouble(key) else null)
            }
        }
        return KumaStatus(p.optJSONObject("config")?.optString("title").orEmpty(), monitors)
    }

    fun kuma(c: SelfHostedConfig): KumaStatus {
        val slug = URLEncoder.encode(c.kumaSlug.trim(), "UTF-8")
        val b = base(c.kumaUrl)
        return parseKuma(request("GET", "$b/api/status-page/$slug", emptyMap()),
            request("GET", "$b/api/status-page/heartbeat/$slug", emptyMap()))
    }

    fun kumaPage(c: SelfHostedConfig) = "${base(c.kumaUrl)}/status/${c.kumaSlug.trim()}"

    // ---- ntfy and Gotify: the latest messages ----

    internal fun parseNtfy(body: String): List<SelfMessage> = body.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        .filter { it.optString("event") == "message" }
        .map {
            SelfMessage(it.optString("id"), it.optString("title").ifBlank { it.optString("topic") }, it.optString("message"),
                it.optLong("time") * 1000, it.optInt("priority", 3))
        }
        .toList().sortedByDescending { it.time }

    internal fun parseGotify(body: String): List<SelfMessage> {
        val a = JSONObject(body).optJSONArray("messages") ?: return emptyList()
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            SelfMessage(o.optLong("id").toString(), o.optString("title"), o.optString("message"),
                runCatching { OffsetDateTime.parse(o.optString("date")).toInstant().toEpochMilli() }.getOrDefault(0L),
                // Gotify's scale is 0-10; 8 and above is urgent, like ntfy's 4 and 5.
                if (o.optInt("priority") >= 8) 5 else 3)
        }.sortedByDescending { it.time }
    }

    fun messages(c: SelfHostedConfig): List<SelfMessage> = when (c.msgKind) {
        MsgKind.NTFY -> {
            val topics = c.msgTopic.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val auth = if (c.msgToken.isNotBlank()) mapOf("Authorization" to "Bearer ${c.msgToken.trim()}") else emptyMap()
            parseNtfy(request("GET", "${base(c.msgUrl)}/$topics/json?poll=1&since=24h", auth))
        }
        MsgKind.GOTIFY -> parseGotify(request("GET", "${base(c.msgUrl)}/message?limit=10", mapOf("X-Gotify-Key" to c.msgToken.trim())))
        MsgKind.OFF -> emptyList()
    }
}
