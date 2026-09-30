package com.cloudit24.stillpoint.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.cloudit24.stillpoint.R
import com.cloudit24.stillpoint.data.AppFont
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// True black for OLED power draw. Warm grey text, one accent colour chosen by the user.
val Ink = Color(0xFFE8E6E1)
val Muted = Color(0xFF7D7A74)
val Slate = Color(0xFF9FB4C7)

val LocalAccent = staticCompositionLocalOf { Slate }

/** The user's accent colour (Settings > Appearance), Windows Phone style. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalAccent.current

/** Bundled variable fonts: one file each, every weight drawn from it. */
private fun variable(res: Int, lo: Int, hi: Int) = FontFamily((1..9).map { i ->
    val w = i * 100
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w.coerceIn(lo, hi))))
})

fun fontFamilyFor(f: AppFont): FontFamily = when (f) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.INTER -> variable(R.font.inter, 100, 900)
    AppFont.MANROPE -> variable(R.font.manrope, 200, 800)
    AppFont.SPACE -> variable(R.font.space_grotesk, 300, 700)
    AppFont.LORA -> variable(R.font.lora, 400, 700)
}

/**
 * Every text style in the chosen font, with joined letters ("tt", "fi") turned off:
 * some fonts, like Motorola's, join them and words such as "Battery" look glued.
 */
private fun Typography.withFont(ff: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = ff, fontFeatureSettings = "liga 0, clig 0")
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun StillpointTheme(accent: Color = Slate, font: AppFont = AppFont.SYSTEM, content: @Composable () -> Unit) {
    val typography = remember(font) { Typography().withFont(fontFamilyFor(font)) }
    MaterialTheme(
        typography = typography,
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF161615),
            surfaceContainer = Color(0xFF161615),
            surfaceContainerHigh = Color(0xFF1E1E1C),
            primary = accent,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
        ),
    ) {
        CompositionLocalProvider(LocalAccent provides accent) {
            Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Ink) { content() }
        }
    }
}
