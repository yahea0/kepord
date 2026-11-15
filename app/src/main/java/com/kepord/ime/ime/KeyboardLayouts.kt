package com.kepord.ime.ime

enum class KeyboardPage {
    ARABIC,
    ARABIC_SHIFT,
    ENGLISH,
    SYMBOLS_1,
    SYMBOLS_2
}

enum class KeyAction {
    CHAR,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    LANG_SWITCH,
    TO_SYMBOLS_1,
    TO_SYMBOLS_2,
    TO_ALPHA,
    OPEN_EMOJI,
    OPEN_CLIPBOARD
}

data class KeySpec(
    val label: String,
    val commitText: String = label,
    val shiftLabel: String = label.uppercase(),
    val shiftCommit: String = commitText.uppercase(),
    val hint: String? = null,
    val alternates: List<String> = emptyList(),
    val weight: Float = 1f,
    val action: KeyAction = KeyAction.CHAR,
    val isAccentSpecial: Boolean = false
)

object KeyboardLayouts {

    private fun charKey(
        label: String,
        commit: String = label,
        hint: String? = null,
        alternates: List<String> = emptyList(),
        weight: Float = 1f
    ) = KeySpec(
        label = label,
        commitText = commit,
        shiftLabel = if (label.length == 1 && label[0] in 'a'..'z') label.uppercase() else label,
        shiftCommit = if (commit.length == 1 && commit[0] in 'a'..'z') commit.uppercase() else commit,
        hint = hint,
        alternates = alternates,
        weight = weight,
        action = KeyAction.CHAR,
        isAccentSpecial = false
    )

    private fun specialKey(
        label: String,
        action: KeyAction,
        weight: Float = 1.3f,
        alternates: List<String> = emptyList()
    ) = KeySpec(
        label = label,
        commitText = "",
        shiftLabel = label,
        shiftCommit = "",
        hint = null,
        alternates = alternates,
        weight = weight,
        action = action,
        isAccentSpecial = true
    )

    private val numberRow = listOf(
        charKey("1", "1", hint = "!", alternates = listOf("١", "!", "¹")),
        charKey("2", "2", hint = "@", alternates = listOf("٢", "@", "²")),
        charKey("3", "3", hint = "#", alternates = listOf("٣", "#", "³")),
        charKey("4", "4", hint = "$", alternates = listOf("٤", "$", "€")),
        charKey("5", "5", hint = "%", alternates = listOf("٥", "%", "‰")),
        charKey("6", "6", hint = "^", alternates = listOf("٦", "^")),
        charKey("7", "7", hint = "&", alternates = listOf("٧", "&")),
        charKey("8", "8", hint = "*", alternates = listOf("٨", "*")),
        charKey("9", "9", hint = "(", alternates = listOf("٩", "(")),
        charKey("0", "0", hint = ")", alternates = listOf("٠", ")"))
    )

    private val arabicRows = listOf(
        listOf(
            charKey("ض", hint = "َ", alternates = listOf("ض", "َ")),
            charKey("ص", hint = "ً", alternates = listOf("ص", "ً")),
            charKey("ث", hint = "ُ", alternates = listOf("ث", "ُ")),
            charKey("ق", hint = "ٌ", alternates = listOf("ق", "ٌ")),
            charKey("ف", hint = "لإ", alternates = listOf("ف", "لإ", "ڤ")),
            charKey("غ", hint = "إ", alternates = listOf("غ", "إ")),
            charKey("ع", hint = "‘", alternates = listOf("ع")),
            charKey("ه", hint = "÷", alternates = listOf("ه", "ة")),
            charKey("خ", hint = "×", alternates = listOf("خ")),
            charKey("ح", hint = "؛", alternates = listOf("ح", "؛")),
            charKey("ج", hint = "<", alternates = listOf("ج", "چ")),
            charKey("د", hint = ">", alternates = listOf("د", "ذ"))
        ),
        listOf(
            charKey("ش", hint = "ِ", alternates = listOf("ش", "ِ")),
            charKey("س", hint = "ٍ", alternates = listOf("س", "ٍ")),
            charKey("ي", hint = "ى", alternates = listOf("ي", "ى", "ئ")),
            charKey("ب", hint = "لآ", alternates = listOf("ب", "پ")),
            charKey("ل", hint = "لأ", alternates = listOf("ل", "لأ", "لإ", "لآ")),
            charKey("ا", hint = "أ", alternates = listOf("ا", "أ", "إ", "آ", "ى")),
            charKey("ت", hint = "ـ", alternates = listOf("ت", "ـ")),
            charKey("ن", hint = "،", alternates = listOf("ن", "،")),
            charKey("م", hint = "/", alternates = listOf("م")),
            charKey("ك", hint = ":", alternates = listOf("ك", "گ")),
            charKey("ط", hint = "\"", alternates = listOf("ط", "ظ"))
        ),
        listOf(
            specialKey("تشكيل", KeyAction.SHIFT, weight = 1.45f),
            charKey("ئ", alternates = listOf("ئ", "ء", "ؤ")),
            charKey("ء", alternates = listOf("ء")),
            charKey("ؤ", alternates = listOf("ؤ")),
            charKey("ر", alternates = listOf("ر")),
            charKey("لا", "لا", alternates = listOf("لا", "لأ", "لإ", "لآ")),
            charKey("ى", alternates = listOf("ى", "ي")),
            charKey("ة", alternates = listOf("ة", "ه")),
            charKey("و", alternates = listOf("و", "ؤ")),
            charKey("ز", alternates = listOf("ز", "ذ", "ژ")),
            charKey("ظ", alternates = listOf("ظ", "ض")),
            specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
        ),
        listOf(
            specialKey("?123", KeyAction.TO_SYMBOLS_1, weight = 1.25f),
            specialKey("AR/EN", KeyAction.LANG_SWITCH, weight = 1.15f),
            specialKey("😀", KeyAction.OPEN_EMOJI, weight = 1.05f),
            charKey("،", "،", alternates = listOf("،", "؛", "؟", "!"), weight = 0.85f),
            specialKey("العربية • Kepord", KeyAction.SPACE, weight = 3.45f),
            charKey(".", ".", alternates = listOf(".", "؟", "!", ":", "..."), weight = 0.85f),
            specialKey("↵", KeyAction.ENTER, weight = 1.40f)
        )
    )

