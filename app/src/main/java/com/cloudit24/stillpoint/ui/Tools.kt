package com.cloudit24.stillpoint.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.LiveRepository
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.SourceKey
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Small everyday tools in one place, so people don't need a separate app for each. Offline unless noted. */
enum class Tool(val section: String, val title: String, val summary: String) {
    DEVICE("Device", "Device info", "Battery, storage, memory, screen, hardware"),
    PASSWORD("Security and text", "Password generator", "Strong passwords, copied in one tap"),
    HASH("Security and text", "Hash", "MD5, SHA-1, SHA-256, SHA-512 of any text"),
    BASE64("Security and text", "Base64", "Encode and decode"),
    UUIDS("Security and text", "UUID", "Random unique IDs"),
    JSON("Security and text", "JSON formatter", "Tidy, check or squeeze JSON"),
    QR("Security and text", "QR code", "Turn text or a link into a QR code"),
    MYIP("Network", "My addresses", "Local and public IP, gateway, DNS servers"),
    SUBNET("Network", "Subnet calculator", "Range, hosts and mask from an address"),
    PING("Network", "Ping", "Is a host reachable, and how fast"),
    PORT("Network", "Port check", "Is a port open on a host"),
    DNS("Network", "DNS lookup", "A, AAAA, MX, TXT, CNAME and NS records"),
    WIFI("Network", "Wi-Fi details", "Signal, speed and band"),
    WOL("Network", "Wake-on-LAN", "Wake a computer on your network"),
}

@Composable
fun ToolsScreen(vm: LauncherViewModel) {
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
    BackHandler { if (tool != null) tool = null else vm.screen = Screen.WIDGETS }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink,
                modifier = Modifier.clip(CircleShape).clickable { if (tool != null) tool = null else vm.screen = Screen.WIDGETS }.padding(8.dp))
            Text(tool?.title ?: "Tools", fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 8.dp))
        }
        when (tool) {
            null -> Tool.entries.groupBy { it.section }.forEach { (section, list) ->
                Text(section, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF121211))) {
                    list.forEach { t ->
                        Column(Modifier.fillMaxWidth().clickable { tool = t }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(t.title, fontSize = 16.sp)
                            Text(t.summary, color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            Tool.DEVICE -> DeviceTool()
            Tool.PASSWORD -> PasswordTool()
            Tool.HASH -> HashTool()
            Tool.BASE64 -> Base64Tool()
            Tool.UUIDS -> UuidTool()
            Tool.JSON -> JsonTool()
            Tool.QR -> QrTool()
            Tool.MYIP -> MyIpTool()
            Tool.SUBNET -> SubnetTool()
            Tool.PING -> PingTool()
            Tool.PORT -> PortTool()
            Tool.DNS -> DnsTool()
            Tool.WIFI -> WifiTool()
            Tool.WOL -> WolTool()
        }
    }
}

// ---------------- Shared pieces ----------------

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Stillpoint", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true, number: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(label, color = Accent, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

/** A label and a value; tap to copy the value. */
@Composable
private fun InfoRow(label: String, value: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { copy(ctx, value) }.padding(vertical = 7.dp)) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.42f))
        Text(value, fontSize = 14.sp, modifier = Modifier.weight(0.58f))
    }
}

/** Monospaced output; tap to copy. */
@Composable
private fun Output(text: String) {
    val ctx = LocalContext.current
    if (text.isEmpty()) return
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        .clip(RoundedCornerShape(14.dp)).background(Color(0xFF121211)).clickable { copy(ctx, text) }.padding(14.dp))
}

@Composable
private fun Hint(text: String) = Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))

private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

// ---------------- Device ----------------

@Composable
private fun DeviceTool() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val rows = remember(tick) { deviceInfo(ctx) }
    rows.forEach { (k, v) -> if (v.isEmpty()) Text(k, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) else InfoRow(k, v) }
    Row(Modifier.padding(top = 12.dp)) { Pill("Refresh") { tick++ } }
    Hint("Tap any line to copy it.")
}

