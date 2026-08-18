package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageTransitionPolicyTest {
    @Test
    fun normalize_acceptsSupportedEffectsIgnoringCaseAndWhitespace() {
        assertEquals(ImageTransitionPolicy.NONE, ImageTransitionPolicy.normalize(" none "))
        assertEquals(ImageTransitionPolicy.FADE, ImageTransitionPolicy.normalize("fade"))
        assertEquals(ImageTransitionPolicy.SLIDE, ImageTransitionPolicy.normalize("Slide"))
        assertEquals(ImageTransitionPolicy.ZOOM, ImageTransitionPolicy.normalize(" zoom "))
    }

    @Test
    fun normalize_fallsBackToFadeForMissingOrUnknownEffects() {
        assertEquals(ImageTransitionPolicy.DEFAULT_EFFECT, ImageTransitionPolicy.normalize(null))
        assertEquals(ImageTransitionPolicy.DEFAULT_EFFECT, ImageTransitionPolicy.normalize(""))
        assertEquals(ImageTransitionPolicy.DEFAULT_EFFECT, ImageTransitionPolicy.normalize("spin"))
    }
}
