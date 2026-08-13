package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRevisionPolicyTest {
    @Test
    fun acceptsOnlyNewerExplicitRevision() {
        assertTrue(CommandRevisionPolicy.canAccept(12, 13))
        assertFalse(CommandRevisionPolicy.canAccept(12, 12))
        assertFalse(CommandRevisionPolicy.canAccept(12, 11))
    }

    @Test
    fun acceptsLegacyCommandWithoutRevision() {
        assertTrue(CommandRevisionPolicy.canAccept(12, null))
    }
}
