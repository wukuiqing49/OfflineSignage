package com.wkq.localsignage.analytics

import android.app.Application
import android.content.Context
import android.os.Looper
import android.util.Base64
import com.wkq.google.commercial.GoogleCommercialConfig
import com.wkq.google.commercial.GoogleCommercialManager
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DevHubConsentTest {
    @Test fun consentIsRequiredAndWithdrawalClearsPendingActivity() {
        val app = RuntimeEnvironment.getApplication()
        val manager = GoogleCommercialManager
        val apiUrl = "https://devhub-test.invalid"
        val consent = app.getSharedPreferences("local_signage_analytics", Context.MODE_PRIVATE)
        val scope = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                "$apiUrl:${app.packageName}:local_signage".toByteArray(Charsets.UTF_8)
            ), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        ).take(24)
        val sdkState = app.getSharedPreferences("devhub_$scope", Context.MODE_PRIVATE)

        manager.initialize(app, GoogleCommercialConfig(
            enabled = true, apiUrl = apiUrl, appKey = "test-app-key"
        ))
        assertTrue(manager.isConfigured())
        assertFalse(manager.isCollectionEnabled())
        repeat(24) { manager.reportActive() }
        assertFalse(sdkState.contains("install_id"))
        assertFalse(sdkState.contains("active_pending"))

        assertTrue(updateConsent(true))
        assertTrue(manager.isCollectionEnabled())
        assertTrue(consent.getBoolean("collection_consent_v1", false))

        assertTrue(updateConsent(false))
        assertFalse(manager.isCollectionEnabled())
        assertFalse(consent.getBoolean("collection_consent_v1", true))
        assertFalse(sdkState.contains("active_pending"))
        assertFalse(sdkState.contains("active_day"))
        repeat(24) { manager.reportActive() }
        assertFalse(sdkState.contains("active_pending"))
    }

    private fun updateConsent(enabled: Boolean): Boolean {
        val result = AtomicReference<Boolean>()
        GoogleCommercialManager.setCollectionEnabled(enabled) { result.set(it) }
        // 这里验证异步授权结果与清队列，不是 SDK 的 5 秒性能 SLA。
        // 冷启动或并行构建负载下给后台线程足够调度时间，仍保留有界等待和全部状态断言。
        val deadline = System.nanoTime() + 30_000_000_000L
        while (result.get() == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertNotNull("SDK consent callback did not complete within 30 seconds (enabled=$enabled)", result.get())
        return result.get()
    }
}
