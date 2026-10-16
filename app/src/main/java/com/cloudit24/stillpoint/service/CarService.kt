package com.cloudit24.stillpoint.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cloudit24.stillpoint.MainActivity
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Camera
import com.cloudit24.stillpoint.data.LauncherSettings
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.SpeedCameras
import com.cloudit24.stillpoint.data.SpeedLimits
import com.cloudit24.stillpoint.llm.Voice
import com.cloudit24.stillpoint.notify.NotifyHub
import com.cloudit24.stillpoint.ui.carBeep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the drive looks like right now; the car screen draws this. */
data class CarState(
    val speedMs: Float = -1f,
    val accuracy: Float = -1f,                 // metres; how good the fix is
    val sats: Int = -1,                        // satellites used in the fix
    val here: Pair<Double, Double>? = null,
    val osmLimit: Int? = null,                 // km/h from the map, when known
    val nearest: Pair<Camera, Float>? = null,  // the closest camera within a kilometre, and its distance
)

/**
 * The drive itself, as a foreground service: GPS once a second, the posted limit and the cameras from the map,
 * the warning tones, and, with spoken alerts on, a voice that says the limit, when you are over it, a camera ahead,
 * and each notification as it arrives. It keeps going when the car screen is left by accident or the screen goes
 * dark, until End drive in the notification (or on the screen), or the car's Bluetooth drops.
 */
