package com.wkq.localsignage.feature.app.player

import android.app.Application
import android.os.Looper
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackCommandIntegrationTest {
    @Test fun timedOutQueuedCommandDoesNotExecuteAfterMainThreadRecovers() {
        val context = RuntimeEnvironment.getApplication()
        SignageRuntime.initialize(context)
        SignagePlaybackController.initialize(context)
        SignageRuntime.setError("unchanged")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Boolean> { SignagePlaybackController.applyCommand("PAUSE") }
            assertFalse(result.get(6, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            // PAUSE 若在超时后执行，会在命令入口清掉此错误。
            assertEquals("unchanged", SignageRuntime.state().error)
        } finally {
            executor.shutdownNow()
            SignagePlaybackController.release()
        }
    }
}
