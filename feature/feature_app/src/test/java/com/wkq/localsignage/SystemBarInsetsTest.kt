package com.wkq.localsignage

import android.app.Application
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SystemBarInsetsTest {
    private fun insets(ime: Int = 0) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 48, 32))
        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(60, 0, 0, 0))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
        .build()

    @Test fun toolbarAddsSafeAreaOnceAndPreservesOriginalPadding() {
        val view = View(RuntimeEnvironment.getApplication())
        view.setPadding(2, 3, 4, 5)
        view.applySystemBarPadding(top = true, horizontal = true)
        repeat(2) { ViewCompat.dispatchApplyWindowInsets(view, insets()) }
        assertEquals(62, view.paddingLeft)
        assertEquals(27, view.paddingTop)
        assertEquals(52, view.paddingRight)
        assertEquals(5, view.paddingBottom)
    }

    @Test fun contentUsesLargerKeyboardInsetAndRestoresWhenHidden() {
        val view = View(RuntimeEnvironment.getApplication())
        view.setPadding(0, 7, 0, 9)
        view.applySystemBarPadding(bottom = true, horizontal = true)
        ViewCompat.dispatchApplyWindowInsets(view, insets(300))
        assertEquals(309, view.paddingBottom)
        assertEquals(7, view.paddingTop)
        ViewCompat.dispatchApplyWindowInsets(view, insets())
        assertEquals(41, view.paddingBottom)
    }
}
