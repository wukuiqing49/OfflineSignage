package com.wkq.localsignage.feature.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
