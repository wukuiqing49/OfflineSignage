package com.wkq.localsignage.feature.app.server

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonArrayEncoderTest {
    @Test
    fun `encodes empty and populated arrays with valid boundaries`() {
        assertEquals("[]", jsonArray(emptyList<String>()) { it })
        assertEquals("[1]", jsonArray(listOf(1)) { it.toString() })
        assertEquals("[1, 2, 3]", jsonArray(listOf(1, 2, 3)) { it.toString() })
    }
}
