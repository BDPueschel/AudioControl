package com.audiocontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(accentHue: Float = 189f, content: @Composable () -> Unit) {
    val accent = accentFor(accentHue)
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color(Ink.bg),
                surface = Color(Ink.panel),
                surfaceVariant = Color(Ink.panel2),
                primary = accent,
                error = Color(Ink.err),
                onBackground = Color(Ink.txt),
                onSurface = Color(Ink.txt),
            ),
            content = content,
        )
    }
}
