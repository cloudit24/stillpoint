package com.cloudit24.stillpoint.ui

import com.cloudit24.stillpoint.data.AccentStyle
import com.cloudit24.stillpoint.data.AppTheme
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
// Cyberpunk: hot magenta and electric cyan on a midnight blue-black city, the clock in acid yellow.
val CyberMagenta = Color(0xFFFF2BD6)
val CyberCyan = Color(0xFF00E5FF)
val CyberClock = Color(0xFFF2EE5A)
val CyberBack = Color(0xFF0A0A14)
val SpecialPink = Color(0xFFFF6FAE)

/**
 * The colours a whole look is drawn in. [neon] is true for the themed looks (Neon Alley, Cyberpunk): their own
 * [line] tint replaces the user's accent, the clock takes [clock], and the terminal window's dots take [line]
 * and [glow].
 */
data class Look(val theme: AppTheme, val back: Color, val line: Color, val glow: Color, val clock: Color,
                val surface: Color, val surfaceHigh: Color) {
    val neon: Boolean get() = theme != AppTheme.STILLPOINT
}

val StillpointLook = Look(AppTheme.STILLPOINT, Color.Black, Slate, Slate, Ink, Color(0xFF161615), Color(0xFF1E1E1C))
val NeonLook = Look(AppTheme.NEON, NeonBack, NeonOrange, NeonTeal, NeonClock, Color(0xFF1D1915), Color(0xFF26211B))
val CyberLook = Look(AppTheme.CYBER, CyberBack, CyberMagenta, CyberCyan, CyberClock, Color(0xFF14132A), Color(0xFF1D1C3A))

fun lookFor(t: AppTheme): Look = when (t) {
    AppTheme.STILLPOINT -> StillpointLook
    AppTheme.NEON -> NeonLook
    AppTheme.CYBER -> CyberLook
}

val LocalLook = staticCompositionLocalOf { StillpointLook }
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
                    theme: AppTheme = AppTheme.STILLPOINT, content: @Composable () -> Unit) {
    val look = lookFor(theme)
    val back = look.back
    val tint = if (look.neon) look.line else accent
    val typography = remember(font) { Typography().withFont(fontFamilyFor(font)) }
    MaterialTheme(
        typography = typography,
        colorScheme = darkColorScheme(
            background = back,
            surface = look.surface,
            surfaceContainer = look.surface,
            surfaceContainerHigh = look.surfaceHigh,
            primary = tint,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
        ),
    ) {
        CompositionLocalProvider(LocalAccent provides tint, LocalAccentStyle provides accentStyle, LocalLook provides look) {
            Surface(Modifier.fillMaxSize(), color = back, contentColor = Ink) { content() }
        }
    }
}