class CarService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var voice: Voice
    private var lm: LocationManager? = null
    private var listener: LocationListener? = null
    private var gnss: android.location.GnssStatus.Callback? = null

    // Working memory of the drive.
    private var last: Location? = null
    private var asked: Pair<Double, Double>? = null
    private var cameras: List<Camera> = emptyList()
    private var camCentre: Pair<Double, Double>? = null
    private var lastTone = 0L
    private var warnedNear = false
    private var wasOver = false
    private var lastOverSaid = 0L
    private var saidLimit = 0
    private var saidWithVoice = false
    private val warnedCams = HashSet<String>()
    private var lastCamDist = Float.MAX_VALUE
    private val readKeys = HashSet<String>()
    private var primed = false   // the notifications that were already there when the drive began are not read

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        voice = Voice(this)
        startForeground(NOTE_ID, note())
        running.value = true
        state.value = CarState()
        listen()
        // Notifications, read aloud as they arrive.
        scope.launch {
            NotifyHub.items.collect { list ->
                val s = prefs.loadSettings()
                val fresh = list.filter { it.pkg !in s.notifyOff && readKeys.add("${it.key}@${it.postTime}") }
                if (!primed) { primed = true; return@collect }
                if (s.carAlert <= 0 || !s.carReadAloud) return@collect
                fresh.filter { s.carAlert >= 2 || it.important || it.special }.sortedBy { it.postTime }.forEach { n ->
                    val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.pkg, 0)).toString() }.getOrDefault("")
                    val words = listOf(app, n.title, n.text.take(220)).filter { it.isNotBlank() }.joinToString(". ")
                    if (words.isNotBlank()) voice.speak(words)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { listener?.let { lm?.removeUpdates(it) }; gnss?.let { lm?.unregisterGnssStatusCallback(it) } }
        voice.release()
        scope.cancel()
        running.value = false
        state.value = CarState()
        super.onDestroy()
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        this.lm = lm
        val l = object : LocationListener {
            override fun onLocationChanged(l: Location) = onFix(l)
            @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { state.value = state.value.copy(speedMs = -1f) }
        }
        val g = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                state.value = state.value.copy(sats = (0 until status.satelliteCount).count { status.usedInFix(it) })
            }
            override fun onStopped() { state.value = state.value.copy(sats = -1) }
        }
        listener = l; gnss = g
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l)
            // The phone's fused position (GPS helped by the network) gets a fix sooner, under trees and between buildings.
            if (Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER))
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 1000L, 0f, l)
            lm.registerGnssStatusCallback(g, Handler(Looper.getMainLooper()))
        }
    }

    private fun onFix(l: Location) {
        val prev = last
        last = l
        // Some fixes carry no speed: work it out from how far we came since the last one.
        val speed = when {
            l.hasSpeed() -> l.speed
            prev != null && l.time - prev.time in 300..6000 -> prev.distanceTo(l) / ((l.time - prev.time) / 1000f)
            else -> -1f
        }
        val here = l.latitude to l.longitude
        state.value = state.value.copy(speedMs = speed, accuracy = if (l.hasAccuracy()) l.accuracy else -1f, here = here)
        val s = prefs.loadSettings()
        lookUp(here, s)
        judge(s)
    }

    /** The map: the limit here (asked again after ~150 m) and the cameras around (asked again after 2 km). */
    private fun lookUp(h: Pair<Double, Double>, s: LauncherSettings) {
        if (s.carOsmLimit) {
            val a = asked
            if (a == null || Math.abs(a.first - h.first) >= 0.0013 || Math.abs(a.second - h.second) >= 0.0015) {
                asked = h
                scope.launch {
                    val v = withContext(Dispatchers.IO) { SpeedLimits.at(h.first, h.second) }
                    if (v != null) state.value = state.value.copy(osmLimit = v)
                }
            }
        } else if (state.value.osmLimit != null) state.value = state.value.copy(osmLimit = null)
        if (s.carCameras) {
            val c = camCentre
            if (c == null || SpeedCameras.metres(c.first, c.second, h.first, h.second) > 2000f) {
                camCentre = h
                scope.launch { cameras = withContext(Dispatchers.IO) { SpeedCameras.around(h.first, h.second) }; nearest(s) }
            }
            nearest(s)
        } else if (cameras.isNotEmpty() || state.value.nearest != null) { cameras = emptyList(); camCentre = null; state.value = state.value.copy(nearest = null) }
    }

    private fun nearest(s: LauncherSettings) {
        val h = state.value.here ?: return
        val n = cameras.map { it to SpeedCameras.metres(h.first, h.second, it.lat, it.lon) }.minByOrNull { it.second }?.takeIf { it.second < 1000f }
        state.value = state.value.copy(nearest = n)
        val (cam, d) = n ?: run { lastCamDist = Float.MAX_VALUE; return }
        val closing = d < lastCamDist
        lastCamDist = d
        if (d < 600f && closing && warnedCams.add(cam.key)) {
            scope.launch { carBeep(loudness = s.carTone); delay(450); carBeep(loudness = s.carTone) }
            if (s.carSpeak) voice.speak("Speed camera ahead, ${(d / 50).toInt() * 50} metres" +
                (if (cam.limitKmh > 0) ", limit ${shown(cam.limitKmh, s)}" else ""))
        }
    }

    /** The limit against the speed: a soft tone nearing it, a sharp one over it; spoken too, with spoken alerts on. */
    private fun judge(s: LauncherSettings) {
        val st = state.value
        val limitKmh = st.osmLimit ?: s.carLimit
        val limit = if (limitKmh <= 0) 0 else shown(limitKmh, s)
        val shown = if (st.speedMs < 0) -1 else shown((st.speedMs * 3.6f).toInt(), s)
        if (limit > 0 && (limit != saidLimit || !saidWithVoice) ) {
            saidLimit = limit
            saidWithVoice = s.carSpeak
            if (s.carSpeak) voice.speak("Speed limit $limit" + if (s.carMph) " miles" else "")
        }
        if (limit <= 0 || shown < 0) { wasOver = false; return }
        val near = shown >= limit - 3 && shown <= limit
        val over = shown > limit
        val now = System.currentTimeMillis()
        if (shown < limit - 5) warnedNear = false
        if (near && !warnedNear) { warnedNear = true; carBeep(soft = true, loudness = s.carTone) }
        if (over) {
            if (now - lastTone > 10_000) { lastTone = now; carBeep(loudness = s.carTone) }
            if (s.carSpeak && (!wasOver || now - lastOverSaid > 30_000)) { lastOverSaid = now; voice.speak("Over the limit") }
        }
        wasOver = over
    }

    private fun shown(kmh: Int, s: LauncherSettings): Int = if (s.carMph) (kmh / 1.609f).toInt() else kmh

    private fun note(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Car mode", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 8,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(CarTileService.EXTRA_CAR, true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 9, Intent(this, CarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stillpoint)
            .setContentTitle("Car mode is on")
            .setContentText("Speed, limit and cameras keep running. Tap to open.")
            .setContentIntent(open)
            .addAction(0, "End drive", stop)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL = "car"
        private const val NOTE_ID = 41
        const val ACTION_STOP = "com.cloudit24.stillpoint.CAR_STOP"

        /** The drive right now, and whether a drive is on. Written only by the service. */
        val state = MutableStateFlow(CarState())
        val running = MutableStateFlow(false)

        /** Starts the drive. Needs the location permission already granted (Android 14 refuses otherwise). */
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CarService::class.java)) }
        }

        fun stop(context: Context) { runCatching { context.stopService(Intent(context, CarService::class.java)) } }
    }
}
