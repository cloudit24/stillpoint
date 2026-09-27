package com.cloudit24.stillpoint.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.data.AppEntry
import kotlinx.coroutines.delay
import java.util.Date

/** Emits wall-clock time aligned to [periodMs] boundaries. */
@Composable
fun rememberTicker(periodMs: Long): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs - (value % periodMs))
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin < 1 -> "<1m"
        h == 0L -> "${m}m"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}

fun formatRemaining(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** "today", "yesterday", "5d ago", "3w ago", "2mo ago", "1y ago". */
fun formatAge(sinceMs: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - sinceMs) / 86_400_000L).coerceAtLeast(0)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 14 -> "${days}d ago"
        days < 60 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

/** Respects the system 12/24-hour setting. */
fun formatClock(context: Context, ms: Long): String = DateFormat.getTimeFormat(context).format(Date(ms))

fun Context.safeStart(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    label: String,
    usageMs: Long?,
    fontSize: TextUnit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    /** Replaces the usage figure on the right, e.g. "3d ago". */
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val right = trailing ?: usageMs?.takeIf { it >= 60_000L }?.let { formatDuration(it) }
        if (right != null) {
            Text(right, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Icon slot for [AppRow], or null when icons are switched off in settings. */
fun appIcon(vm: LauncherViewModel, app: AppEntry, size: Dp): (@Composable () -> Unit)? =
    if (vm.settings.showIcons) ({ AppIcon(vm, app, size) }) else null

/** Loads off the main thread; keeps its space while loading so rows don't jump. */
@Composable
fun AppIcon(vm: LauncherViewModel, app: AppEntry, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState(vm.cachedIcon(app), app.key) {
        if (value == null) value = vm.loadIcon(app, px)
    }
    Box(Modifier.size(size)) {
        bmp?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp, bottom = 6.dp))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = if (enabled) Ink else Muted, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun ActionRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(label, fontSize = 16.sp)
        if (detail != null) Text(detail, color = Muted, fontSize = 13.sp)
    }
}
