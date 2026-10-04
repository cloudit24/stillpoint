package com.cloudit24.stillpoint.ui

import com.cloudit24.stillpoint.data.AccentStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.ExperimentalTextApi
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

// Neon Alley: warm orange neon and teal on a dark brown-black street.
val NeonOrange = Color(0xFFFF9A3C)
val NeonTeal = Color(0xFF4FD1C5)
val NeonClock = Color(0xFFFFB36B)
val NeonBack = Color(0xFF12100E)
val SpecialPink = Color(0xFFFF6FAE)
val LocalNeon = staticCompositionLocalOf { false }
val LocalAccentStyle = staticCompositionLocalOf { AccentStyle.SOLID }

/** The accent as a fill for tiles and squares, in the chosen style. */
val AccentFill: Brush
    @Composable @ReadOnlyComposable get() = accentBrush(LocalAccent.current, LocalAccentStyle.current)

private fun shiftHue(c: Color, deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A soft light from the top-left corner, fading to a deeper shade. */
private class GlowBrush(private val colors: List<Color>) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        RadialGradientShader(Offset(size.width * 0.25f, size.height * 0.2f), size.maxDimension * 0.95f, colors)
}

fun accentBrush(c: Color, style: AccentStyle): Brush = when (style) {
    AccentStyle.SOLID -> SolidColor(c)
    AccentStyle.SOFT -> Brush.linearGradient(listOf(lerp(c, Color.White, 0.22f), lerp(c, Color.Black, 0.32f)))
    AccentStyle.DUO -> Brush.linearGradient(listOf(c, shiftHue(c, 38f)))
    AccentStyle.GLOW -> GlowBrush(listOf(lerp(c, Color.White, 0.3f), c, lerp(c, Color.Black, 0.45f)))
}

/** The user's accent colour (Settings > Appearance), Windows Phone style. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalAccent.current

/** Bundled variable fonts: one file each, every weight drawn from it. */
@OptIn(ExperimentalTextApi::class)
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
fun StillpointTheme(accent: Color = Slate, accentStyle: AccentStyle = AccentStyle.SOLID, font: AppFont = AppFont.SYSTEM,
                    neon: Boolean = false, content: @Composable () -> Unit) {
    val back = if (neon) NeonBack else Color.Black
    val tint = if (neon) NeonOrange else accent
    val typography = remember(font) { Typography().withFont(fontFamilyFor(font)) }
    MaterialTheme(
        typography = typography,
        colorScheme = darkColorScheme(
            background = back,
            surface = if (neon) Color(0xFF1D1915) else Color(0xFF161615),
            surfaceContainer = if (neon) Color(0xFF1D1915) else Color(0xFF161615),
            surfaceContainerHigh = if (neon) Color(0xFF26211B) else Color(0xFF1E1E1C),
            primary = tint,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
        ),
    ) {
        CompositionLocalProvider(LocalAccent provides tint, LocalAccentStyle provides accentStyle, LocalNeon provides neon) {
            Surface(Modifier.fillMaxSize(), color = back, contentColor = Ink) { content() }
        }
    }
}
