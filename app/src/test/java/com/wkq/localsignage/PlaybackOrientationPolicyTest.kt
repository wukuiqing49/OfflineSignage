package com.wkq.localsignage

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOrientationPolicyTest {
    @Test fun tabletDoesNotLockEitherOrientation() {
        for (preference in listOf("LANDSCAPE", "PORTRAIT", "AUTO")) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, PlaybackOrientationPolicy.resolve(preference, 600, false))
        }
    }

    @Test fun splitScreenFollowsWindowEvenWhenNarrow() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, PlaybackOrientationPolicy.resolve("LANDSCAPE", 360, true))
    }

    @Test fun phoneRetainsExplicitPlaybackPreference() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, PlaybackOrientationPolicy.resolve("LANDSCAPE", 599, false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, PlaybackOrientationPolicy.resolve("PORTRAIT", 360, false))
    }

    @Test fun autoFollowsSystemOnPhone() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, PlaybackOrientationPolicy.resolve("AUTO", 360, false))
    }
}
