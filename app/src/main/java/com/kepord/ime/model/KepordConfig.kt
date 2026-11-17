package com.kepord.ime.model

import com.kepord.ime.ime.ArabicFontStyle
import com.kepord.ime.ime.MechanicalSoundProfile

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
    val letterColorMode: LetterColorMode = LetterColorMode.RAINBOW_WAVE,
    val staticLetterColor: Int = 0xFFFFFFFF.toInt(),
    val gradientColor1: Int = 0xFF00F0FF.toInt(),
    val gradientColor2: Int = 0xFFFFFFFF.toInt(),
    val gradientColor3: Int = 0xFFFF007F.toInt(),
    val wavePattern: WavePattern = WavePattern.DIAGONAL,
    val waveSpeed: Float = 1.2f,
    val letterGlow: Float = 0.65f,
    val reactiveTouchRipple: Boolean = true,

    val background3DStyle: Background3DStyle = Background3DStyle.GRID_3D,
    val bg3DIntensity: Float = 0.72f,
    val keycapOpacity: Float = 0.92f,
    val letter3DDepth: Int = 4,
    val keyBevelDepth: Int = 3,
    val keyCornerRadiusDp: Float = 9f,
    val showKeyBorders: Boolean = true,
    val showSecondaryHints: Boolean = true,

    val arabicFontStyle: ArabicFontStyle = ArabicFontStyle.NORMAL,
    val keyboardHeightScale: Float = 1.0f,
    val letterSizeScale: Float = 1.0f,
    val showNumberRow: Boolean = true,
    val defaultLanguageArabic: Boolean = true,
    val keyPopupEnabled: Boolean = true,
    val spacebarCursorSwipe: Boolean = true,
    val hapticStrengthMs: Int = 18,
    val keySoundEnabled: Boolean = true,
    val keySoundVolume: Float = 0.75f,
    val mechanicalSoundProfile: MechanicalSoundProfile = MechanicalSoundProfile.CLICKY_BLUE,
    val touchpadSensitivity: Float = 1.65f,
    val autoCapsEnglish: Boolean = true,
    val doubleSpacePeriod: Boolean = true,

    val clipboardMaxUnpinned: Int = 20,
    val clipboardMonitorEnabled: Boolean = true,
    val clipboardSkipSensitive: Boolean = true
)
