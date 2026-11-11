package de.mirranet.lingua.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours of the classic look: green header, blue accents, gold for finished skills. */
object Palette {
    val Green = Color(0xFF78C800)
    val GreenDark = Color(0xFF58A700)
    val GreenLight = Color(0xFFD7FFB8)
    val Blue = Color(0xFF1CB0F6)
    val BlueDark = Color(0xFF1899D6)
    val BlueLight = Color(0xFFDDF4FF)
    val Red = Color(0xFFFF4B4B)
    val RedDark = Color(0xFFEA2B2B)
    val RedLight = Color(0xFFFFDFE0)
    val Gold = Color(0xFFFFC800)
    val GoldDark = Color(0xFFE5A800)
    val Orange = Color(0xFFFF9600)
    val Gray = Color(0xFFE5E5E5)
    val GrayDark = Color(0xFFAFAFAF)
    val Text = Color(0xFF3C3C3C)
    val Muted = Color(0xFF777777)
}

@Composable
fun LinguaTheme(content: @Composable () -> Unit) {
    // The classic design is a bright, light look; the app keeps it in dark mode too.
    val scheme = lightColorScheme(
        primary = Palette.Green, onPrimary = Color.White, secondary = Palette.Blue, onSecondary = Color.White,
        error = Palette.Red, background = Color.White, surface = Color.White, onSurface = Palette.Text,
        onBackground = Palette.Text, surfaceVariant = Color(0xFFF7F7F7),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
