package com.wkq.localsignage.feature.app.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonFieldsTest {
    @Test
    fun returnsNullForNullAndBlankValues() {
        assertNull(normalizedString(null))
        assertNull(normalizedString(""))
        assertNull(normalizedString("   "))
    }

    @Test
    fun trimsStringValues() {
        assertEquals("scene-id", normalizedString("  scene-id  "))
    }
}
