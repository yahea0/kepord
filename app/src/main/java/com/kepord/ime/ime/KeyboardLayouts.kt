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

data class KeyRowSpec(
    val keys: List<KeySpec>,
    val heightWeight: Float = 1.0f
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
        weight: Float = 1.0f,
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

    private val arabicNumberRow = KeyRowSpec(
        heightWeight = 0.78f,
        keys = listOf(
            charKey("١", "١", hint = "1", alternates = listOf("١", "1", "!")),
            charKey("٢", "٢", hint = "2", alternates = listOf("٢", "2", "@")),
            charKey("٣", "٣", hint = "3", alternates = listOf("٣", "3", "#")),
            charKey("٤", "٤", hint = "4", alternates = listOf("٤", "4", "$")),
            charKey("٥", "٥", hint = "5", alternates = listOf("٥", "5", "%")),
            charKey("٦", "٦", hint = "6", alternates = listOf("٦", "6", "^")),
            charKey("٧", "٧", hint = "7", alternates = listOf("٧", "7", "&")),
            charKey("٨", "٨", hint = "8", alternates = listOf("٨", "8", "*")),
            charKey("٩", "٩", hint = "9", alternates = listOf("٩", "9", "(")),
            charKey("٠", "٠", hint = "0", alternates = listOf("٠", "0", ")"))
        )
    )

    private val latinNumberRow = KeyRowSpec(
        heightWeight = 0.78f,
        keys = listOf(
            charKey("1", "1", hint = "!", alternates = listOf("1", "١", "!")),
            charKey("2", "2", hint = "@", alternates = listOf("2", "٢", "@")),
            charKey("3", "3", hint = "#", alternates = listOf("3", "٣", "#")),
            charKey("4", "4", hint = "$", alternates = listOf("4", "٤", "$")),
            charKey("5", "5", hint = "%", alternates = listOf("5", "٥", "%")),
            charKey("6", "6", hint = "^", alternates = listOf("6", "٦", "^")),
            charKey("7", "7", hint = "&", alternates = listOf("7", "٧", "&")),
            charKey("8", "8", hint = "*", alternates = listOf("8", "٨", "*")),
            charKey("9", "9", hint = "(", alternates = listOf("9", "٩", "(")),
            charKey("0", "0", hint = ")", alternates = listOf("0", "٠", ")"))
        )
    )

    // مطابق تماماً لتوزيع وأحجام ومحاذاة الأزرار الـ 11 في الصورة
    private val arabicRows = listOf(
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("ض", hint = "َ", alternates = listOf("ض", "َ")),
                charKey("ص", hint = "ً", alternates = listOf("ص", "ً")),
                charKey("ث", hint = "ُ", alternates = listOf("ث", "ُ")),
                charKey("ق", hint = "ٌ", alternates = listOf("ق", "ٌ")),
                charKey("ف", hint = "ڤ", alternates = listOf("ف", "ڤ")),
                charKey("غ", alternates = listOf("غ")),
                charKey("ع", alternates = listOf("ع")),
                charKey("ه", hint = "ة", alternates = listOf("ه", "ة")),
                charKey("خ", alternates = listOf("خ")),
                charKey("ح", alternates = listOf("ح")),
                charKey("ج", hint = "چ", alternates = listOf("ج", "چ"))
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("ش", hint = "ِ", alternates = listOf("ش", "ِ")),
                charKey("س", hint = "ٍ", alternates = listOf("س", "ٍ")),
                charKey("ي", hint = "ئ", alternates = listOf("ي", "ئ", "ى")),
                charKey("ب", hint = "پ", alternates = listOf("ب", "پ")),
                charKey("ل", hint = "لا", alternates = listOf("ل", "لا", "لأ", "لإ", "لآ")),
                charKey("ا", hint = "أ", alternates = listOf("ا", "أ", "إ", "آ", "ى")),
                charKey("ت", hint = "ـ", alternates = listOf("ت", "ـ")),
                charKey("ن", alternates = listOf("ن")),
                charKey("م", alternates = listOf("م")),
                charKey("ك", hint = "گ", alternates = listOf("ك", "گ")),
                charKey("ط", alternates = listOf("ط"))
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("ذ", hint = "ّ", alternates = listOf("ذ", "\u0651", "\u0652")),
                charKey("ء", hint = "ئ", alternates = listOf("ء", "ئ", "ؤ")),
                charKey("ؤ", alternates = listOf("ؤ")),
                charKey("ر", alternates = listOf("ر")),
                charKey("ى", alternates = listOf("ى", "ي")),
                charKey("ة", alternates = listOf("ة", "ه")),
                charKey("و", alternates = listOf("و", "ؤ")),
                charKey("ز", hint = "ژ", alternates = listOf("ز", "ژ")),
                charKey("ظ", alternates = listOf("ظ")),
                charKey("د", alternates = listOf("د")),
                specialKey("⌫", KeyAction.BACKSPACE, weight = 1.0f)
            )
        ),
        KeyRowSpec(
            heightWeight = 1.04f,
            keys = listOf(
                specialKey("?٣٢١", KeyAction.TO_SYMBOLS_1, weight = 1.48f),
                charKey("/", "/", alternates = listOf("/", "،", "؛", "؟", "!"), weight = 1.05f),
                specialKey("🌐", KeyAction.LANG_SWITCH, weight = 1.05f),
                specialKey("العربية", KeyAction.SPACE, weight = 4.85f),
                charKey(".", ".", alternates = listOf(".", "،", "؟", "!", "؛", ":"), weight = 1.05f),
                specialKey("↵", KeyAction.ENTER, weight = 1.52f)
            )
        )
    )

    private val arabicShiftRows = arabicRows

    private val englishRows = listOf(
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
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
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("a", hint = "@", alternates = listOf("a", "@", "á", "à")),
                charKey("s", hint = "#", alternates = listOf("s", "#", "$")),
                charKey("d", hint = "$", alternates = listOf("d", "$")),
                charKey("f", hint = "_", alternates = listOf("f", "_")),
                charKey("g", hint = "&", alternates = listOf("g", "&")),
                charKey("h", hint = "-", alternates = listOf("h", "-")),
                charKey("j", hint = "+", alternates = listOf("j", "+")),
                charKey("k", hint = "(", alternates = listOf("k", "(")),
                charKey("l", hint = ")", alternates = listOf("l", ")"))
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                specialKey("⇧", KeyAction.SHIFT, weight = 1.45f),
                charKey("z", hint = "*", alternates = listOf("z", "*")),
                charKey("x", hint = "\"", alternates = listOf("x", "\"")),
                charKey("c", hint = "'", alternates = listOf("c", "'")),
                charKey("v", hint = ":", alternates = listOf("v", ":")),
                charKey("b", hint = ";", alternates = listOf("b", ";")),
                charKey("n", hint = "!", alternates = listOf("n", "!")),
                charKey("m", hint = "?", alternates = listOf("m", "?")),
                specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
            )
        ),
        KeyRowSpec(
            heightWeight = 1.04f,
            keys = listOf(
                specialKey("?123", KeyAction.TO_SYMBOLS_1, weight = 1.48f),
                charKey(",", ",", alternates = listOf(",", "/", ";", ":"), weight = 1.05f),
                specialKey("🌐", KeyAction.LANG_SWITCH, weight = 1.05f),
                specialKey("English", KeyAction.SPACE, weight = 4.85f),
                charKey(".", ".", alternates = listOf(".", "!", "?", "/", "_"), weight = 1.05f),
                specialKey("↵", KeyAction.ENTER, weight = 1.52f)
            )
        )
    )

    private val symbols1Rows = listOf(
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("1", alternates = listOf("1", "١")),
                charKey("2", alternates = listOf("2", "٢")),
                charKey("3", alternates = listOf("3", "٣")),
                charKey("4", alternates = listOf("4", "٤")),
                charKey("5", alternates = listOf("5", "٥")),
                charKey("6", alternates = listOf("6", "٦")),
                charKey("7", alternates = listOf("7", "٧")),
                charKey("8", alternates = listOf("8", "٨")),
                charKey("9", alternates = listOf("9", "٩")),
                charKey("0", alternates = listOf("0", "٠"))
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("@"), charKey("#"), charKey("$"), charKey("_"), charKey("&"),
                charKey("-"), charKey("+"), charKey("("), charKey(")"), charKey("/")
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                specialKey("=\\<", KeyAction.TO_SYMBOLS_2, weight = 1.45f),
                charKey("*"), charKey("\""), charKey("'"), charKey(":"),
                charKey(";"), charKey("!"), charKey("؟", alternates = listOf("؟", "?")),
                specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
            )
        ),
        KeyRowSpec(
            heightWeight = 1.04f,
            keys = listOf(
                specialKey("أ ب ج", KeyAction.TO_ALPHA, weight = 1.48f),
                charKey("،", "،", alternates = listOf("،", ",", "/"), weight = 1.05f),
                specialKey("🌐", KeyAction.LANG_SWITCH, weight = 1.05f),
                specialKey("المسافة", KeyAction.SPACE, weight = 4.85f),
                charKey(".", weight = 1.05f),
                specialKey("↵", KeyAction.ENTER, weight = 1.52f)
            )
        )
    )

    private val symbols2Rows = listOf(
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("~"), charKey("`"), charKey("|"), charKey("•"), charKey("√"),
                charKey("π"), charKey("÷"), charKey("×"), charKey("¶"), charKey("Δ")
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                charKey("{"), charKey("}"), charKey("["), charKey("]"), charKey("<"),
                charKey(">"), charKey("="), charKey("\\"), charKey("^"), charKey("%")
            )
        ),
        KeyRowSpec(
            heightWeight = 1.0f,
            keys = listOf(
                specialKey("?٣٢١", KeyAction.TO_SYMBOLS_1, weight = 1.45f),
                charKey("◌َ", "\u064E"), charKey("◌ً", "\u064B"), charKey("◌ُ", "\u064F"),
                charKey("◌ِ", "\u0650"), charKey("◌ّ", "\u0651"), charKey("◌ْ", "\u0652"),
                charKey("ـ", "ـ"),
                specialKey("⌫", KeyAction.BACKSPACE, weight = 1.45f)
            )
        ),
        KeyRowSpec(
            heightWeight = 1.04f,
            keys = listOf(
                specialKey("أ ب ج", KeyAction.TO_ALPHA, weight = 1.48f),
                charKey("«", alternates = listOf("«", "»")),
                specialKey("🌐", KeyAction.LANG_SWITCH, weight = 1.05f),
                specialKey("الرموز والتشكيل", KeyAction.SPACE, weight = 4.85f),
                charKey("؛", alternates = listOf("؛", ";")),
                specialKey("↵", KeyAction.ENTER, weight = 1.52f)
            )
        )
    )

    fun getRows(page: KeyboardPage, showNumberRow: Boolean): List<KeyRowSpec> {
        val base = when (page) {
            KeyboardPage.ARABIC -> arabicRows
            KeyboardPage.ARABIC_SHIFT -> arabicShiftRows
            KeyboardPage.ENGLISH -> englishRows
            KeyboardPage.SYMBOLS_1 -> symbols1Rows
            KeyboardPage.SYMBOLS_2 -> symbols2Rows
        }
        return if (showNumberRow && page != KeyboardPage.SYMBOLS_1) {
            val numRow = if (page == KeyboardPage.ARABIC || page == KeyboardPage.ARABIC_SHIFT) {
                arabicNumberRow
            } else {
                latinNumberRow
            }
            listOf(numRow) + base
        } else {
            base
        }
    }
}