    private val arabicShiftRows = listOf(
        listOf(
            charKey("◌َ", "\u064E", hint = "فتحة"),
            charKey("◌ً", "\u064B", hint = "تنوين"),
            charKey("◌ُ", "\u064F", hint = "ضمة"),
            charKey("◌ٌ", "\u064C", hint = "تنوين"),
            charKey("◌ِ", "\u0650", hint = "كسرة"),
            charKey("◌ٍ", "\u064D", hint = "تنوين"),
            charKey("◌ّ", "\u0651", hint = "شدة"),
            charKey("◌ْ", "\u0652", hint = "سكون"),
            charKey("◌ٰ", "\u0670", hint = "ألف"),
            charKey("ـ", "ـ", hint = "تطويل")
        ),
        listOf(
            charKey("أ"),
            charKey("إ"),
            charKey("آ"),
            charKey("لأ"),
            charKey("لإ"),
            charKey("لآ"),
            charKey("ذ"),
            charKey("ة"),
            charKey("ى"),
            charKey("؟")
        ),
        listOf(
            specialKey("حروف", KeyAction.SHIFT, weight = 1.45f),
            charKey("«"),
            charKey("»"),
            charKey("؛"),
            charKey("،"),
            charKey("÷"),
            charKey("×"),
            charKey("٪"),
            charKey("﷼"),
            specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
        ),
        listOf(
            specialKey("?123", KeyAction.TO_SYMBOLS_1, weight = 1.25f),
            specialKey("AR/EN", KeyAction.LANG_SWITCH, weight = 1.15f),
            specialKey("😀", KeyAction.OPEN_EMOJI, weight = 1.05f),
            charKey("،", weight = 0.85f),
            specialKey("التشكيل • Kepord", KeyAction.SPACE, weight = 3.45f),
            charKey(".", weight = 0.85f),
            specialKey("↵", KeyAction.ENTER, weight = 1.40f)
        )
    )

