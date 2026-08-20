package com.wkq.localsignage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {
    @Test
    fun `Android 15 boot waits for user launch`() {
        assertTrue(
            BootRestorePolicy.shouldDeferUntilUserLaunch(
                action = "android.intent.action.BOOT_COMPLETED",
                sdkInt = 35
            )
        )
    }

    @Test
    fun `Android 14 keeps legacy dedicated-device restore`() {
        assertFalse(
            BootRestorePolicy.shouldDeferUntilUserLaunch(
                action = "android.intent.action.BOOT_COMPLETED",
                sdkInt = 34
            )
        )
    }

    @Test
    fun `package replacement may restore service on Android 15`() {
        assertFalse(
            BootRestorePolicy.shouldDeferUntilUserLaunch(
                action = "android.intent.action.MY_PACKAGE_REPLACED",
                sdkInt = 35
            )
        )
    }
}
