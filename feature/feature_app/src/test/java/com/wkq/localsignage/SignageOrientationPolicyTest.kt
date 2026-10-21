package com.wkq.localsignage

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SignageOrientationPolicyTest {
    @Test fun newDeviceDefaultsToLandscape() {
        assertEquals("LANDSCAPE", com.wkq.localsignage.feature.app.model.SignageSettings().orientation)
    }

    @Test fun tabletDoesNotLockEitherOrientation() {
        for (preference in listOf("LANDSCAPE", "PORTRAIT", "AUTO")) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, SignageOrientationPolicy.resolve(preference, 600, false, 36))
        }
    }

    @Test fun splitScreenFollowsWindowEvenWhenNarrow() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, SignageOrientationPolicy.resolve("LANDSCAPE", 360, true, 35))
    }

    @Test fun phoneRetainsExplicitPlaybackPreference() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, SignageOrientationPolicy.resolve("LANDSCAPE", 599, false, 36))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, SignageOrientationPolicy.resolve("PORTRAIT", 360, false, 36))
    }

    @Test fun olderTabletHonorsExplicitOrientation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, SignageOrientationPolicy.resolve("LANDSCAPE", 800, false, 35))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, SignageOrientationPolicy.resolve("PORTRAIT", 800, false, 35))
    }

    @Test fun autoFollowsDeviceOnPhone() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, SignageOrientationPolicy.resolve("AUTO", 360, false, 36))
    }
}
