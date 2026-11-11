package de.mirranet.lingua.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * iOS-style system colours (light appearance). Names follow Apple's semantic colours so the intent
 * of each use stays readable.
 */
object Ios {
    val Blue = Color(0xFF007AFF)
    val BlueTint = Color(0xFFE5F1FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF248A3D)
    val GreenTint = Color(0xFFE8F8EC)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFD70015)
    val RedTint = Color(0xFFFFECEB)
    val Orange = Color(0xFFFF9500)
    val OrangeTint = Color(0xFFFFF4E5)
    val Yellow = Color(0xFFFFCC00)
    val Purple = Color(0xFFAF52DE)
    val Teal = Color(0xFF30B0C7)

    /** Page background behind grouped lists. */
    val GroupedBackground = Color(0xFFF2F2F7)
    /** Cards/rows on the grouped background. */
    val Card = Color.White
    val Label = Color(0xFF000000)
    val SecondaryLabel = Color(0xFF6C6C70)
    val TertiaryLabel = Color(0xFFAEAEB2)
    val Separator = Color(0xFFD1D1D6)
    /** Neutral fills for text fields, tracks and inactive controls. */
    val Fill = Color(0xFFE9E9EB)
    val FillLight = Color(0xFFF2F2F7)
}

/**
 * Type scale modelled on iOS Dynamic Type, one step larger than the defaults because the app is meant
 * to be comfortable for people who rarely use a phone.
 */
object IosType {
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val Title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val Title2 = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
    val Title3 = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val Headline = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 18.sp, lineHeight = 24.sp)
    val Callout = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val Subhead = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val Footnote = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
    val SectionHeader = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    /** Prompt sentences in lessons. */
    val Prompt = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
}

/**
 * App theme. [largeText] scales all text by 20 % on top of the system font size setting.
 */
@Composable
fun LinguaTheme(largeText: Boolean = false, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Ios.Blue, onPrimary = Color.White, secondary = Ios.Green, onSecondary = Color.White,
        error = Ios.Red, background = Ios.GroupedBackground, surface = Color.White, onSurface = Ios.Label,
        onBackground = Ios.Label, surfaceVariant = Ios.FillLight, outline = Ios.Separator,
        surfaceContainerLow = Color.White, surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    )
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.2f) else density
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = IosType.Body, bodyMedium = IosType.Callout, titleLarge = IosType.Title2),
            content = content,
        )
    }
}
