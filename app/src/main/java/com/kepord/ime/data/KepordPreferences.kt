package com.kepord.ime.data

import android.content.Context
import android.content.SharedPreferences
import com.kepord.ime.ime.ArabicFontStyle
import com.kepord.ime.ime.MechanicalSoundProfile
import com.kepord.ime.model.Background3DStyle
import com.kepord.ime.model.KepordConfig
import com.kepord.ime.model.LetterColorMode
import com.kepord.ime.model.WavePattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KepordPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<KepordConfig> = _config.asStateFlow()

    fun current(): KepordConfig = _config.value

    fun update(transform: (KepordConfig) -> KepordConfig) {
        val next = sanitize(transform(_config.value))
        writeConfig(next)
        _config.value = next
    }

    fun resetDefaults() {
        val defaults = KepordConfig()
        writeConfig(defaults)
        _config.value = defaults
    }

    fun getRecentEmojis(): List<String> {
        val raw = prefs.getString("recent_emojis", "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split('\u001F').filter { it.isNotEmpty() }
    }

    fun recordRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val current = getRecentEmojis().toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        val trimmed = current.take(32)
        prefs.edit().putString("recent_emojis", trimmed.joinToString("\u001F")).apply()
    }

    private fun sanitize(c: KepordConfig): KepordConfig = c.copy(
        waveSpeed = c.waveSpeed.coerceIn(0.1f, 4.0f),
        letterGlow = c.letterGlow.coerceIn(0.0f, 1.0f),
        bg3DIntensity = c.bg3DIntensity.coerceIn(0.15f, 1.0f),
        keycapOpacity = c.keycapOpacity.coerceIn(0.70f, 1.0f),
        letter3DDepth = c.letter3DDepth.coerceIn(0, 6),
        keyBevelDepth = c.keyBevelDepth.coerceIn(0, 5),
        keyCornerRadiusDp = c.keyCornerRadiusDp.coerceIn(2f, 16f),
        keyboardHeightScale = c.keyboardHeightScale.coerceIn(0.80f, 1.25f),
        letterSizeScale = c.letterSizeScale.coerceIn(0.80f, 1.35f),
        hapticStrengthMs = c.hapticStrengthMs.coerceIn(0, 45),
        keySoundVolume = c.keySoundVolume.coerceIn(0.1f, 1.0f),
        touchpadSensitivity = c.touchpadSensitivity.coerceIn(0.8f, 3.5f),
        clipboardMaxUnpinned = c.clipboardMaxUnpinned.coerceIn(20, 200)
    )

    private fun readConfig(): KepordConfig {
        val colorMode = prefs.getString("letter_color_mode", null)
            ?.let { runCatching { LetterColorMode.valueOf(it) }.getOrNull() }
            ?: LetterColorMode.RAINBOW_WAVE

        val wavePattern = prefs.getString("wave_pattern", null)
            ?.let { runCatching { WavePattern.valueOf(it) }.getOrNull() }
            ?: WavePattern.DIAGONAL

        val bgStyle = prefs.getString("bg_3d_style", null)
            ?.let { runCatching { Background3DStyle.valueOf(it) }.getOrNull() }
            ?: Background3DStyle.GRID_3D

        val arabicFont = prefs.getString("arabic_font_style", null)
            ?.let { runCatching { ArabicFontStyle.valueOf(it) }.getOrNull() }
            ?: ArabicFontStyle.NORMAL

        val mechProfile = prefs.getString("mech_sound_profile", null)
            ?.let { runCatching { MechanicalSoundProfile.valueOf(it) }.getOrNull() }
            ?: MechanicalSoundProfile.CLICKY_BLUE

        return sanitize(
            KepordConfig(
                letterColorMode = colorMode,
                staticLetterColor = prefs.getInt("static_letter_color", 0xFFFFFFFF.toInt()),
                gradientColor1 = prefs.getInt("grad_c1", 0xFF00F0FF.toInt()),
                gradientColor2 = prefs.getInt("grad_c2", 0xFFFFFFFF.toInt()),
                gradientColor3 = prefs.getInt("grad_c3", 0xFFFF007F.toInt()),
                wavePattern = wavePattern,
                waveSpeed = prefs.getFloat("wave_speed", 1.2f),
                letterGlow = prefs.getFloat("letter_glow", 0.65f),
                reactiveTouchRipple = prefs.getBoolean("reactive_ripple", true),
                background3DStyle = bgStyle,
                bg3DIntensity = prefs.getFloat("bg_3d_intensity", 0.72f),
                keycapOpacity = prefs.getFloat("keycap_opacity", 0.92f),
                letter3DDepth = prefs.getInt("letter_3d_depth", 4),
                keyBevelDepth = prefs.getInt("key_bevel_depth", 3),
                keyCornerRadiusDp = prefs.getFloat("key_corner_radius", 9f),
                showKeyBorders = prefs.getBoolean("show_key_borders", true),
                showSecondaryHints = prefs.getBoolean("show_secondary_hints", true),
                arabicFontStyle = arabicFont,
                keyboardHeightScale = prefs.getFloat("kb_height_scale", 1.0f),
                letterSizeScale = prefs.getFloat("letter_size_scale", 1.0f),
                showNumberRow = prefs.getBoolean("show_number_row", true),
                defaultLanguageArabic = prefs.getBoolean("default_lang_ar", true),
                keyPopupEnabled = prefs.getBoolean("key_popup_enabled", true),
                spacebarCursorSwipe = prefs.getBoolean("space_cursor_swipe", true),
                hapticStrengthMs = prefs.getInt("haptic_ms", 18),
                keySoundEnabled = prefs.getBoolean("sound_enabled", true),
                keySoundVolume = prefs.getFloat("sound_volume", 0.75f),
                mechanicalSoundProfile = mechProfile,
                touchpadSensitivity = prefs.getFloat("touchpad_sensitivity", 1.65f),
                autoCapsEnglish = prefs.getBoolean("auto_caps_en", true),
                doubleSpacePeriod = prefs.getBoolean("double_space_period", true),
                clipboardMaxUnpinned = prefs.getInt("clip_max_unpinned", 20),
                clipboardMonitorEnabled = prefs.getBoolean("clip_monitor", true),
                clipboardSkipSensitive = prefs.getBoolean("clip_skip_sensitive", true)
            )
        )
    }

    private fun writeConfig(c: KepordConfig) {
        prefs.edit()
            .putString("letter_color_mode", c.letterColorMode.name)
            .putInt("static_letter_color", c.staticLetterColor)
            .putInt("grad_c1", c.gradientColor1)
            .putInt("grad_c2", c.gradientColor2)
            .putInt("grad_c3", c.gradientColor3)
            .putString("wave_pattern", c.wavePattern.name)
            .putFloat("wave_speed", c.waveSpeed)
            .putFloat("letter_glow", c.letterGlow)
            .putBoolean("reactive_ripple", c.reactiveTouchRipple)
            .putString("bg_3d_style", c.background3DStyle.name)
            .putFloat("bg_3d_intensity", c.bg3DIntensity)
            .putFloat("keycap_opacity", c.keycapOpacity)
            .putInt("letter_3d_depth", c.letter3DDepth)
            .putInt("key_bevel_depth", c.keyBevelDepth)
            .putFloat("key_corner_radius", c.keyCornerRadiusDp)
            .putBoolean("show_key_borders", c.showKeyBorders)
            .putBoolean("show_secondary_hints", c.showSecondaryHints)
            .putString("arabic_font_style", c.arabicFontStyle.name)
            .putFloat("kb_height_scale", c.keyboardHeightScale)
            .putFloat("letter_size_scale", c.letterSizeScale)
            .putBoolean("show_number_row", c.showNumberRow)
            .putBoolean("default_lang_ar", c.defaultLanguageArabic)
            .putBoolean("key_popup_enabled", c.keyPopupEnabled)
            .putBoolean("space_cursor_swipe", c.spacebarCursorSwipe)
            .putInt("haptic_ms", c.hapticStrengthMs)
            .putBoolean("sound_enabled", c.keySoundEnabled)
            .putFloat("sound_volume", c.keySoundVolume)
            .putString("mech_sound_profile", c.mechanicalSoundProfile.name)
            .putFloat("touchpad_sensitivity", c.touchpadSensitivity)
            .putBoolean("auto_caps_en", c.autoCapsEnglish)
            .putBoolean("double_space_period", c.doubleSpacePeriod)
            .putInt("clip_max_unpinned", c.clipboardMaxUnpinned)
            .putBoolean("clip_monitor", c.clipboardMonitorEnabled)
            .putBoolean("clip_skip_sensitive", c.clipboardSkipSensitive)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "kepord_prefs_v1"
    }
}