private fun deviceInfo(ctx: Context): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    out += "Battery" to ""
    if (b != null) {
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        out += "Charge" to "$level%"
        out += "Status" to when (b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        out += "Health" to when (b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Too hot"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            else -> "Unknown"
        }
        out += "Temperature" to String.format(Locale.US, "%.1f °C", b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
        out += "Voltage" to String.format(Locale.US, "%.2f V", b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0)
        b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.let { out += "Type" to it }
    }
    out += "Storage and memory" to ""
    val fs = StatFs(Environment.getDataDirectory().path)
    out += "Storage" to "${gb(fs.totalBytes - fs.availableBytes)} used of ${gb(fs.totalBytes)}"
    val mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    out += "Memory" to "${gb(mi.totalMem - mi.availMem)} used of ${gb(mi.totalMem)}"
    out += "Screen" to ""
    val dm = ctx.resources.displayMetrics
    out += "Resolution" to "${dm.widthPixels} × ${dm.heightPixels}"
    out += "Density" to "${dm.densityDpi} dpi"
    val refresh = runCatching { if (Build.VERSION.SDK_INT >= 30) ctx.display?.refreshRate else null }.getOrNull()
    refresh?.let { out += "Refresh rate" to "${it.toInt()} Hz" }
    out += "Hardware and software" to ""
    out += "Model" to "${Build.MANUFACTURER} ${Build.MODEL}"
    out += "Device" to Build.DEVICE
    out += "Processor" to "${Runtime.getRuntime().availableProcessors()} cores · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}"
    out += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    out += "Security patch" to Build.VERSION.SECURITY_PATCH
    out += "Sensors" to "${ctx.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).size}"
    val up = SystemClock.elapsedRealtime() / 60_000
    out += "Up for" to "${up / 1440} d ${up / 60 % 24} h ${up % 60} min"
    return out
}

// ---------------- Security and text ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordTool() {
    val ctx = LocalContext.current
    var length by remember { mutableFloatStateOf(20f) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var seed by remember { mutableStateOf(0) }
    val pw = remember(length.toInt(), digits, symbols, upper, seed) {
        val sets = buildList {
            add("abcdefghijkmnopqrstuvwxyz")
            if (upper) add("ABCDEFGHJKLMNPQRSTUVWXYZ")
            if (digits) add("23456789")
            if (symbols) add("!@#$%^&*-_=+?")
        }
        val all = sets.joinToString("")
        val r = SecureRandom()
        // One from each chosen kind, the rest from all, then shuffled.
        val chars = (sets.map { it[r.nextInt(it.length)] } + List(length.toInt() - sets.size) { all[r.nextInt(all.length)] })
            .shuffled(r)
        chars.joinToString("")
    }
    Output(pw)
    Text("Length ${length.toInt()}", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    Slider(value = length, onValueChange = { length = it }, valueRange = 8f..64f)
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Capital letters", Modifier.weight(1f)); Switch(upper, { upper = it }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Numbers", Modifier.weight(1f)); Switch(digits, { digits = it }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Symbols", Modifier.weight(1f)); Switch(symbols, { symbols = it }) }
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("New password") { seed++ }
        Pill("Copy") { copy(ctx, pw) }
    }
    Hint("Made on the phone with a secure random source. Look-alike letters (l, 1, O, 0) are left out.")
}

@Composable
private fun HashTool() {
    var text by rememberSaveable { mutableStateOf("") }
    Field(text, { text = it }, "Text", singleLine = false)
    if (text.isNotEmpty()) listOf("MD5", "SHA-1", "SHA-256", "SHA-512").forEach { alg ->
        val hex = MessageDigest.getInstance(alg).digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        Text(alg, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Output(hex)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Base64Tool() {
    var text by rememberSaveable { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    Field(text, { text = it }, "Text or Base64", singleLine = false)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Encode") { out = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP) }
        Pill("Decode") {
            out = runCatching { String(Base64.decode(text.trim(), Base64.DEFAULT)) }.getOrElse { "That isn't valid Base64." }
        }
    }
    Output(out)
}

@Composable
private fun UuidTool() {
    var seed by remember { mutableStateOf(0) }
    val ids = remember(seed) { List(5) { UUID.randomUUID().toString() } }
    ids.forEach { Output(it) }
    Row { Pill("New IDs") { seed++ } }
    Hint("Tap one to copy it.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JsonTool() {
    var text by rememberSaveable { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    Field(text, { text = it }, "JSON", singleLine = false)
    fun parse(): Any? = JSONTokener(text.trim()).nextValue()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Tidy") {
            out = runCatching {
                when (val v = parse()) { is JSONObject -> v.toString(2); is JSONArray -> v.toString(2); else -> v.toString() }
            }.getOrElse { "Not valid JSON: ${it.message}" }.replace("\\/", "/")
        }
        Pill("Squeeze") {
            out = runCatching { parse().toString() }.getOrElse { "Not valid JSON: ${it.message}" }.replace("\\/", "/")
        }
        Pill("Check") { out = runCatching { parse(); "Valid JSON." }.getOrElse { "Not valid: ${it.message}" } }
    }
    Output(out)
}

@Composable
private fun QrTool() {
    var text by rememberSaveable { mutableStateOf("") }
    Field(text, { text = it }, "Text or link", singleLine = false)
    val bmp = remember(text) { if (text.isBlank()) null else runCatching { qr(text, 720) }.getOrNull() }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp))
    }
    Hint("Made on the phone. Point any camera at it.")
}

private fun qr(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

// ---------------- Network ----------------

@Composable
private fun MyIpTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var public by remember { mutableStateOf<String?>(null) }
    val rows = remember {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) }
        buildList {
            if (lp == null) add("Network" to "Not connected")
            else {
                add("Interface" to (lp.interfaceName ?: "?"))
                lp.linkAddresses.forEach { add((if (it.address.address.size == 4) "IPv4" else "IPv6") to it.toString()) }
                lp.routes.filter { it.isDefaultRoute && it.gateway != null }.forEach { add("Gateway" to it.gateway!!.hostAddress.orEmpty()) }
                lp.dnsServers.forEach { add("DNS server" to it.hostAddress.orEmpty()) }
                lp.domains?.let { add("Domain" to it) }
            }
        }
    }
    rows.forEach { (k, v) -> InfoRow(k, v) }
    InfoRow("Public IP", public ?: "Tap to look up")
    Row(Modifier.padding(top = 8.dp)) {
        Pill("Look up public IP") {
            public = "Looking…"
            scope.launch { public = withContext(Dispatchers.IO) { LiveRepository(Prefs(ctx).loadSources()).publicIp() } ?: "Couldn't reach the service." }
        }
    }
    Hint("The public IP uses the address set in Settings, Online sources.")
}

