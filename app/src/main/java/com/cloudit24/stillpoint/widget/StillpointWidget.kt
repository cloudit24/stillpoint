package com.cloudit24.stillpoint.widget

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
import java.util.Date

/**
 * "Stillpoint Widget": clock, date, Hijri and Tamil dates, next prayer.
 * The clock ticks by itself (TextClock); [Refresh] redraws the text at each prayer time and at midnight.
 */
class StillpointWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
        Refresh.schedule(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StillpointWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = Prefs(context).loadSettings()
            val today = LocalDate.now()
            val views = RemoteViews(context.packageName, R.layout.widget_stillpoint)
            views.setTextViewText(R.id.widget_hijri, Calendars.hijri(today, s.hijriAdjust))
            views.setTextViewText(R.id.widget_tamil, Calendars.tamil(today))

            val city = s.city
            val next = city?.let { PrayerTimes.next(System.currentTimeMillis(), it.lat, it.lon, s.prayerMethod, s.asrHanafi) }
            if (next == null) {
                views.setViewVisibility(R.id.widget_prayer, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_prayer, View.VISIBLE)
                views.setTextViewText(R.id.widget_prayer,
                    "Next  ${next.first.label}  ${DateFormat.getTimeFormat(context).format(Date(next.second))}")
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            manager.updateAppWidget(ids, views)
        }

        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
