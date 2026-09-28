package com.cloudit24.stillpoint.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.LiveRepository
import com.cloudit24.stillpoint.data.Prefs
import com.cloudit24.stillpoint.data.priceFor
import java.util.Date
import java.util.Locale

/**
 * "Stillpoint Gold" widget (Extras). Shows the last fetched price; fetches again when older than 15 minutes
 * (checked every 30 minutes by Android) or when tapped. Placing the widget is the opt-in.
 */
class GoldWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
        fetch(context, force = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) fetch(context, force = true)
    }

    private fun fetch(context: Context, force: Boolean) {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()
        val cached = prefs.loadGold()
        val fresh = cached != null && cached.currency == s.goldCurrency && cached.source == s.goldSource &&
            System.currentTimeMillis() - cached.fetchedAt < 15 * 60_000L
        if (fresh && !force) return
        val pending = goAsync()
        Thread {
            try {
                LiveRepository().gold(s.goldSource, s.goldCurrency)?.let { prefs.saveGold(it) }
                updateAll(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val ACTION_REFRESH = "com.cloudit24.stillpoint.GOLD_REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GoldWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = Prefs(context)
            val s = prefs.loadSettings()
            val g = prefs.loadGold()?.takeIf { it.currency == s.goldCurrency && it.source == s.goldSource }
            val price = g?.priceFor(s.goldKarat, s.goldPerGram)
            val views = RemoteViews(context.packageName, R.layout.widget_gold)
            if (g == null || price == null) {
                views.setTextViewText(R.id.gold_label, "GOLD ${s.goldKarat}K")
                views.setTextViewText(R.id.gold_price, "—")
                views.setTextViewText(R.id.gold_meta, "Tap to load")
            } else {
                views.setTextViewText(R.id.gold_label, "GOLD ${s.goldKarat}K · ${if (price.dubai) "DUBAI" else "SPOT"}")
                views.setTextViewText(R.id.gold_price, String.format(Locale.US, "%,.2f", price.value))
                views.setTextViewText(R.id.gold_meta,
                    "${g.currency} per ${if (s.goldPerGram) "gram" else "ounce"} · " +
                        DateFormat.getTimeFormat(context).format(Date(g.fetchedAt)))
            }
            val refresh = PendingIntent.getBroadcast(
                context, 2, Intent(context, GoldWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.gold_root, refresh)
            manager.updateAppWidget(ids, views)
        }
    }
}