@Composable
private fun SubnetTool() {
    var text by rememberSaveable { mutableStateOf("192.168.1.10/24") }
    Field(text, { text = it }, "Address/prefix, like 10.0.0.5/22")
    val rows = remember(text) { subnet(text) }
    if (rows == null) Hint("Type an IPv4 address and a prefix length (0–32).") else rows.forEach { (k, v) -> InfoRow(k, v) }
}

/** Network facts for "a.b.c.d/n" (IPv4), or null if it can't be read. */
internal fun subnet(input: String): List<Pair<String, String>>? {
    val parts = input.trim().split("/")
    val ip = parts[0].split(".").mapNotNull { it.toIntOrNull()?.takeIf { v -> v in 0..255 } }
    val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 32
    if (ip.size != 4 || prefix !in 0..32) return null
    val addr = ip.fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
    val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    val net = addr and mask
    val bcast = net or (mask.inv() and 0xFFFFFFFFL)
    fun s(v: Long) = listOf(24, 16, 8, 0).joinToString(".") { ((v shr it) and 255).toString() }
    val hosts = when (prefix) { 32 -> 1L; 31 -> 2L; else -> (bcast - net - 1).coerceAtLeast(0) }
    val first = if (prefix >= 31) net else net + 1
    val last = if (prefix >= 31) bcast else bcast - 1
    val private = (ip[0] == 10) || (ip[0] == 172 && ip[1] in 16..31) || (ip[0] == 192 && ip[1] == 168)
    return listOf(
        "Network" to "${s(net)}/$prefix",
        "Mask" to s(mask),
        "Wildcard" to s(mask.inv() and 0xFFFFFFFFL),
        "Broadcast" to s(bcast),
        "First host" to s(first),
        "Last host" to s(last),
        "Hosts" to "%,d".format(hosts),
        "Kind" to if (private) "Private (home or office network)" else "Public",
    )
}

