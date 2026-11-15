package com.kepord.ime.ime

enum class ArabicFontStyle(val titleAr: String, val descAr: String) {
    NORMAL("1. خط النسخ الأصيل (العادي)", "كتابة عربية طبيعية بدون أي زخرفة"),
    DIWANI_MAMDUD("2. الخط الديواني الممدود", "وصلات تطويل انسيابية متوازنة بين الأحرف المتصلة"),
    THULUTH_ROYAL("3. خط الثلث الملوكي", "مدّات ثلثية عريضة وفخمة تحافظ على ربط الكلمة"),
    NASTALIQ_FARSI("4. خط النستعليق / الفارسي", "أحرف كـ/يـ/هـ الفارسية الانسيابية في الكتابة"),
    NASTALIQ_FLOW("5. النستعليق الممدود", "يمزج رسم النستعليق مع التطويل الانسيابي"),
    RUQAH_BALANCED("6. خط الرقعة المتناغم", "تطويل انتقائي للأحرف الأفقية العريضة فقط"),
    KUFI_GEOMETRIC("7. الخط الكوفي الهندسي", "أحرف كوفية هندسية منفصلة ومتباعدة بوضوح"),
    ANDALUSI_ORNATE("8. الخط الأندلسي المزخرف", "تطويل متوازن مع سكون خفيف على الأحرف العريضة"),
    CROWN_ALIF("9. خط التاج السامي", "تطويل مع ألف خنجرية لطيفة على الأحرف المميزة"),
    MODERN_CALLIGRAPHY("10. الخط العصري الفاخر", "دمج اللمسة الفارسية مع مدّات عريضة أنيقة")
}

object ArabicFontEngine {

    private val dualJoining = setOf(
        'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'س', 'ش', 'ص', 'ض',
        'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن',
        'ه', 'ي', 'ئ', 'ک', 'ی'
    )

    private val wideLetters = setOf(
        'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ف', 'ق', 'ك', 'ل', 'م', 'ک'
    )

    private val arabicLetters = setOf(
        'ا', 'أ', 'إ', 'آ', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ',
        'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ',
        'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و',
        'ؤ', 'ي', 'ئ', 'ى', 'ة', 'ء'
    )

    private val kufiIsolated = mapOf(
        'ا' to 'ﺍ', 'أ' to 'ﺃ', 'إ' to 'ﺇ', 'آ' to 'ﺁ', 'ب' to 'ﺏ',
        'ت' to 'ﺕ', 'ث' to 'ﺙ', 'ج' to 'ﺝ', 'ح' to 'ﺡ', 'خ' to 'ﺥ',
        'د' to 'ﺩ', 'ذ' to 'ﺫ', 'ر' to 'ﺭ', 'ز' to 'ﺯ', 'س' to 'ﺱ',
        'ش' to 'ﺵ', 'ص' to 'ﺹ', 'ض' to 'ﺽ', 'ط' to 'ﻁ', 'ظ' to 'ﻅ',
        'ع' to 'ﻉ', 'غ' to 'ﻍ', 'ف' to 'ﻑ', 'ق' to 'ﻕ', 'ك' to 'ﻙ',
        'ل' to 'ﻝ', 'م' to 'ﻡ', 'ن' to 'ﻥ', 'ه' to 'ﻩ', 'و' to 'ﻭ',
        'ؤ' to 'ﺅ', 'ي' to 'ﻱ', 'ئ' to 'ﺉ', 'ى' to 'ﻯ', 'ة' to 'ﺓ',
        'ء' to 'ﺀ'
    )

    private fun mapNastaliqChar(ch: Char): Char = when (ch) {
        'ك' -> 'ک'
        'ي' -> 'ی'
        'ى' -> 'ے'
        'ه' -> 'ہ'
        'ة' -> 'ۃ'
        else -> ch
    }

