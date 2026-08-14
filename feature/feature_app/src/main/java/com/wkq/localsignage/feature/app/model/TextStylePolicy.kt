package com.wkq.localsignage.feature.app.model

object TextStylePolicy {
    const val DEFAULT_TEXT_SIZE_SP = 48
    const val DEFAULT_TEXT_COLOR = "#FFFFFFFF"
    const val DEFAULT_BACKGROUND_COLOR = "#FF000000"
    const val DEFAULT_FONT_FAMILY = "SYSTEM_SANS"
    const val DEFAULT_CJK_FONT_FAMILY = "ZCOOL_XIAOWEI"

    const val MIN_TEXT_SIZE_SP = 8
    const val MAX_TEXT_SIZE_SP = 200

    val supportedFontFamilies = setOf(
        "SYSTEM_SANS",
        "SYSTEM_SERIF",
        "MONOSPACE",
        "LATO",
        "CRIMSON_TEXT",
        "BEBAS_NEUE",
        "MA_SHAN_ZHENG",
        "ZCOOL_XIAOWEI",
        "ZCOOL_KUAILE"
    )

    fun normalizeTextSize(value: Int?): Int =
        (value ?: DEFAULT_TEXT_SIZE_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)

    fun normalizeFontFamily(value: String?): String = value
        ?.trim()
        ?.uppercase()
        ?.takeIf(supportedFontFamilies::contains)
        ?: DEFAULT_FONT_FAMILY

    fun fontFamilyForText(value: String?, text: String?): String {
        val normalized = normalizeFontFamily(value)
        return if (normalized in LATIN_ONLY_FONT_FAMILIES && containsCjk(text)) {
            DEFAULT_CJK_FONT_FAMILY
        } else normalized
    }

    fun normalizeColor(value: String?, fallback: String): String {
        val raw = value?.trim()?.uppercase().orEmpty()
        return when {
            RGB_COLOR.matches(raw) -> "#FF${raw.drop(1)}"
            ARGB_COLOR.matches(raw) -> raw
            else -> fallback
        }
    }

    private val RGB_COLOR = Regex("^#[0-9A-F]{6}$")
    private val ARGB_COLOR = Regex("^#[0-9A-F]{8}$")
    private val LATIN_ONLY_FONT_FAMILIES = setOf("LATO", "CRIMSON_TEXT", "BEBAS_NEUE")

    private fun containsCjk(value: String?): Boolean = value.orEmpty().any { character ->
        character in '\u3000'..'\u303F' ||
            character in '\u3400'..'\u4DBF' ||
            character in '\u4E00'..'\u9FFF' ||
            character in '\uF900'..'\uFAFF' ||
            character in '\uFF00'..'\uFFEF'
    }
}