@Composable
private fun PingTool() {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("1.1.1.1") }
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Field(host, { host = it }, "Host or address")
    Row { Pill(if (busy) "Pinging…" else "Ping") {
        if (!busy) { busy = true; out = ""; scope.launch { out = withContext(Dispatchers.IO) { ping(host.trim()) }; busy = false } }
    } }
    Output(out)
    Hint("Uses Android's ping. If it isn't allowed, it times a web connection instead (port 443).")
}

private fun ping(host: String): String {
    if (host.isEmpty()) return "Type a host first."
    runCatching {
        val p = ProcessBuilder("ping", "-c", "4", "-W", "2", host).redirectErrorStream(true).start()
        if (p.waitFor(15, TimeUnit.SECONDS)) {
            val text = p.inputStream.bufferedReader().readText().trim()
            if (text.isNotEmpty() && p.exitValue() != 2) return text
        } else p.destroy()
    }
    // No ping on this phone: time four connections to the web port instead.
    val times = (1..4).map {
        runCatching {
            val t0 = System.nanoTime()
            Socket().use { s -> s.connect(InetSocketAddress(host, 443), 2000) }
            (System.nanoTime() - t0) / 1_000_000
        }.getOrNull()
    }
    val ok = times.filterNotNull()
    return if (ok.isEmpty()) "$host didn't answer." else
        "Connected to $host:443 ${ok.size} of 4 times\n" + times.mapIndexed { i, t -> "#${i + 1}  ${t?.let { "$it ms" } ?: "no answer"}" }.joinToString("\n")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortTool() {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var ports by rememberSaveable { mutableStateOf("22, 80, 443") }
    var out by remember { mutableStateOf("") }
    Field(host, { host = it }, "Host or address")
    Field(ports, { ports = it }, "Ports, separated by commas")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Check") {
            out = "Checking…"
            scope.launch {
                val list = ports.split(",", " ").mapNotNull { it.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } }.distinct().take(30)
                out = withContext(Dispatchers.IO) {
                    list.joinToString("\n") { port ->
                        val t0 = System.nanoTime()
                        val r = runCatching { Socket().use { s -> s.connect(InetSocketAddress(host.trim(), port), 1500) } }
                        "$port  " + if (r.isSuccess) "open (${(System.nanoTime() - t0) / 1_000_000} ms)" else "closed or filtered"
                    }.ifEmpty { "Type at least one port." }
                }
            }
        }
        Pill("Common ports") { ports = "21, 22, 25, 53, 80, 110, 143, 443, 445, 587, 993, 3306, 3389, 5432, 8080, 8443" }
    }
    Output(out)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("A") }
    var out by remember { mutableStateOf("") }
    Field(name, { name = it }, "Domain, like example.com")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("A", "AAAA", "MX", "TXT", "CNAME", "NS").forEach { t ->
            Text(t, color = if (t == type) Color.Black else Accent, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (t == type) Accent else Color.White.copy(alpha = 0.07f)).clickable { type = t }
                .padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
    Row(Modifier.padding(top = 10.dp)) {
        Pill("Look up") {
            out = "Looking up…"
            scope.launch { out = withContext(Dispatchers.IO) { dns(Prefs(ctx).loadSources().url(SourceKey.DNS), name.trim(), type) } }
        }
    }
    Output(out)
    Hint("Asked through DNS over HTTPS (Settings, Online sources); A and AAAA fall back to the phone's own DNS.")
}

