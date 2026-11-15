package com.kepord.ime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KepordPalette {
    val PureBlack = Color(0xFF000000)
    val SurfaceCard = Color(0xFF0B0B0F)
    val SurfaceElevated = Color(0xFF14141A)
    val BorderHairline = Color(0xFF262630)
    val BorderStrong = Color(0xFF454555)
    val PureWhite = Color(0xFFFFFFFF)
    val SoftWhite = Color(0xFFEAEAF0)
    val MutedGray = Color(0xFF9898A6)
}

private val MonochromeScheme = darkColorScheme(
    primary = KepordPalette.PureWhite,
    onPrimary = KepordPalette.PureBlack,
    primaryContainer = KepordPalette.SurfaceElevated,
    onPrimaryContainer = KepordPalette.PureWhite,
    secondary = KepordPalette.SoftWhite,
    onSecondary = KepordPalette.PureBlack,
    background = KepordPalette.PureBlack,
    onBackground = KepordPalette.PureWhite,
    surface = KepordPalette.SurfaceCard,
    onSurface = KepordPalette.PureWhite,
    surfaceVariant = KepordPalette.SurfaceElevated,
    onSurfaceVariant = KepordPalette.MutedGray,
    outline = KepordPalette.BorderHairline,
    outlineVariant = KepordPalette.BorderStrong
)

@Composable
fun KepordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonochromeScheme,
        content = content
    )
}
