package com.kepord.ime.ime

enum class ArabicFontStyle(val titleAr: String, val descAr: String) {
    NORMAL("1. خط النسخ الأصيل (العادي)", "كتابة عربية طبيعية واضحة بدون أي تغيير"),
    UTHMANI("2. الخط العثماني (رسم المصحف)", "ألف الوصل ٱ والسكون العثماني ۡ والألف الخنجرية ٰ"),
    RUQAH("3. خط الرقعة الأصيل", "حروف متراصة ورشيقة كخط الرقعة اليدوي السريع"),
    HANDWRITING("4. خط اليد الحر", "تشكيل ولمسات قلم يدوية متدفقة وانسيابية"),
    DIWANI("5. الخط الديواني الملكي", "وصلات مقوّسة وانسيابية ديوانية فخمة"),
    THULUTH("6. خط الثلث الكلاسيكي", "امتدادات كلاسيكية متوازنة وعريضة"),
    KUFI("7. الخط الكوفي الهندسي", "أحرف كوفية هندسية منفصلة ومتناسقة"),
    FARSI_NASTALIQ("8. الخط الفارسي (النستعليق)", "أحرف ک ی ے ہ الفارسية الانسيابية"),
    MAGHREBI("9. الخط المغربي / الأندلسي", "لمسة مغربية أندلسية عريقة ومتزنة"),
    ORNATE_LUXURY("10. الخط المزخرف الفاخر", "زخرفة خطية أنيقة تحافظ على وضوح القراءة")
}

object ArabicFontEngine {

    private val dualJoining = setOf(
        'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'س', 'ش', 'ص', 'ض',
        'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن',
        'ه', 'ي', 'ئ', 'ک', 'ی', 'ھ'
    )

    private val wideLetters = setOf(
        'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ف', 'ق', 'ك', 'ل', 'م', 'ک'
    )

    private val sweepLetters = setOf(
        'س', 'ش', 'ص', 'ض'
    )

    private val handwritingAnchors = setOf(
        'ب', 'ت', 'ن', 'ي', 'م', 'ی'
    )

