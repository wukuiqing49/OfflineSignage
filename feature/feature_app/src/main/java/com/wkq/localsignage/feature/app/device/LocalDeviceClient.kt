package com.wkq.localsignage.feature.app.device

import com.google.gson.JsonParser
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.PlaylistSchedule
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
    private val transport = DeviceHttpTransport(::open)
    fun resourceExists(hash: String): RemoteResourceResult {
        val response = request("GET", "/api/resources/${urlEncode(hash)}/exists")
        if (response.status !in 200..299) return RemoteResourceResult(false, null, response.status)
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return RemoteResourceResult(false, null, response.status)
        val exists = json.optBoolean("exists")
        return RemoteResourceResult(exists, existingResourceId(response.body), response.status)
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
        val safeName = resource.name.replace("\"", "_").replace("\r", "_").replace("\n", "_")
        val response = transport.execute("POST", "/api/resources/upload", "multipart/form-data; boundary=$boundary") { output ->
            output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write("Content-Type: ${resource.mimeType}\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            file.inputStream().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        return RemoteResourceResult(response.status in 200..299, uploadedResourceId(response.body), response.status)
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

    fun saveScene(scene: SignageScene, resourceId: String): Int {
        return saveSceneResult(scene, resourceId).status
    }

    fun saveSceneResult(scene: SignageScene, resourceId: String, sidebarResourceId: String? = null): RemoteWriteResult {
        val body = sceneWritePayload(scene, resourceId, sidebarResourceId)
        return writeResult(postJson("/api/internal/sync/scene", body))
    }

    fun savePlaylist(playlist: SignagePlaylist): Int {
        return savePlaylistResult(playlist).status
    }

    fun savePlaylistResult(playlist: SignagePlaylist): RemoteWriteResult {
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
        return writeResult(postJson("/api/internal/sync/playlist", body))
    }

    fun replacePlaylistSchedules(schedules: Collection<PlaylistSchedule>): Int {
        val body = JSONObject().put("schedules", JSONArray().apply {
            schedules.forEach { schedule -> put(JSONObject().apply {
                put("id", schedule.id); put("playlistId", schedule.playlistId)
                put("weekdays", JSONArray(schedule.weekdays.sorted()))
                put("startMinute", schedule.startMinute); put("endMinute", schedule.endMinute)
                put("priority", schedule.priority); put("enabled", schedule.enabled)
            }) }
        })
        return postJson("/api/internal/sync/schedules", body).status
    }

    private fun request(method: String, path: String, body: String? = null) = transport.request(method, path, body)

    private fun postJson(path: String, body: JSONObject) = request("POST", path, body.toString())

    private fun writeResult(response: DeviceHttpTransport.Response): RemoteWriteResult {
        val errorCode = runCatching { JSONObject(response.body).optJSONObject("error")?.optString("code") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return RemoteWriteResult(response.status, errorCode)
    }

    private fun open(method: String, path: String): HttpURLConnection {
        require(device.port in 1..65535) { "DEVICE_PORT_INVALID" }
        val addresses = InetAddress.getAllByName(device.host)
        require(addresses.isNotEmpty() && addresses.all {
            it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress
        }) { "DEVICE_HOST_NOT_LOCAL" }
        // 使用本次已验证的地址连接，避免再次解析主机名时改变网络边界。
        val connection = URL("http://${addresses.first().hostAddress}:${device.port}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Local-Signage-Device-Token", device.token)
        return connection
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    data class RemoteResourceResult(val exists: Boolean, val resourceId: String?, val status: Int)
    data class RemoteWriteResult(val status: Int, val errorCode: String? = null)
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

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

internal fun sceneWritePayload(scene: SignageScene, resourceId: String, sidebarResourceId: String?): JSONObject = JSONObject().apply {
    put("id", scene.id)
    put("name", scene.name)
    put("resourceId", resourceId)
    put("layoutTemplate", scene.layoutTemplate)
    if (sidebarResourceId == null) put("sidebarResourceId", JSONObject.NULL) else put("sidebarResourceId", sidebarResourceId)
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

/** Only an explicit positive lookup result may reuse a remote resource ID. */
internal fun existingResourceId(responseBody: String): String? = runCatching {
    val json = JsonParser.parseString(responseBody).asJsonObject
    if (!json.get("exists").asBoolean) return@runCatching null
    json.get("resourceId")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.takeIf { it.isNotBlank() }
}.getOrNull()
