package com.wkq.localsignage.feature.app.server

import android.app.Application
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class KtorSignageServerIntegrationTest {
    @Test fun realHttpEnforcesPairingSessionsTakeoverAndUploadDeduplication() {
        val context = RuntimeEnvironment.getApplication()
        SignageRuntime.initialize(context)
        val port = ServerSocket(0).use { it.localPort }
        val server = KtorSignageServer(context, port)
        server.start()
        try {
            fun request(path: String, method: String = "GET", body: String? = null,
                        token: String? = null, session: String? = null,
                        contentType: String = "application/json", range: String? = null): Pair<Int, String> {
                val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.requestMethod = method
                    token?.let { connection.setRequestProperty("X-Local-Signage-Token", it) }
                    session?.let { connection.setRequestProperty("X-Control-Session", it) }
                    range?.let { connection.setRequestProperty("Range", it) }
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", contentType)
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = connection.responseCode
                    val input = if (code < 400) connection.inputStream else connection.errorStream
                    return code to input?.bufferedReader()?.use { it.readText() }.orEmpty()
                } finally { connection.disconnect() }
            }

            assertEquals(401, request("/api/resources").first)
            val pairing = SignageRuntime.issuePairingToken()
            val credential = JSONObject().put("pairingToken", pairing.token).toString()
            val exchange = request("/api/access/exchange", "POST", credential)
            assertEquals(200, exchange.first)
            val token = JSONObject(exchange.second).getString("accessToken")
            assertEquals(401, request("/api/access/exchange", "POST", credential).first)
            assertEquals(200, request("/api/resources", token = token).first)
            assertEquals(409, request("/api/settings", "POST", "{}", token).first)

            val first = request("/api/control/session/acquire", "POST", "{\"clientName\":\"first\"}", token)
            assertEquals(201, first.first)
            val firstSession = JSONObject(first.second).getString("sessionId")
            assertEquals(409, request("/api/control/session/acquire", "POST", "{}", token).first)
            val takeover = request("/api/control/session/acquire", "POST", "{\"takeover\":true}", token)
            assertEquals(201, takeover.first)
            val session = JSONObject(takeover.second).getString("sessionId")
            assertEquals(409, request("/api/control/session/heartbeat", "POST", "{}", token, firstSession).first)

            val boundary = "test-signage-boundary"
            val multipart = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\nfixture\r\n--$boundary--\r\n"
            val upload = request("/api/resources/upload?play=false", "POST", multipart, token, session, "multipart/form-data; boundary=$boundary")
            assertEquals(upload.second, 201, upload.first)
            val duplicate = request("/api/resources/upload?play=false", "POST", multipart, token, session, "multipart/form-data; boundary=$boundary")
            assertEquals(201, duplicate.first)
            assertEquals(JSONObject(upload.second).getJSONArray("ids").getString(0), JSONObject(duplicate.second).getJSONArray("ids").getString(0))
            assertEquals(1, SignageRuntime.resources().size)
            val mediaPath = "/media/" + JSONObject(upload.second).getJSONArray("ids").getString(0)
            assertEquals(206 to "fix", request(mediaPath, token = token, range = "bytes=0-2"))
            assertEquals(416, request(mediaPath, token = token, range = "bytes=1000-2000").first)
            assertEquals(401, request(mediaPath, range = "bytes=0-2").first)
            val received = CompletableFuture<Unit>()
            val closed = CompletableFuture<Int>()
            val client = OkHttpClient()
            val socket = client.newWebSocket(
                Request.Builder().url("ws://127.0.0.1:$port/ws?token=$token").build(),
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) { received.complete(Unit) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(code)
                        webSocket.close(code, reason)
                    }
                    override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                        received.completeExceptionally(error)
                        closed.completeExceptionally(error)
                    }
                }
            )
            try {
                received.get(5, TimeUnit.SECONDS)
                assertEquals(200, request("/api/access/revoke", "POST", "{}", token).first)
                assertEquals(1008, closed.get(5, TimeUnit.SECONDS).toInt())
            } finally {
                socket.cancel()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
            assertEquals(401, request("/api/resources", token = token).first)
        } finally { server.stop() }
    }
}