    fun transformOnCommit(
        input: String,
        textBeforeCursor: String,
        style: ArabicFontStyle
    ): String {
        if (style == ArabicFontStyle.NORMAL || input.isEmpty()) return input

        val prev = textBeforeCursor.lastOrNull { it !in '\u064B'..'\u0652' }

        if (input == "لا") {
            return when (style) {
                ArabicFontStyle.DIWANI_MAMDUD,
                ArabicFontStyle.NASTALIQ_FLOW -> if (prev != null && prev in dualJoining) "\u0640لا" else "لا"
                ArabicFontStyle.THULUTH_ROYAL -> if (prev != null && prev in dualJoining) "\u0640\u0640لا" else "لا"
                ArabicFontStyle.RUQAH_BALANCED,
                ArabicFontStyle.MODERN_CALLIGRAPHY -> if (prev != null && prev in wideLetters) "\u0640لا" else "لا"
                ArabicFontStyle.ANDALUSI_ORNATE -> when {
                    prev == null || prev !in dualJoining -> "لا"
                    prev in wideLetters -> "\u0652\u0640لا"
                    else -> "\u0640لا"
                }
                ArabicFontStyle.CROWN_ALIF -> when {
                    prev == null || prev !in dualJoining -> "لا"
                    prev in wideLetters -> "\u0670\u0640لا"
                    else -> "\u0640لا"
                }
                ArabicFontStyle.KUFI_GEOMETRIC -> {
                    val lastRaw = textBeforeCursor.lastOrNull()
                    val prefix = if (lastRaw != null && !lastRaw.isWhitespace()) "\u2009" else ""
                    "${prefix}ﻝ\u2009ﺍ"
                }
                else -> "لا"
            }
        }

        if (input.length != 1) return input
        val ch = input[0]
        if (ch !in arabicLetters) return input

        val isLamAlif = prev == 'ل' && (ch == 'ا' || ch == 'أ' || ch == 'إ' || ch == 'آ')

        return when (style) {
            ArabicFontStyle.NORMAL -> input

            ArabicFontStyle.DIWANI_MAMDUD -> {
                if (prev != null && prev in dualJoining && !isLamAlif) "\u0640$ch" else input
            }

            ArabicFontStyle.THULUTH_ROYAL -> {
                if (prev != null && prev in dualJoining && !isLamAlif) "\u0640\u0640$ch" else input
            }

            ArabicFontStyle.NASTALIQ_FARSI -> {
                mapNastaliqChar(ch).toString()
            }

            ArabicFontStyle.NASTALIQ_FLOW -> {
                val mapped = mapNastaliqChar(ch)
                if (prev != null && prev in dualJoining && !isLamAlif) "\u0640$mapped" else mapped.toString()
            }

            ArabicFontStyle.RUQAH_BALANCED -> {
                if (prev != null && prev in wideLetters && !isLamAlif) "\u0640$ch" else input
            }

            ArabicFontStyle.KUFI_GEOMETRIC -> {
                val iso = kufiIsolated[ch] ?: ch
                val lastRaw = textBeforeCursor.lastOrNull()
                val prefix = if (lastRaw != null && !lastRaw.isWhitespace()) "\u2009" else ""
                "$prefix$iso"
            }

            ArabicFontStyle.ANDALUSI_ORNATE -> {
                if (prev != null && prev in dualJoining && !isLamAlif) {
                    if (prev in wideLetters) "\u0652\u0640$ch" else "\u0640$ch"
                } else {
                    input
                }
            }

            ArabicFontStyle.CROWN_ALIF -> {
                if (prev != null && prev in dualJoining && !isLamAlif) {
                    if (prev in wideLetters) "\u0670\u0640$ch" else "\u0640$ch"
                } else {
                    input
                }
            }

            ArabicFontStyle.MODERN_CALLIGRAPHY -> {
                val mapped = mapNastaliqChar(ch)
                if (prev != null && prev in wideLetters && !isLamAlif) "\u0640$mapped" else mapped.toString()
            }
        }
    }

    fun computeBackspaceDeleteCount(
        textBeforeCursor: String,
        style: ArabicFontStyle
    ): Int {
        if (style == ArabicFontStyle.NORMAL || textBeforeCursor.length < 2) return 1
        val last = textBeforeCursor.last()
        if (Character.isLowSurrogate(last)) return 1

        var idx = textBeforeCursor.length - 2
        var extra = 0
        while (idx >= 0) {
            val c = textBeforeCursor[idx]
            val isFontConnector = c == '\u0640' || c == '\u2009' || c == '\u0652' || c == '\u0670'
            if (isFontConnector) {
                extra++
                idx--
            } else {
                break
            }
        }
        return if (extra > 0) 1 + extra else 1
    }

    fun transformFullText(text: String, style: ArabicFontStyle): String {
        if (style == ArabicFontStyle.NORMAL || text.isEmpty()) return text
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val start = (sb.length - 4).coerceAtLeast(0)
            val before = sb.substring(start)
            sb.append(transformOnCommit(ch.toString(), before, style))
        }
        return sb.toString()
    }
}
