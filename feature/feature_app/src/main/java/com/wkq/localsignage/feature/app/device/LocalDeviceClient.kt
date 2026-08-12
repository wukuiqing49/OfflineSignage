package com.wkq.localsignage.feature.app.device

import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Small blocking client used from IO dispatchers for paired signage devices. */
class LocalDeviceClient(private val device: PairedDevice) {
    fun resourceExists(hash: String): RemoteResourceResult {
        val response = request("GET", "/api/resources/${urlEncode(hash)}/exists")
        if (response.status !in 200..299) return RemoteResourceResult(false, null, response.status)
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return RemoteResourceResult(false, null, response.status)
        return RemoteResourceResult(json.optBoolean("exists"), json.optString("resourceId").takeIf { it.isNotBlank() }, response.status)
    }

    fun status(): RemoteStatusResult {
        val response = request("GET", "/api/status")
        if (response.status !in 200..299) return RemoteStatusResult(response.status, 0L)
        val revision = runCatching { JSONObject(response.body).optLong("commandRevision", 0L) }.getOrDefault(0L)
        return RemoteStatusResult(response.status, revision)
    }

    fun upload(resource: SignageResource, file: File): RemoteResourceResult {
        if (!file.isFile) return RemoteResourceResult(false, null, 404)
        val boundary = "----LocalSignage${System.currentTimeMillis()}"
        val connection = open("POST", "/api/resources/upload")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        val safeName = resource.name.replace("\"", "_").replace("\r", "_").replace("\n", "_")
        return runCatching {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n".toByteArray(StandardCharsets.UTF_8))
                output.write("Content-Type: ${resource.mimeType}\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                file.inputStream().use { it.copyTo(output) }
                output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            val response = read(connection)
            val json = runCatching { JSONObject(response.body) }.getOrNull()
            RemoteResourceResult(response.status in 200..299, json?.optString("id")?.takeIf { it.isNotBlank() }, response.status)
        }.getOrElse { RemoteResourceResult(false, null, -1) }
    }

    fun command(action: String, resourceId: String? = null, value: Int? = null, revision: Long? = null): RemoteCommandResult {
        return command(action, resourceId, null, value, revision)
    }

    fun command(action: String, resourceId: String? = null, playlistId: String? = null, value: Int? = null, revision: Long? = null): RemoteCommandResult {
        val body = JSONObject().apply {
            put("action", action)
            resourceId?.let { put("resourceId", it) }
            playlistId?.let { put("playlistId", it) }
            value?.let { put("value", it) }
            revision?.let { put("revision", it) }
        }
        val response = request("POST", "/api/control", body.toString())
        return RemoteCommandResult(response.status in 200..299, response.status, response.body)
    }

    fun saveScene(scene: SignageScene, resourceId: String): Boolean {
        val body = JSONObject().apply {
            put("id", scene.id)
            put("name", scene.name)
            put("resourceId", resourceId)
            put("fitMode", scene.fitMode)
            put("cropGravity", scene.cropGravity)
            put("backgroundType", scene.backgroundType)
            scene.backgroundColor?.let { put("backgroundColor", it) }
            scene.volume?.let { put("volume", it) }
            put("muted", scene.muted)
        }
        return postJson("/api/internal/sync/scene", body).status in 200..299
    }

    fun savePlaylist(playlist: SignagePlaylist): Boolean {
        val items = JSONArray().apply {
            playlist.items.forEach { item ->
                put(JSONObject().apply {
                    put("sceneId", item.sceneId)
                    item.durationMs?.let { put("durationMs", it) }
                    put("enabled", item.enabled)
                })
            }
        }
        val body = JSONObject().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("loop", playlist.loop)
            put("items", items)
        }
        return postJson("/api/internal/sync/playlist", body).status in 200..299
    }

    private fun request(method: String, path: String, body: String? = null): HttpResponse {
        val connection = open(method, path)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        return runCatching { read(connection) }.getOrElse { HttpResponse(-1, "") }
    }

    private fun postJson(path: String, body: JSONObject): HttpResponse = request("POST", path, body.toString())

    private fun open(method: String, path: String): HttpURLConnection {
        val connection = URL("http://${hostForUrl()}:${device.port}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Local-Signage-Device-Token", device.token)
        return connection
    }

    private fun read(connection: HttpURLConnection): HttpResponse {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(status, body)
    }

    private fun hostForUrl(): String = if (device.host.contains(":") && !device.host.startsWith("[")) "[${device.host}]" else device.host
    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    data class RemoteResourceResult(val exists: Boolean, val resourceId: String?, val status: Int)
    data class RemoteStatusResult(val status: Int, val commandRevision: Long)
    data class RemoteCommandResult(val success: Boolean, val status: Int, val body: String)
    private data class HttpResponse(val status: Int, val body: String)

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}
