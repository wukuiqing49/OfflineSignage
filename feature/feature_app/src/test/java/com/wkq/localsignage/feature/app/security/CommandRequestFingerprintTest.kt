package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CommandRequestFingerprintTest {
    @Test
    fun fingerprintIsStableRegardlessOfMapOrder() {
        val first = CommandRequestFingerprint.create(
            linkedMapOf("action" to "PLAY", "resourceId" to "resource-1", "revision" to "12")
        )
        val second = CommandRequestFingerprint.create(
            linkedMapOf("revision" to "12", "resourceId" to "resource-1", "action" to "PLAY")
        )

        assertEquals(first, second)
    }

    @Test
    fun fingerprintChangesWhenCommandMeaningChanges() {
        val play = CommandRequestFingerprint.create(mapOf("action" to "PLAY", "resourceId" to "resource-1"))
        val pause = CommandRequestFingerprint.create(mapOf("action" to "PAUSE", "resourceId" to "resource-1"))
        val otherResource = CommandRequestFingerprint.create(mapOf("action" to "PLAY", "resourceId" to "resource-2"))

        assertNotEquals(play, pause)
        assertNotEquals(play, otherResource)
    }
}
