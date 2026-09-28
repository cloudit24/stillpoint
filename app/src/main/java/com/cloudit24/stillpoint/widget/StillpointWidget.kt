package com.cloudit24.stillpoint.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.cloudit24.stillpoint.MainActivity
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.Calendars
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.PrayerTimes
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * "Stillpoint Widget": clock, date, Hijri and Tamil dates, next prayer.
 * The clock ticks by itself (TextClock). The text is refreshed at the next prayer time or midnight,
 * whichever comes first, when Stillpoint opens, and every 30 minutes as a fallback. Works on any launcher.
 */
class StillpointWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, manager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    companion object {
        /** Refreshes every placed Stillpoint widget; cheap when none are placed. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StillpointWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = Prefs(context).loadSettings()
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val views = RemoteViews(context.packageName, R.layout.widget_stillpoint)

            views.setTextViewText(R.id.widget_hijri, Calendars.hijri(today, s.hijriAdjust))
            views.setTextViewText(R.id.widget_tamil, Calendars.tamil(today))

            val city = s.city
            val next = city?.let { PrayerTimes.next(now, it.lat, it.lon, s.prayerMethod, s.asrHanafi) }
            if (next == null) {
                views.setViewVisibility(R.id.widget_prayer, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_prayer, View.VISIBLE)
                views.setTextViewText(R.id.widget_prayer,
                    "Next  ${next.first.label}  ${DateFormat.getTimeFormat(context).format(Date(next.second))}")
            }

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)

            // Wake once for the next change: the next prayer, or midnight for the dates.
            val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val at = minOf(next?.second ?: midnight, midnight) + 30_000L
            runCatching {
                context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at, refreshIntent(context))
            }
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, StillpointWidget::class.java))
            val intent = Intent(context, StillpointWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            return PendingIntent.getBroadcast(context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
