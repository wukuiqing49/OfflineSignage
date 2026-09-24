package com.wkq.localsignage.feature.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.wkq.localsignage.feature.app.model.SceneLayoutTemplate
import com.wkq.localsignage.feature.app.model.SignageScene
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalDeviceClientContractTest {
    @Test
    fun readsResourceIdFromBatchUploadResponse() {
        assertEquals("resource-1", uploadedResourceId("{\"ids\":[\"resource-1\"],\"playlistId\":null}"))
    }

    @Test
    fun keepsCompatibilityWithSingleResourceResponse() {
        assertEquals("resource-1", uploadedResourceId("{\"id\":\"resource-1\"}"))
    }

    @Test
    fun rejectsMissingOrInvalidResourceId() {
        assertNull(uploadedResourceId("{\"ids\":[]}"))
        assertNull(uploadedResourceId("not-json"))
    }

    @Test
    fun doesNotReuseNullIdWhenRemoteResourceIsMissing() {
        assertNull(existingResourceId("{\"exists\":false,\"resourceId\":null}"))
    }

    @Test
    fun reusesOnlyAnExistingRemoteResourceId() {
        assertEquals("resource-1", existingResourceId("{\"exists\":true,\"resourceId\":\"resource-1\"}"))
        assertNull(existingResourceId("{\"exists\":true,\"resourceId\":null}"))
    }

    @Test
    fun sceneSyncUsesMappedRemoteIdsForBothRegions() {
        val scene = SignageScene(
            id = "scene", name = "Promotion", resourceId = "local-main",
            layoutTemplate = SceneLayoutTemplate.MAIN_WITH_SIDEBAR, sidebarResourceId = "local-side"
        )
        val payload = sceneWritePayload(scene, "remote-main", "remote-side")
        assertEquals("remote-main", payload.getString("resourceId"))
        assertEquals("remote-side", payload.getString("sidebarResourceId"))
        assertEquals(SceneLayoutTemplate.MAIN_WITH_SIDEBAR, payload.getString("layoutTemplate"))
    }
}
