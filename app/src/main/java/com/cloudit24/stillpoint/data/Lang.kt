package com.cloudit24.stillpoint.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's own language, chosen in Settings. Empty means the phone's language. */
object Lang {
    val CHOICES = listOf("" to "", "en" to "English", "hi" to "हिन्दी", "ta" to "தமிழ்", "ar" to "العربية")

    private fun prefs(c: Context) = c.getSharedPreferences("stillpoint", Context.MODE_PRIVATE)

    fun saved(c: Context): String = prefs(c).getString("language", "").orEmpty()

    fun save(c: Context, code: String) {
        prefs(c).edit().putString("language", code).commit()
    }

    /** The context in the chosen language (Arabic also turns the layout right to left). */
    fun wrap(base: Context): Context {
        val code = saved(base)
        if (code.isEmpty()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
