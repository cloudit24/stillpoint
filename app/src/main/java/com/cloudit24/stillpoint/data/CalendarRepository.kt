package com.cloudit24.stillpoint.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the on-device calendar provider only. Works with any sync adapter
 * (DAVx5, Etar, Google), so no Google account is required.
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun today(limit: Int = 5): List<AgendaItem> {
        if (!hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, endOfDay)
        }.build()
        val projection = arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY)

        val out = ArrayList<AgendaItem>()
        runCatching {
            context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")
                ?.use { c ->
                    while (c.moveToNext() && out.size < limit) {
                        out += AgendaItem(
                            title = c.getString(0) ?: "(no title)",
                            begin = c.getLong(1),
                            end = c.getLong(2),
                            allDay = c.getInt(3) == 1,
                        )
                    }
                }
        }
        return out
    }
}
