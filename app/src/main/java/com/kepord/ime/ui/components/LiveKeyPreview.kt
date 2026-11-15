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
