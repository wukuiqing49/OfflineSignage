package com.wkq.localsignage.feature.app.device

import com.google.gson.JsonParser
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
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
        if (response.status !in 200..299) return RemoteStatusResult(response.status)
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return RemoteStatusResult(response.status)
        return RemoteStatusResult(
            status = response.status,
            deviceId = json.optString("deviceId").takeIf { it.isNotBlank() },
            deviceName = json.optString("deviceName").takeIf { it.isNotBlank() },
            currentResourceId = json.optString("currentResourceId").takeIf { it.isNotBlank() },
            currentSceneId = json.optString("currentSceneId").takeIf { it.isNotBlank() },
            currentPlaylistId = json.optString("currentPlaylistId").takeIf { it.isNotBlank() },
            playing = json.optBoolean("playing"),
            volume = json.optInt("volume", 80),
            muted = json.optBoolean("muted"),
            error = json.optString("error").takeIf { it.isNotBlank() },
            commandRevision = json.optLong("commandRevision", 0L),
            currentResourceName = json.optString("currentResourceName").takeIf { it.isNotBlank() },
            currentResourceKind = json.optString("currentResourceKind").takeIf { it.isNotBlank() },
            currentResourceContent = json.optString("currentResourceContent").takeIf { it.isNotBlank() },
            currentResourceSourceUri = json.optString("currentResourceSourceUri").takeIf { it.isNotBlank() },
            currentResourceMimeType = json.optString("currentResourceMimeType").takeIf { it.isNotBlank() },
            currentResourceTextSizeSp = if (json.has("currentResourceTextSizeSp") && !json.isNull("currentResourceTextSizeSp")) json.optInt("currentResourceTextSizeSp") else null,
            currentResourceTextColor = json.optString("currentResourceTextColor").takeIf { it.isNotBlank() },
            currentResourceTextBackgroundColor = json.optString("currentResourceTextBackgroundColor").takeIf { it.isNotBlank() },
            currentResourceFontFamily = json.optString("currentResourceFontFamily").takeIf { it.isNotBlank() },
            currentResourceUrl = json.optString("currentResourceUrl").takeIf { it.isNotBlank() }
        )
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
            RemoteResourceResult(
                response.status in 200..299,
                uploadedResourceId(response.body),
                response.status
            )
        }.getOrElse { RemoteResourceResult(false, null, -1) }
    }

    fun exchangePairingCredential(): RemotePairingResult {
        val response = postJson("/api/device/pair", JSONObject().apply {
            put("pairingCredential", device.token)
        })
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val deviceToken = json?.optString("deviceToken")?.takeIf { it.isNotBlank() }
        return RemotePairingResult(
            success = response.status in 200..299 && deviceToken != null,
            status = response.status,
            deviceId = json?.optString("deviceId")?.takeIf { it.isNotBlank() },
            deviceName = json?.optString("deviceName")?.takeIf { it.isNotBlank() },
            deviceToken = deviceToken
        )
    }

    fun saveVirtualResource(resource: SignageResource): RemoteResourceResult {
        val response = postJson("/api/internal/sync/resource", JSONObject().apply {
            put("name", resource.name); put("kind", resource.kind)
            resource.sourceUri?.let { put("sourceUri", it) }
            resource.content?.let { put("content", it) }
            resource.refreshIntervalMs?.let { put("refreshIntervalMs", it) }
            put("textSizeSp", resource.textSizeSp)
            put("textColor", resource.textColor)
            put("textBackgroundColor", resource.textBackgroundColor)
            put("fontFamily", resource.fontFamily)
            put("textSpeedDpPerSecond", resource.textSpeedDpPerSecond)
            put("textRepeatCount", resource.textRepeatCount)
        })
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        return RemoteResourceResult(response.status in 200..299, json?.optString("id")?.takeIf { it.isNotBlank() }, response.status)
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
            put("playbackSpeed", scene.playbackSpeed.toDouble())
            put("transitionEffect", scene.transitionEffect)
            put("overlays", JSONArray().apply { scene.overlays.forEach { overlay -> put(JSONObject().apply {
                put("id", overlay.id); put("type", overlay.type); put("content", overlay.content)
                put("horizontalPosition", overlay.horizontalPosition); put("verticalPosition", overlay.verticalPosition)
                put("textSizeSp", overlay.textSizeSp); put("textColor", overlay.textColor); put("backgroundColor", overlay.backgroundColor)
                put("paddingDp", overlay.paddingDp); put("cornerRadiusDp", overlay.cornerRadiusDp); put("fontFamily", overlay.fontFamily)
                put("speedDpPerSecond", overlay.speedDpPerSecond)
                put("enabled", overlay.enabled); put("zIndex", overlay.zIndex)
            }) } })
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
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        return runCatching { read(connection) }.getOrElse { HttpResponse(-1, "") }
    }

    private fun postJson(path: String, body: JSONObject): HttpResponse = request("POST", path, body.toString())

    private fun open(method: String, path: String): HttpURLConnection {
        require(isPrivateIpv4Host(device.host)) { "DEVICE_HOST_NOT_LOCAL" }
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

    private fun isPrivateIpv4Host(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).let { addresses ->
            addresses.isNotEmpty() && addresses.all { address ->
                address is Inet4Address && address.isSiteLocalAddress &&
                    !address.isLoopbackAddress && !address.isLinkLocalAddress
            }
        }
    }.getOrDefault(false)

    data class RemoteResourceResult(val exists: Boolean, val resourceId: String?, val status: Int)
    data class RemotePairingResult(
        val success: Boolean,
        val status: Int,
        val deviceId: String? = null,
        val deviceName: String? = null,
        val deviceToken: String? = null
    )
    data class RemoteStatusResult(
        val status: Int,
        val deviceId: String? = null,
        val deviceName: String? = null,
        val currentResourceId: String? = null,
        val currentSceneId: String? = null,
        val currentPlaylistId: String? = null,
        val playing: Boolean = false,
        val volume: Int = 80,
        val muted: Boolean = false,
        val error: String? = null,
        val commandRevision: Long = 0L,
        val currentResourceName: String? = null,
        val currentResourceKind: String? = null,
        val currentResourceContent: String? = null,
        val currentResourceSourceUri: String? = null,
        val currentResourceMimeType: String? = null,
        val currentResourceTextSizeSp: Int? = null,
        val currentResourceTextColor: String? = null,
        val currentResourceTextBackgroundColor: String? = null,
        val currentResourceFontFamily: String? = null,
        val currentResourceUrl: String? = null
    )
    data class RemoteCommandResult(val success: Boolean, val status: Int, val body: String)
    private data class HttpResponse(val status: Int, val body: String)

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

internal fun uploadedResourceId(responseBody: String): String? = runCatching {
    val json = JsonParser.parseString(responseBody).asJsonObject
    json.get("id")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.takeIf { it.isNotBlank() }
        ?: json.getAsJsonArray("ids")
            ?.firstOrNull()
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf { it.isNotBlank() }
}.getOrNull()
