package com.cloudit24.stillpoint.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Lang
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.widget.PrayerAlerts
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date

/** The full-screen prayer alert, shown over the lock screen at the adhan and the iqama. */
class PrayerPopupActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(Lang.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show()
    }

    private fun show() {
        val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(PrayerAlerts.EXTRA_PRAYER)!!) }.getOrNull()
            ?: return finish()
        val iqama = intent.getStringExtra(PrayerAlerts.EXTRA_KIND) == PrayerAlerts.Kind.IQAMA.name
        val prayerAt = intent.getLongExtra(PrayerAlerts.EXTRA_PRAYER_AT, System.currentTimeMillis())
        val s = Prefs(this).loadSettings()
        val close = {
            getSystemService(NotificationManager::class.java)?.cancel(PrayerAlerts.ID)
            finish()
        }
        setContent {
            StillpointTheme(accent = Color(s.accent), accentStyle = s.accentStyle, font = s.font) {
                PrayerPopup(
                    name = if (prayer == Prayer.DHUHR && s.jumuahAt != null && LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY) "Jumu'ah" else prayer.label,
                    arabic = prayer.arabic,
                    iqama = iqama,
                    iqamaAt = PrayerAlerts.iqamaAt(s, prayer, prayerAt),
                    canSnooze = { PrayerAlerts.canSnooze(s, prayer, prayerAt) },
                    onSnooze = { PrayerAlerts.snooze(this, prayer, prayerAt); finish() },
                    onClose = close,
                )
            }
        }
    }
}

@Composable
private fun PrayerPopup(
    name: String, arabic: String, iqama: Boolean, iqamaAt: Long,
    canSnooze: () -> Boolean, onSnooze: () -> Unit, onClose: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clock = DateFormat.getTimeFormat(ctx).format(Date(now))
    val left = (iqamaAt - now).coerceAtLeast(0L) / 1000
    val green = Color(0xFF1D9E75)

    Column(
        Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(clock, color = Color(0xFFAAAAAA), fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        Text(if (iqama) "${stringResource(R.string.s_iqama)} · $name" else name, color = Color.White, fontSize = 40.sp, textAlign = TextAlign.Center)
        Text(arabic, color = Color(0xFFAAAAAA), fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1f))
        if (iqama) {
            Text(stringResource(R.string.s_the_prayer_is_starting), color = Color.White, fontSize = 20.sp)
        } else if (left > 0) {
            Box(Modifier.size(170.dp).border(3.dp, green, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%d:%02d".format(left / 60, left % 60), color = Color.White, fontSize = 38.sp)
                    Text(stringResource(R.string.s_until_iqama), color = Color(0xFFAAAAAA), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val snooze = !iqama && canSnooze()
        if (!iqama && !snooze && left > 0) Text(stringResource(R.string.s_iqama_is_near), color = Color(0xFFAAAAAA),
            fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (snooze) PopupButton(stringResource(R.string.s_remind_in_5_min), filled = false, Modifier.weight(1f), onSnooze)
            PopupButton(stringResource(R.string.s_ok), filled = true, Modifier.weight(1f), onClose)
        }
    }
}

@Composable
private fun PopupButton(text: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.height(56.dp).clip(shape)
            .then(if (filled) Modifier.background(Color(0xFF1D9E75)) else Modifier.border(1.dp, Color(0xFF555555), shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp) }
}
