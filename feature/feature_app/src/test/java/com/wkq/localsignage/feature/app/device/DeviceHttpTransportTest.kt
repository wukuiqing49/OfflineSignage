package com.wkq.localsignage.feature.app.device

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class DeviceHttpTransportTest {
    private class Connection(var status: Int = 200) : HttpURLConnection(URL("http://192.168.1.2/")) {
        var disconnected = false
        var failWrite = false
        var response: InputStream = ByteArrayInputStream("{}".toByteArray())
        val sent = ByteArrayOutputStream()
        val streamingChunk: Int get() = chunkLength
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = response
        override fun getErrorStream() = response
        override fun getOutputStream(): OutputStream {
            if (failWrite) throw IOException("broken connection")
            return sent
        }
    }

    @Test fun uploadStreamsWithoutBufferingAndDoesNotFollowRedirects() {
        val connection = Connection(302)
        val response = DeviceHttpTransport { _, _ -> connection }.execute("POST", "/upload", "video/mp4") {
            it.write(byteArrayOf(1, 2, 3))
        }
        assertEquals(302, response.status)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.streamingChunk > 0)
        assertTrue(connection.disconnected)
    }

    @Test fun failedOutputAlwaysDisconnects() {
        val connection = Connection().apply { failWrite = true }
        val response = DeviceHttpTransport { _, _ -> connection }.request("POST", "/command", "{}")
        assertEquals(-1, response.status)
        assertTrue(connection.disconnected)
    }

    @Test fun oversizedResponseIsRejectedAndDisconnected() {
        val connection = Connection().apply {
            response = ByteArrayInputStream(ByteArray(DeviceHttpTransport.MAX_RESPONSE_BYTES + 1))
        }
        assertEquals(-1, DeviceHttpTransport { _, _ -> connection }.request("GET", "/status").status)
        assertTrue(connection.disconnected)
    }

    @Test fun connectionFailureBecomesExplicitOfflineResult() {
        assertEquals(-1, DeviceHttpTransport { _, _ -> throw IOException("offline") }.request("GET", "/status").status)
    }
}
