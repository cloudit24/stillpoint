package com.cloudit24.stillpoint.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// True black for OLED power draw. Warm grey text, one cool slate accent for state only.
val Ink = Color(0xFFE8E6E1)
val Muted = Color(0xFF7D7A74)
val Slate = Color(0xFF9FB4C7)

@Composable
fun StillpointTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF161615),
            surfaceContainer = Color(0xFF161615),
            primary = Slate,
            onPrimary = Color.Black,
            onBackground = Ink,
            onSurface = Ink,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Ink) { content() }
    }
}
