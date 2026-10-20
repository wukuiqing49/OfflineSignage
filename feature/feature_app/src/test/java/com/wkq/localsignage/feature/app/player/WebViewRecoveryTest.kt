package com.wkq.localsignage.feature.app.player

import android.app.Application
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WebViewRecoveryTest {
    private fun views(): SignagePlaybackViews {
        val context = RuntimeEnvironment.getApplication()
        return SignagePlaybackViews(
            PlayerView(context), ViewPager2(context), WebView(context), TextView(context),
            FrameLayout(context), ImageView(context), FrameLayout(context)
        )
    }

    @Test fun replacesFailedInstanceWithoutChangingLayoutPosition() {
        val owner = views()
        val failed = owner.webView
        failed.id = 123
        failed.visibility = View.GONE
        val parent = FrameLayout(failed.context)
        parent.addView(View(failed.context))
        parent.addView(failed, FrameLayout.LayoutParams(320, 180))
        assertTrue(replaceFailedWebView(owner, failed))
        assertNotSame(failed, owner.webView)
        assertSame(owner.webView, parent.getChildAt(1))
        assertNull(failed.parent)
        assertEquals(123, owner.webView.id)
        assertEquals(320, owner.webView.layoutParams.width)
        assertEquals(View.GONE, owner.webView.visibility)
        assertFalse(replaceFailedWebView(owner, failed))
        owner.webView.destroy()
    }

    @Test fun releasedPageIsNotRecreatedByLateCallback() {
        val owner = views()
        val parent = FrameLayout(owner.webView.context)
        parent.addView(owner.webView)
        owner.released = true
        assertFalse(replaceFailedWebView(owner, owner.webView))
        assertEquals(1, parent.childCount)
        owner.webView.destroy()
    }

    @Test fun detachClearsOwnerAndIsIdempotent() {
        val owner = views()
        val field = SignagePlaybackController::class.java.getDeclaredField("views").apply { isAccessible = true }
        field.set(SignagePlaybackController, owner)
        SignagePlaybackController.detach(owner)
        assertNull(field.get(SignagePlaybackController))
        assertTrue(owner.released)
        SignagePlaybackController.detach(owner)
        assertNull(field.get(SignagePlaybackController))
    }
}
