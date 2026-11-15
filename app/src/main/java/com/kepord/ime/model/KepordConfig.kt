package com.kepord.ime.model

enum class LetterColorMode(val titleAr: String, val descAr: String) {
    STATIC("لون ثابت للحرف", "تخصيص لون واحد ساطع للأحرف فقط"),
    RAINBOW_WAVE("تدرج قوس قزح RGB", "موجة ألوان كاملة مثل كيبوردات الجيمنج"),
    CUSTOM_GRADIENT_WAVE("تدرج ألوان مخصص", "موجة متدرجة بين ألوانك المختارة"),
    MONOCHROME_WAVE("موجة فضي وأبيض فاخر", "تدرج أبيض وأسود/فضي فخم")
}

enum class WavePattern(val titleAr: String) {
    HORIZONTAL("تموج أفقي"),
    VERTICAL("تموج عمودي"),
    DIAGONAL("تموج قطري"),
    RADIAL("تموج دائري من المركز"),
    BREATHING("نبض لوني متزامن"),
    KEY_CASCADE("تتابع حرفاً بحرف")
}

enum class Background3DStyle(val titleAr: String, val descAr: String) {
    GRID_3D("شبكة 3D عميقة", "منظور ثلاثي الأبعاد أبيض وأسود مع عمق أفقي"),
    ISOMETRIC_CUBES("مكعبات 3D متراصة", "أوجه ثلاثية الأبعاد بالأسود والرمادي والأبيض"),
    WAVES_3D("تموجات 3D طبوغرافية", "خطوط ومنحنيات عميقة متباينة"),
    PURE_BLACK("أسود OLED خالص", "خلفية سوداء فاخرة بدون نقش")
}

data class KepordConfig(
    // إضاءة الأحرف نفسها
    val letterColorMode: LetterColorMode = LetterColorMode.RAINBOW_WAVE,
    val staticLetterColor: Int = 0xFFFFFFFF.toInt(),
    val gradientColor1: Int = 0xFF00F0FF.toInt(),
    val gradientColor2: Int = 0xFFFFFFFF.toInt(),
    val gradientColor3: Int = 0xFFFF007F.toInt(),
    val wavePattern: WavePattern = WavePattern.DIAGONAL,
    val waveSpeed: Float = 1.2f,
    val letterGlow: Float = 0.65f,
    val reactiveTouchRipple: Boolean = true,

    // الأبعاد الثلاثية والخلفية والتباين
    val background3DStyle: Background3DStyle = Background3DStyle.GRID_3D,
    val bg3DIntensity: Float = 0.72f,
    val keycapOpacity: Float = 0.92f,
    val letter3DDepth: Int = 4,
    val keyBevelDepth: Int = 3,
    val keyCornerRadiusDp: Float = 9f,
    val showKeyBorders: Boolean = true,
    val showSecondaryHints: Boolean = true,

    // الأبعاد والكتابة والتفاعل
    val keyboardHeightScale: Float = 1.0f,
    val letterSizeScale: Float = 1.0f,
    val showNumberRow: Boolean = true,
    val defaultLanguageArabic: Boolean = true,
    val keyPopupEnabled: Boolean = true,
    val spacebarCursorSwipe: Boolean = true,
    val hapticStrengthMs: Int = 18,
    val keySoundEnabled: Boolean = false,
    val keySoundVolume: Float = 0.55f,
    val autoCapsEnglish: Boolean = true,
    val doubleSpacePeriod: Boolean = true,

    // الحافظة الاحترافية
    val clipboardMaxUnpinned: Int = 20,
    val clipboardMonitorEnabled: Boolean = true,
    val clipboardSkipSensitive: Boolean = true
)

app/src/main/java/com/kepord/ime/data/KepordPreferences.kt

package com.kepord.ime.data

import android.content.Context
import android.content.SharedPreferences
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
                keyboardHeightScale = prefs.getFloat("kb_height_scale", 1.0f),
                letterSizeScale = prefs.getFloat("letter_size_scale", 1.0f),
                showNumberRow = prefs.getBoolean("show_number_row", true),
                defaultLanguageArabic = prefs.getBoolean("default_lang_ar", true),
                keyPopupEnabled = prefs.getBoolean("key_popup_enabled", true),
                spacebarCursorSwipe = prefs.getBoolean("space_cursor_swipe", true),
                hapticStrengthMs = prefs.getInt("haptic_ms", 18),
                keySoundEnabled = prefs.getBoolean("sound_enabled", false),
                keySoundVolume = prefs.getFloat("sound_volume", 0.55f),
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
            .putFloat("kb_height_scale", c.keyboardHeightScale)
            .putFloat("letter_size_scale", c.letterSizeScale)
            .putBoolean("show_number_row", c.showNumberRow)
            .putBoolean("default_lang_ar", c.defaultLanguageArabic)
            .putBoolean("key_popup_enabled", c.keyPopupEnabled)
            .putBoolean("space_cursor_swipe", c.spacebarCursorSwipe)
            .putInt("haptic_ms", c.hapticStrengthMs)
            .putBoolean("sound_enabled", c.keySoundEnabled)
            .putFloat("sound_volume", c.keySoundVolume)
            .putBoolean("auto_caps_en", c.autoCapsEnglish)
            .putBoolean("double_space_period", c.doubleSpacePeriod)
            .putInt("clip_max_unpinned", c.clipboardMaxUnpinned)