    private val englishRows = listOf(
        listOf(
            charKey("q", hint = "1", alternates = listOf("q", "1")),
            charKey("w", hint = "2", alternates = listOf("w", "2")),
            charKey("e", hint = "3", alternates = listOf("e", "3", "é", "è")),
            charKey("r", hint = "4", alternates = listOf("r", "4")),
            charKey("t", hint = "5", alternates = listOf("t", "5")),
            charKey("y", hint = "6", alternates = listOf("y", "6")),
            charKey("u", hint = "7", alternates = listOf("u", "7", "ü", "ú")),
            charKey("i", hint = "8", alternates = listOf("i", "8", "í", "ï")),
            charKey("o", hint = "9", alternates = listOf("o", "9", "ó", "ö")),
            charKey("p", hint = "0", alternates = listOf("p", "0"))
        ),
        listOf(
            charKey("a", hint = "@", alternates = listOf("a", "@", "á", "à")),
            charKey("s", hint = "#", alternates = listOf("s", "#", "$")),
            charKey("d", hint = "$", alternates = listOf("d", "$")),
            charKey("f", hint = "_", alternates = listOf("f", "_")),
            charKey("g", hint = "&", alternates = listOf("g", "&")),
            charKey("h", hint = "-", alternates = listOf("h", "-")),
            charKey("j", hint = "+", alternates = listOf("j", "+")),
            charKey("k", hint = "(", alternates = listOf("k", "(")),
            charKey("l", hint = ")", alternates = listOf("l", ")"))
        ),
        listOf(
            specialKey("⇧", KeyAction.SHIFT, weight = 1.45f),
            charKey("z", hint = "*", alternates = listOf("z", "*")),
            charKey("x", hint = "\"", alternates = listOf("x", "\"")),
            charKey("c", hint = "'", alternates = listOf("c", "'")),
            charKey("v", hint = ":", alternates = listOf("v", ":")),
            charKey("b", hint = ";", alternates = listOf("b", ";")),
            charKey("n", hint = "!", alternates = listOf("n", "!")),
            charKey("m", hint = "?", alternates = listOf("m", "?")),
            specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
        ),
        listOf(
            specialKey("?123", KeyAction.TO_SYMBOLS_1, weight = 1.25f),
            specialKey("EN/AR", KeyAction.LANG_SWITCH, weight = 1.15f),
            specialKey("😀", KeyAction.OPEN_EMOJI, weight = 1.05f),
            charKey(",", ",", alternates = listOf(",", ";", ":"), weight = 0.85f),
            specialKey("English • Kepord", KeyAction.SPACE, weight = 3.45f),
            charKey(".", ".", alternates = listOf(".", "!", "?", "/", "_"), weight = 0.85f),
            specialKey("↵", KeyAction.ENTER, weight = 1.40f)
        )
    )

    private val symbols1Rows = listOf(
        listOf(
            charKey("1"), charKey("2"), charKey("3"), charKey("4"), charKey("5"),
            charKey("6"), charKey("7"), charKey("8"), charKey("9"), charKey("0")
        ),
        listOf(
            charKey("@"), charKey("#"), charKey("$"), charKey("_"), charKey("&"),
            charKey("-"), charKey("+"), charKey("("), charKey(")"), charKey("/")
        ),
        listOf(
            specialKey("=\\<", KeyAction.TO_SYMBOLS_2, weight = 1.45f),
            charKey("*"), charKey("\""), charKey("'"), charKey(":"),
            charKey(";"), charKey("!"), charKey("?"),
            specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
        ),
        listOf(
            specialKey("ABC", KeyAction.TO_ALPHA, weight = 1.25f),
            specialKey("AR/EN", KeyAction.LANG_SWITCH, weight = 1.15f),
            specialKey("😀", KeyAction.OPEN_EMOJI, weight = 1.05f),
            charKey(",", weight = 0.85f),
            specialKey("Space", KeyAction.SPACE, weight = 3.45f),
            charKey(".", weight = 0.85f),
            specialKey("↵", KeyAction.ENTER, weight = 1.40f)
        )
    )

    private val symbols2Rows = listOf(
        listOf(
            charKey("~"), charKey("`"), charKey("|"), charKey("•"), charKey("√"),
            charKey("π"), charKey("÷"), charKey("×"), charKey("¶"), charKey("Δ")
        ),
        listOf(
            charKey("{"), charKey("}"), charKey("["), charKey("]"), charKey("<"),
            charKey(">"), charKey("="), charKey("\\"), charKey("^"), charKey("%")
        ),
        listOf(
            specialKey("?123", KeyAction.TO_SYMBOLS_1, weight = 1.45f),
            charKey("©"), charKey("®"), charKey("™"), charKey("°"),
            charKey("€"), charKey("£"), charKey("¥"),
            specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
        ),
        listOf(
            specialKey("ABC", KeyAction.TO_ALPHA, weight = 1.25f),
            specialKey("AR/EN", KeyAction.LANG_SWITCH, weight = 1.15f),
            specialKey("😀", KeyAction.OPEN_EMOJI, weight = 1.05f),
            charKey("_", weight = 0.85f),
            specialKey("Coding Symbols", KeyAction.SPACE, weight = 3.45f),
            charKey(";", weight = 0.85f),
            specialKey("↵", KeyAction.ENTER, weight = 1.40f)
        )
    )

    fun getRows(page: KeyboardPage, showNumberRow: Boolean): List<List<KeySpec>> {
        val base = when (page) {
            KeyboardPage.ARABIC -> arabicRows
            KeyboardPage.ARABIC_SHIFT -> arabicShiftRows
            KeyboardPage.ENGLISH -> englishRows
            KeyboardPage.SYMBOLS_1 -> symbols1Rows
            KeyboardPage.SYMBOLS_2 -> symbols2Rows
        }
        return if (showNumberRow && page != KeyboardPage.SYMBOLS_1) {
            listOf(numberRow) + base
        } else {
            base
        }
    }
}
