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

app/src/main/java/com/kepord/ime/ui/components/LiveKeyPreview.kt

package com.kepord.ime.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kepord.ime.ime.KepordKeyboardView
import com.kepord.ime.model.KepordConfig
import com.kepord.ime.ui.theme.KepordPalette

@Composable
fun LiveKeyboardEnginePreview(
    config: KepordConfig,
    arabicPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val previewCfg = config.copy(showNumberRow = false, keyboardHeightScale = 0.85f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(195.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, KepordPalette.BorderStrong, RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                KepordKeyboardView(ctx).apply {
                    applyConfig(previewCfg, forceDefaultLanguage = false)
                    setLanguageArabic(arabicPreview)
                }
            },
            update = { view ->
                view.applyConfig(previewCfg, forceDefaultLanguage = false)
                view.setLanguageArabic(arabicPreview)
            }
        )
    }
}