    private val arabicLetters = setOf(
        'ا', 'أ', 'إ', 'آ', 'ٱ', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ',
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

    private val diacriticsAndMarks = setOf(
        '\u064E', '\u064B', '\u064F', '\u064C', '\u0650', '\u064D',
        '\u0651', '\u0652', '\u0670', '\u06E1', '\u0654'
    )

    private fun mapNastaliqChar(ch: Char): Char = when (ch) {
        'ك' -> 'ک'
        'ي' -> 'ی'
        'ى' -> 'ے'
        'ه' -> 'ہ'
        'ة' -> 'ۃ'
        else -> ch
    }

    private fun mapRuqahChar(ch: Char): Char = when (ch) {
        'ك' -> 'ک'
        'ي' -> 'ی'
        'ى' -> 'ے'
        'ه' -> 'ھ'
        else -> ch
    }

    private fun mapUthmaniChar(ch: Char, prev: Char?): Char = when {
        ch == 'ا' && (prev == null || prev.isWhitespace()) -> 'ٱ'
        ch == 'ك' -> 'ک'
        ch == 'ي' -> 'ی'
        ch == 'ى' -> 'ی'
        else -> ch
    }

    fun transformOnCommit(
        input: String,
        textBeforeCursor: String,
        style: ArabicFontStyle
    ): String {
        if (style == ArabicFontStyle.NORMAL || input.isEmpty()) return input

        val prev = textBeforeCursor.lastOrNull { it !in diacriticsAndMarks && it != '\u0640' && it != '\u2009' }
        val prevImmediate = textBeforeCursor.lastOrNull()

        if (input == "لا") {
            val canJoin = prevImmediate != null && prevImmediate in dualJoining
            return when (style) {
                ArabicFontStyle.DIWANI -> if (canJoin) "\u0640لا" else "لا"
                ArabicFontStyle.THULUTH -> if (canJoin) "\u0640\u0640لا" else "لا"
                ArabicFontStyle.KUFI -> {
                    val prefix = if (prevImmediate != null && !prevImmediate.isWhitespace()) "\u2009" else ""
                    "${prefix}ﻝ\u2009ﺍ"
                }
                ArabicFontStyle.UTHMANI -> if (canJoin) "\u06E1لا" else "لا"
                ArabicFontStyle.RUQAH -> if (prev != null && prev in sweepLetters) "\u0640لا" else "لا"
                ArabicFontStyle.ORNATE_LUXURY -> if (canJoin) "\u0670\u0640لا" else "لا"
                else -> "لا"
            }
        }

        if (input.length != 1) return input
        val ch = input[0]
        if (ch !in arabicLetters) return input

        val isLamAlif = prev == 'ل' && (ch == 'ا' || ch == 'أ' || ch == 'إ' || ch == 'آ')

        return when (style) {
            ArabicFontStyle.NORMAL -> input

            ArabicFontStyle.UTHMANI -> {
                val mapped = mapUthmaniChar(ch, prevImmediate)
                val isAfterLam = prev == 'ل' && (ch == 'ا' || ch == 'ه')
                val uthmaniAccent = when {
                    isLamAlif || isAfterLam -> ""
                    prev != null && prev in setOf('ن', 'م', 'س', 'ع', 'ل') && prevImmediate in dualJoining -> "\u06E1"
                    prev != null && prev in setOf('ر', 'ح', 'ق') -> "\u0670"
                    else -> ""
                }
                "$uthmaniAccent$mapped"
            }

            ArabicFontStyle.RUQAH -> {
                val mapped = mapRuqahChar(ch)
                if (prevImmediate != null && prevImmediate in sweepLetters && !isLamAlif) {
                    "\u0640$mapped"
                } else {
                    mapped.toString()
                }
            }

            ArabicFontStyle.HANDWRITING -> {
                val mapped = mapNastaliqChar(ch)
                val penSweep = if (prevImmediate != null && prevImmediate in handwritingAnchors && !isLamAlif) {
                    "\u0640"
                } else {
                    ""
                }
                val penAccent = when {
                    isLamAlif || prev == null || prev.isWhitespace() -> ""
                    ch == 'م' || ch == 'ن' -> "\u064E"
                    ch == 'ب' || ch == 'ت' -> "\u0650"
                    else -> ""
                }
                "$penSweep$mapped$penAccent"
            }

            ArabicFontStyle.DIWANI -> {
                val mapped = mapRuqahChar(ch)
                if (prevImmediate != null && prevImmediate in dualJoining && !isLamAlif) {
                    "\u0640$mapped"
                } else {
                    mapped.toString()
                }
            }

            ArabicFontStyle.THULUTH -> {
                if (prevImmediate != null && prevImmediate in dualJoining && !isLamAlif) {
                    "\u0640\u0640$ch"
                } else {
                    input
                }
            }

            ArabicFontStyle.KUFI -> {
                val iso = kufiIsolated[ch] ?: ch
                val prefix = if (prevImmediate != null && !prevImmediate.isWhitespace()) "\u2009" else ""
                "$prefix$iso"
            }

            ArabicFontStyle.FARSI_NASTALIQ -> {
                mapNastaliqChar(ch).toString()
            }

            ArabicFontStyle.MAGHREBI -> {
                val mapped = if (ch == 'ي') 'ی' else ch
                if (prevImmediate != null && prevImmediate in dualJoining && !isLamAlif) {
                    if (prevImmediate in wideLetters) "\u0652\u0640$mapped" else "\u0640$mapped"
                } else {
                    mapped.toString()
                }
            }

            ArabicFontStyle.ORNATE_LUXURY -> {
                val mapped = mapNastaliqChar(ch)
                if (prevImmediate != null && prevImmediate in dualJoining && !isLamAlif) {
                    if (prevImmediate in wideLetters) "\u0670\u0640$mapped" else "\u0640$mapped"
                } else {
                    mapped.toString()
                }
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

        var deleteCount = 1
        var idx = textBeforeCursor.length - 1

        // If cursor is right on a trailing font diacritic (like Handwriting accent), consume the base letter behind it too
        if (last in diacriticsAndMarks) {
            idx--
            if (idx >= 0) deleteCount++
        }

        // Clean up any font-injected connectors/ornaments immediately preceding the deleted letter
        var scan = idx - 1
        while (scan >= 0) {
            val c = textBeforeCursor[scan]
            val isFontConnector = c == '\u0640' || c == '\u2009' || c in diacriticsAndMarks
            if (isFontConnector) {
                deleteCount++
                scan--
            } else {
                break
            }
        }
        return deleteCount
    }

    fun transformFullText(text: String, style: ArabicFontStyle): String {
        if (style == ArabicFontStyle.NORMAL || text.isEmpty()) return text
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val start = (sb.length - 6).coerceAtLeast(0)
            val before = sb.substring(start)
            sb.append(transformOnCommit(ch.toString(), before, style))
        }
        return sb.toString()
    }
}