private fun dns(server: String, name: String, type: String): String {
    if (name.isEmpty()) return "Type a domain first."
    val viaDoh = runCatching {
        val conn = (URL("$server?name=${URLEncoder.encode(name, "UTF-8")}&type=$type").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/dns-json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val answers = JSONObject(body).optJSONArray("Answer") ?: return "No $type records for $name."
        (0 until answers.length()).joinToString("\n") { i ->
            val a = answers.getJSONObject(i)
            "${a.optString("data")}   (TTL ${a.optInt("TTL")} s)"
        }
    }.getOrNull()
    if (viaDoh != null) return viaDoh
    if (type == "A" || type == "AAAA") return runCatching {
        InetAddress.getAllByName(name).filter { (it.address.size == 4) == (type == "A") }.joinToString("\n") { it.hostAddress.orEmpty() }
            .ifEmpty { "No $type records for $name." }
    }.getOrElse { "Couldn't look up $name." }
    return "Couldn't reach the DNS server."
}

@Composable
private fun WifiTool() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val rows = remember(tick) {
        @Suppress("DEPRECATION")
        val info = ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
        if (info == null || info.networkId == -1) listOf("Wi-Fi" to "Not connected")
        else {
            val freq = info.frequency
            @Suppress("DEPRECATION")
            val ip = info.ipAddress
            listOf(
                "Signal" to "${info.rssi} dBm · " + when {
                    info.rssi >= -55 -> "excellent"
                    info.rssi >= -67 -> "good"
                    info.rssi >= -75 -> "fair"
                    else -> "weak"
                },
                "Link speed" to "${info.linkSpeed} Mbps",
                "Band" to when { freq >= 5925 -> "6 GHz"; freq >= 4900 -> "5 GHz"; else -> "2.4 GHz" } + " (channel frequency $freq MHz)",
                "Address" to listOf(0, 8, 16, 24).joinToString(".") { ((ip shr it) and 255).toString() },
                "Network name" to info.ssid.trim('"').takeUnless { it.contains("unknown") }.orEmpty().ifEmpty { "Hidden by Android (needs location access)" },
            )
        }
    }
    rows.forEach { (k, v) -> InfoRow(k, v) }
    Row(Modifier.padding(top = 12.dp)) { Pill("Refresh") { tick++ } }
}

@Composable
private fun WolTool() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("stillpoint", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var mac by rememberSaveable { mutableStateOf(sp.getString("wol_mac", "") ?: "") }
    var addr by rememberSaveable { mutableStateOf(sp.getString("wol_addr", "255.255.255.255") ?: "255.255.255.255") }
    var out by remember { mutableStateOf("") }
    Field(mac, { mac = it }, "MAC address, like AA:BB:CC:DD:EE:FF")
    Field(addr, { addr = it }, "Broadcast address")
    Row { Pill("Wake") {
        sp.edit().putString("wol_mac", mac).putString("wol_addr", addr).apply()
        scope.launch { out = withContext(Dispatchers.IO) { wake(mac, addr) } }
    } }
    Output(out)
    Hint("The computer must have Wake-on-LAN turned on, and this phone must be on the same network.")
}

private fun wake(mac: String, addr: String): String {
    val bytes = mac.split(":", "-", ".").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull(16)?.toByte() }
    if (bytes.size != 6) return "That MAC address doesn't look right."
    val packet = ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten().toByteArray()
    return runCatching {
        DatagramSocket().use { s ->
            s.broadcast = true
            val target = InetAddress.getByName(addr.trim())
            s.send(DatagramPacket(packet, packet.size, target, 9))
            s.send(DatagramPacket(packet, packet.size, target, 7))
        }
        "Magic packet sent to ${mac.uppercase()}."
    }.getOrElse { "Couldn't send: ${it.message}" }
}
