package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextStylePolicyTest {
    @Test
    fun `normalizes rgb and argb colors`() {
        assertEquals("#FF12ABEF", TextStylePolicy.normalizeColor("#12abef", "#FFFFFFFF"))
        assertEquals("#8012ABEF", TextStylePolicy.normalizeColor("#8012abef", "#FFFFFFFF"))
    }

    @Test
    fun `rejects malformed colors`() {
        assertEquals("#FFFFFFFF", TextStylePolicy.normalizeColor("red", "#FFFFFFFF"))
        assertEquals("#99000000", TextStylePolicy.normalizeColor("#12345", "#99000000"))
    }

    @Test
    fun `normalizes supported fonts and size bounds`() {
        assertEquals("ZCOOL_XIAOWEI", TextStylePolicy.normalizeFontFamily("zcool_xiaowei"))
        assertEquals("LATO", TextStylePolicy.normalizeFontFamily("lato"))
        assertEquals("CRIMSON_TEXT", TextStylePolicy.normalizeFontFamily("crimson_text"))
        assertEquals("BEBAS_NEUE", TextStylePolicy.normalizeFontFamily("bebas_neue"))
        assertEquals(TextStylePolicy.DEFAULT_FONT_FAMILY, TextStylePolicy.normalizeFontFamily("unknown"))
        assertEquals(8, TextStylePolicy.normalizeTextSize(1))
        assertEquals(200, TextStylePolicy.normalizeTextSize(999))
    }

    @Test
    fun `uses commercially licensed cjk font when latin font cannot display chinese`() {
        assertEquals("LATO", TextStylePolicy.fontFamilyForText("LATO", "Summer sale"))
        assertEquals("ZCOOL_XIAOWEI", TextStylePolicy.fontFamilyForText("LATO", "夏季促销"))
        assertEquals("MA_SHAN_ZHENG", TextStylePolicy.fontFamilyForText("MA_SHAN_ZHENG", "夏季促销"))
    }
}
