package com.cloudit24.stillpoint.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.Moon
import com.cloudit24.stillpoint.data.Prayer
import com.cloudit24.stillpoint.data.PrayerTimes
import com.cloudit24.stillpoint.data.Prefs
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt

/**
 * Stillpoint Prayer: the current prayer, the next one with a live countdown, a bar for the time passed,
 * all five times, the moon phase and the Hijri date. Redrawn at each prayer time and at midnight;
 * Android keeps the countdown ticking.
 */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        Refresh.schedule(context)
    }

    companion object {
        private const val INK = 0xFFE8E6E1.toInt()
        private const val MUTED = 0xFF9A978F.toInt()
        private const val DIM = 0x809A978F.toInt()
        private val FIVE = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        private val CELLS = listOf(
            Triple(R.id.p0_cell, R.id.p0_name, R.id.p0_time), Triple(R.id.p1_cell, R.id.p1_name, R.id.p1_time),
            Triple(R.id.p2_cell, R.id.p2_name, R.id.p2_time), Triple(R.id.p3_cell, R.id.p3_name, R.id.p3_time),
            Triple(R.id.p4_cell, R.id.p4_name, R.id.p4_time),
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PrayerWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_prayer)
            views.setOnClickPendingIntent(R.id.prayer_root, StillpointWidget.openApp(context))

            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val s = Prefs(context).loadSettings()
            val accent = s.accent.toInt()
            views.setInt(R.id.prayer_glow, "setColorFilter", accent)
            views.setTextColor(R.id.prayer_now, accent)
            views.setTextColor(R.id.prayer_countdown, accent)
            if (Build.VERSION.SDK_INT >= 31) {
                views.setColorStateList(R.id.prayer_progress, "setProgressTintList", ColorStateList.valueOf(accent))
            }

            val fmt = DateFormat.getTimeFormat(context)
            val age = Moon.age(now)
            val hijri = Calendars.hijri(today, s.hijriAdjust).replace(Regex(" \\d+ AH$"), "")
            views.setImageViewBitmap(R.id.prayer_moon_img, moonBitmap(age, 150))
            views.setTextViewText(R.id.prayer_moon,
                "${Moon.name(age)} · ${(Moon.illumination(age) * 100).roundToInt()}% lit · $hijri")

            val city = s.city
            val span = if (s.prayerOn && city != null) PrayerTimes.span(now, city.lat, city.lon, s.prayerMethod, s.asrHanafi) else null
            if (city == null || span == null) {
                views.setTextViewText(R.id.prayer_now, "Prayer times are off")
                views.setTextViewText(R.id.prayer_since, "Stillpoint settings, Extras, Islamic prayer")
                views.setTextViewText(R.id.prayer_next, "")
                views.setViewVisibility(R.id.prayer_countdown, View.GONE)
                views.setViewVisibility(R.id.prayer_progress, View.GONE)
                views.setViewVisibility(R.id.prayer_times, View.GONE)
            } else {
                views.setTextViewText(R.id.prayer_now, "${span.current.label}  ${span.current.arabic}")
                views.setTextViewText(R.id.prayer_since,
                    if (span.current == Prayer.FAJR) "until sunrise ${fmt.format(Date(span.endsAt))}"
                    else "since ${fmt.format(Date(span.currentAt))}")
                views.setTextViewText(R.id.prayer_next, "NEXT  ${span.next.label} ${fmt.format(Date(span.nextAt))}")
                views.setViewVisibility(R.id.prayer_countdown, View.VISIBLE)
                views.setChronometer(R.id.prayer_countdown, SystemClock.elapsedRealtime() + (span.nextAt - now), "in %s", true)
                views.setChronometerCountDown(R.id.prayer_countdown, true)
                val passed = (now - span.currentAt).toFloat() / (span.nextAt - span.currentAt).coerceAtLeast(1)
                views.setViewVisibility(R.id.prayer_progress, View.VISIBLE)
                views.setProgressBar(R.id.prayer_progress, 1000, (passed.coerceIn(0f, 1f) * 1000).toInt(), false)
                views.setViewVisibility(R.id.prayer_times, View.VISIBLE)
                val times = PrayerTimes.forDate(today, city.lat, city.lon, s.prayerMethod, s.asrHanafi)
                FIVE.forEachIndexed { i, p ->
                    val (cellId, nameId, timeId) = CELLS[i]
                    val at = times[p]
                    val isNow = p == span.current
                    val past = at != null && at < now && !isNow
                    views.setTextViewText(nameId, p.label)
                    views.setTextViewText(timeId, at?.let { fmt.format(Date(it)) } ?: "—")
                    views.setInt(cellId, "setBackgroundResource", if (isNow) R.drawable.widget_pill else 0)
                    views.setTextColor(nameId, if (isNow) accent else if (past) DIM else MUTED)
                    views.setTextColor(timeId, when {
                        isNow -> INK
                        p == span.next -> INK
                        past -> DIM
                        else -> MUTED
                    })
                }
            }
            manager.updateAppWidget(ids, views)
        }

        /** The moon as it looks tonight, with a faint halo that grows towards full moon. */
        private fun moonBitmap(age: Double, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val c = px / 2f
            val r = c * 0.74f
            val lit = Moon.illumination(age).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val haloAlpha = (40 + 70 * lit).toInt()
            paint.shader = RadialGradient(c, c, c, (haloAlpha shl 24) or 0xE8E6E1, 0x00E8E6E1, Shader.TileMode.CLAMP)
            canvas.drawCircle(c, c, c, paint)
            paint.shader = null
            paint.color = 0xFF262624.toInt()
            canvas.drawCircle(c, c, r, paint)
            paint.color = INK
            canvas.drawPath(Moon.litPath(age, c, c, r), paint)
            return bmp
        }
    }
}
