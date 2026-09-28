package com.cloudit24.stillpoint.ui

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

@Composable
fun StillpointTheme(accent: Color = Slate, content: @Composable () -> Unit) {
    MaterialTheme(
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
