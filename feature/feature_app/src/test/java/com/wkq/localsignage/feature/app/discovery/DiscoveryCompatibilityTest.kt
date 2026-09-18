package com.wkq.localsignage.feature.app.discovery

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = Application::class)
class DiscoveryCompatibilityTest {
    @Test fun discoveryStateInitializesAndClearsOnMinimumSdk() {
        assertTrue(LocalDeviceDiscovery.snapshot().isEmpty())
        LocalDeviceDiscovery.stop()
        assertTrue(LocalDeviceDiscovery.snapshot().isEmpty())
    }
}
