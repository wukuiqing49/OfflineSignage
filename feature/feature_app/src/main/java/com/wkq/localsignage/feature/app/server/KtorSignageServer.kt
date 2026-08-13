package com.wkq.localsignage.feature.app.server

import android.content.Context
import android.util.Base64
import android.os.Build
import android.os.SystemClock
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignageSettings
import com.wkq.localsignage.feature.app.model.ResourceKind
import com.wkq.localsignage.feature.app.model.SignageOverlay
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.device.SignageDeviceFleet
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.discovery.LocalDeviceDiscovery
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.storage.TemporaryPairingToken
import com.wkq.localsignage.feature.app.security.CommandRequestFingerprint
import com.wkq.localsignage.feature.app.R
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.websocket.WebSockets
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArraySet

class KtorSignageServer(context: Context, private val port: Int) {
    private val applicationContext = context.applicationContext
    private var engine: ApplicationEngine? = null
    private var broadcastScope: CoroutineScope? = null
    private val webSocketSessions = CopyOnWriteArraySet<WebSocketSession>()
    private val commandMutex = Mutex()
    private val stateListener: () -> Unit = { broadcastState() }

    fun start() {
        if (engine != null) return
        broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SignageRuntime.registerStateListener(stateListener)
        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                get("/") {
                    val pairingToken = call.request.queryParameters["pairingToken"]
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                    call.response.headers.append("Content-Security-Policy", WEB_CONTENT_SECURITY_POLICY)
                    call.response.headers.append("Referrer-Policy", "no-referrer")
                    call.response.headers.append("X-Content-Type-Options", "nosniff")
                    val html = webConsoleTemplate
                        .replace("__CONTROL_TOKEN__", quote(""))
                        .replace("__PAIRING_TOKEN__", quote(pairingToken.orEmpty()))
                    call.respondText(html, ContentType.Text.Html)
                }
                webSocket("/ws") {
                    val token = call.request.queryParameters["token"] ?: call.request.headers["X-Local-Signage-Token"]
                    if (!SignageRuntime.hasWebAccessToken(token)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }
                    webSocketSessions += this
                    try {
                        send(Frame.Text(deviceStatusEventJson()))
                        for (frame in incoming) {
                            if (frame is Frame.Text && frame.readText() == "PING") {
                                send(Frame.Text("{\"type\":\"PONG\"}"))
                            }
                        }
                    } finally {
                        webSocketSessions -= this
                    }
                }
                get("/api/device") { call.respondJson(deviceJson()) }
                get("/api/pairing") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondPairing(SignageRuntime.pairingToken())
                }
                get("/api/pairing/qr") {
                    if (!call.authorized(requireSession = false)) return@get
                    val code = SignageRuntime.pairingCode()
                    val qr = code.qrBitmap ?: run {
                        call.respondJson(errorJson("LOCAL_NETWORK_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    call.respondBytes(qrPng(qr), ContentType.Image.PNG)
                }
                post("/api/pairing/rotate") {
                    if (!call.authorized(requireSession = false)) return@post
                    call.respondPairing(SignageRuntime.issuePairingToken())
                }
                post("/api/pairing/revoke") {
                    if (!call.authorized(requireSession = false)) return@post
                    SignageRuntime.revokePairingToken()
                    call.respondJson("{\"revoked\":true}")
                }
                post("/api/access/rotate") {
                    if (!call.authorized(requireSession = false)) return@post
                    call.respondJson("{\"accessToken\":${quote(SignageRuntime.rotateWebAccessToken())},\"expiresInMs\":$WEB_ACCESS_TOKEN_TTL_MS}")
                }
                post("/api/access/exchange") {
                    val pairingToken = runCatching { JSONObject(call.receiveText()).optString("pairingToken") }.getOrDefault("")
                    if (!SignageRuntime.consumePairingToken(pairingToken)) {
                        call.respondJson(errorJson("PAIRING_TOKEN_INVALID"), HttpStatusCode.Unauthorized)
                        return@post
                    }
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                    call.respondJson(
                        "{\"accessToken\":${quote(SignageRuntime.rotateWebAccessToken())}," +
                            "\"expiresInMs\":$WEB_ACCESS_TOKEN_TTL_MS}"
                    )
                }
                post("/api/access/revoke") {
                    if (!call.authorized(requireSession = false)) return@post
                    SignageRuntime.revokeWebAccessToken()
                    call.respondJson("{\"revoked\":true}")
                }
                get("/api/devices") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(devicesJson())
                }
                get("/api/devices/paired") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(pairedDevicesJson())
                }
                get("/api/devices/status") {
                    if (!call.authorized(requireSession = false)) return@get
                    val targets = SignageRuntime.pairedDevices()
                    val statuses = withContext(Dispatchers.IO) { SignageDeviceFleet.statuses(targets) }
                    call.respondJson(fleetStatusesJson(statuses))
                }
                post("/api/devices/pair") {
                    if (!call.authorized()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val deviceId = body.optString("deviceId").takeIf { it.isNotBlank() }
                            ?: throw IllegalArgumentException("DEVICE_ID_REQUIRED")
                        val discovered = LocalDeviceDiscovery.find(deviceId)
                            ?: throw IllegalArgumentException("DEVICE_NOT_DISCOVERED")
                        val host = body.optString("host").takeIf { it.isNotBlank() }
                            ?: throw IllegalArgumentException("DEVICE_HOST_REQUIRED")
                        val port = body.optInt("port", -1)
                        require(host == discovered.host && port == discovered.port) { "DEVICE_ADDRESS_MISMATCH" }
                        val device = PairedDevice(
                            deviceId = deviceId,
                            deviceName = body.optString("deviceName").ifBlank { discovered.deviceName },
                            host = host,
                            port = port,
                            token = body.optString("token").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("DEVICE_TOKEN_REQUIRED"),
                            pairedAt = System.currentTimeMillis()
                        )
                        call.respondJson(pairedDeviceJson(SignageRuntime.savePairedDevice(device)), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_DEVICE"), HttpStatusCode.BadRequest)
                    }
                }
                delete("/api/devices/paired/{id}") {
                    if (!call.authorized()) return@delete
                    val deleted = SignageRuntime.deletePairedDevice(call.parameters["id"].orEmpty())
                    call.respondJson("{\"deleted\":$deleted}", if (deleted) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
                get("/api/status") {
                    if (!call.authorizedOrDevice(requireSession = false)) return@get
                    call.respondJson(statusJson())
                }
                get("/api/resources") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(resourcesJson())
                }
                get("/api/resources/{hash}/exists") {
                    if (!call.authorizedOrDevice(requireSession = false)) return@get
                    val hash = call.parameters["hash"].orEmpty().lowercase()
                    if (!hash.matches(SHA256_PATTERN)) {
                        call.respondJson(errorJson("INVALID_HASH"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val resource = SignageRuntime.resourceByHash(hash)
                    call.respondJson("{\"exists\":${resource != null},\"hash\":${quote(hash)},\"resourceId\":${resource?.id?.let(::quote) ?: "null"}}")
                }
                get("/api/scenes") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(scenesJson())
                }
                get("/api/playlists") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(playlistsJson())
                }
                get("/api/settings") {
                    if (!call.authorized()) return@get
                    call.respondJson(settingsJson())
                }
                post("/api/settings") {
                    if (!call.authorized()) return@post
                    try {
                        val body = call.receiveText()
                        val settings = SignageSettings(
                            fallbackSceneId = jsonString(body, "fallbackSceneId"),
                            keepScreenAwake = jsonBoolean(body, "keepScreenAwake") ?: true,
                            autoResume = jsonBoolean(body, "autoResume") ?: true,
                            fullscreen = jsonBoolean(body, "fullscreen") ?: true
                        )
                        SignageRuntime.setSettings(settings)
                        call.respondJson(settingsJson())
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "INVALID_SETTINGS"), HttpStatusCode.BadRequest)
                    }
                }
                get("/api/errors") {
                    if (!call.authorized()) return@get
                    call.respondJson(errorsJson())
                }
                get("/api/diagnostics") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(diagnosticsJson())
                }
                get("/api/diagnostics/export") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=local-signage-diagnostics.txt"
                    )
                    call.respondText(diagnosticsText(), ContentType.Text.Plain)
                }
                delete("/api/errors") {
                    if (!call.authorized()) return@delete
                    SignageRuntime.clearPlaybackErrors()
                    call.respondJson("{\"cleared\":true}")
                }
                get("/api/control/session") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(controlSessionStatusJson(SignageRuntime.controlSession()))
                }
                post("/api/control/session/acquire") {
                    if (!call.authorized(requireSession = false)) return@post
                    val body = call.receiveText()
                    val session = SignageRuntime.acquireControlSession(
                        jsonString(body, "clientName") ?: "Browser",
                        jsonBoolean(body, "takeover") == true
                    )
                    if (session == null) {
                        call.respondJson(controlSessionBusyJson(SignageRuntime.controlSession()), HttpStatusCode.Conflict)
                    }
                    else call.respondJson(sessionJson(session), HttpStatusCode.Created)
                }
                post("/api/control/session/heartbeat") {
                    if (!call.authorized(requireSession = false)) return@post
                    val session = SignageRuntime.heartbeatControlSession(call.sessionId().orEmpty())
                    if (session == null) call.respondJson(errorJson("CONTROL_SESSION_INVALID"), HttpStatusCode.Conflict)
                    else call.respondJson(sessionJson(session))
                }
                post("/api/control/session/release") {
                    if (!call.authorized(requireSession = false)) return@post
                    val released = SignageRuntime.releaseControlSession(call.sessionId().orEmpty())
                    call.respondJson("{\"released\":$released}", if (released) HttpStatusCode.OK else HttpStatusCode.Conflict)
                }
                get("/media/{id}") {
                    if (!call.authorized(requireSession = false)) return@get
                    val resource = SignageRuntime.resource(call.parameters["id"])
                    val file = resource?.takeIf { it.isLocalFile }?.let(SignageRuntime::fileFor)
                    if (file?.isFile == true) call.respondFile(file)
                    else call.respondJson(errorJson("RESOURCE_NOT_FOUND"), HttpStatusCode.NotFound)
                }
                post("/api/resources/upload") {
                    val deviceRequest = call.hasDeviceToken()
                    if (!deviceRequest && !call.authorized()) return@post
                    val existingIds = SignageRuntime.resources().mapTo(mutableSetOf()) { it.id }
                    val createdIds = linkedSetOf<String>()
                    var createdPlaylistId: String? = null
                    try {
                        val multipart = call.receiveMultipart()
                        val uploadedIds = mutableListOf<String>()
                        var fileCount = 0
                        while (true) {
                            val part = multipart.readPart() ?: break
                            try {
                                if (part is PartData.FileItem) {
                                    fileCount += 1
                                    require(fileCount <= MAX_IMAGE_BATCH_SIZE) { "TOO_MANY_FILES" }
                                    val resource = SignageRuntime.saveUpload(
                                        part.originalFileName ?: "resource",
                                        part.contentType?.toString() ?: "application/octet-stream",
                                        part.provider().toInputStream()
                                    )
                                    uploadedIds += resource.id
                                    if (resource.id !in existingIds) createdIds += resource.id
                                }
                            } finally { part.dispose() }
                        }
                        if (uploadedIds.isEmpty()) call.respondJson(errorJson("FILE_REQUIRED"), HttpStatusCode.BadRequest)
                        else {
                            val resources = uploadedIds.mapNotNull(SignageRuntime::resource)
                            require(resources.size == uploadedIds.size) { "UPLOAD_INCOMPLETE" }
                            require(resources.all { it.isImage } || resources.size == 1 && resources.single().isVideo) {
                                "UPLOAD_BATCH_TYPE_INVALID"
                            }
                            val playlist = if (!deviceRequest && resources.size > 1 && resources.all { it.isImage }) {
                                SignageRuntime.createImageSlideshow("Image slideshow", uploadedIds, DEFAULT_SLIDE_DURATION_MS)
                            } else null
                            createdPlaylistId = playlist?.id
                            if (!deviceRequest) {
                                if (playlist != null) SignagePlaybackController.applyCommand("PLAY_PLAYLIST", playlistId = playlist.id)
                                else SignagePlaybackController.applyCommand("PLAY", resourceId = uploadedIds.last())
                            }
                            call.respondJson(
                                "{\"ids\":[${uploadedIds.joinToString { quote(it) }}],\"playlistId\":${playlist?.id?.let(::quote) ?: "null"}}",
                                HttpStatusCode.Created
                            )
                        }
                    } catch (error: IllegalArgumentException) {
                        createdPlaylistId?.let(SignageRuntime::deletePlaylist)
                        createdIds.forEach(SignageRuntime::deleteResource)
                        call.respondJson(errorJson(error.message ?: "INVALID_UPLOAD"), HttpStatusCode.BadRequest)
                    } catch (_: Exception) {
                        createdPlaylistId?.let(SignageRuntime::deletePlaylist)
                        createdIds.forEach(SignageRuntime::deleteResource)
                        call.respondJson(errorJson("UPLOAD_FAILED"), HttpStatusCode.InternalServerError)
                    }
                }
                post("/api/resources/link") {
                    if (!call.authorized()) return@post
                    val existingIds = SignageRuntime.resources().mapTo(mutableSetOf()) { it.id }
                    val createdIds = linkedSetOf<String>()
                    var createdPlaylistId: String? = null
                    try {
                        val body = JSONObject(call.receiveText())
                        val mediaType = body.optString("mediaType").uppercase()
                        val urls = buildList {
                            body.optJSONArray("urls")?.let { array ->
                                for (index in 0 until array.length()) {
                                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                            if (isEmpty()) body.optString("url").trim().takeIf { it.isNotBlank() }?.let(::add)
                        }.distinct()
                        require(urls.isNotEmpty()) { "REMOTE_MEDIA_URL_REQUIRED" }
                        require(mediaType == "IMAGE" || mediaType == "VIDEO") { "REMOTE_MEDIA_TYPE_INVALID" }
                        if (mediaType == "VIDEO") require(urls.size == 1) { "ONE_VIDEO_REQUIRED" }
                        if (mediaType == "IMAGE") require(urls.size <= MAX_IMAGE_BATCH_SIZE) { "TOO_MANY_IMAGES" }
                        val baseName = body.optString("name").ifBlank { mediaType.lowercase() }
                        val resources = withContext(Dispatchers.IO) {
                            urls.mapIndexed { index, url ->
                                SignageRuntime.saveRemote(
                                    url = url,
                                    name = if (urls.size == 1) baseName else "$baseName ${index + 1}"
                                ).also { resource ->
                                    if (resource.id !in existingIds) createdIds += resource.id
                                }
                            }
                        }
                        require(resources.all { if (mediaType == "IMAGE") it.isImage else it.isVideo }) {
                            "REMOTE_MEDIA_TYPE_MISMATCH"
                        }
                        val playlist = if (resources.size > 1) {
                            SignageRuntime.createImageSlideshow(baseName, resources.map { it.id }, DEFAULT_SLIDE_DURATION_MS)
                        } else null
                        createdPlaylistId = playlist?.id
                        if (playlist != null) SignagePlaybackController.applyCommand("PLAY_PLAYLIST", playlistId = playlist.id)
                        else SignagePlaybackController.applyCommand("PLAY", resourceId = resources.single().id)
                        call.respondJson(
                            "{\"ids\":[${resources.joinToString { quote(it.id) }}],\"playlistId\":${playlist?.id?.let(::quote) ?: "null"}}",
                            HttpStatusCode.Created
                        )
                    } catch (error: IllegalArgumentException) {
                        createdPlaylistId?.let(SignageRuntime::deletePlaylist)
                        createdIds.forEach(SignageRuntime::deleteResource)
                        call.respondJson(errorJson(error.message ?: "REMOTE_MEDIA_INVALID"), HttpStatusCode.BadRequest)
                    } catch (_: Exception) {
                        createdPlaylistId?.let(SignageRuntime::deletePlaylist)
                        createdIds.forEach(SignageRuntime::deleteResource)
                        call.respondJson(errorJson("REMOTE_DOWNLOAD_FAILED"), HttpStatusCode.InternalServerError)
                    }
                }
                post("/api/resources/remote") {
                    if (!call.authorized()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val url = body.optString("url").trim().takeIf { it.isNotBlank() }
                            ?: throw IllegalArgumentException("REMOTE_URL_REQUIRED")
                        val name = body.optString("name").takeIf { it.isNotBlank() }
                        val resource = withContext(Dispatchers.IO) { SignageRuntime.saveRemote(url, name) }
                        SignagePlaybackController.applyCommand("PLAY", resourceId = resource.id)
                        call.respondJson(resourceJson(resource), HttpStatusCode.Created)
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "REMOTE_DOWNLOAD_FAILED"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/resources/virtual") {
                    if (!call.authorized()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val kind = ResourceKind.valueOf(body.optString("kind").uppercase())
                        val resource = SignageRuntime.saveVirtualResource(
                            name = body.optString("name").ifBlank { kind.name.lowercase() },
                            kind = kind,
                            sourceUri = body.optString("sourceUri").takeIf { it.isNotBlank() },
                            content = body.optString("content").takeIf { it.isNotBlank() },
                            refreshIntervalMs = body.optLongOrNull("refreshIntervalMs")
                        )
                        SignagePlaybackController.applyCommand("PLAY", resourceId = resource.id)
                        call.respondJson(resourceJson(resource), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_VIRTUAL_RESOURCE"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/control") {
                    if (!call.authorizedOrDevice()) return@post
                    commandMutex.withLock {
                        try {
                            val body = call.receiveText()
                            val json = JSONObject(body)
                            val commandId = call.request.headers["X-Command-Id"]?.trim()?.takeIf { it.isNotBlank() }
                                ?: json.optString("commandId").trim().takeIf { it.isNotBlank() }
                            val fingerprint = commandFingerprint(json)
                            if (commandId != null) {
                                val previous = SignageRuntime.commandResult(commandId, fingerprint)
                                if (previous != null) {
                                    call.respondJson(previous.response, HttpStatusCode.fromValue(previous.statusCode))
                                    return@withLock
                                }
                            }
                            val action = jsonString(body, "action") ?: "TOGGLE"
                            val accepted = SignagePlaybackController.applyCommand(
                                action = action,
                                resourceId = jsonString(body, "resourceId"),
                                sceneId = jsonString(body, "sceneId"),
                                playlistId = jsonString(body, "playlistId"),
                                value = jsonInt(body, "value"),
                                revision = jsonLong(body, "revision")
                            )
                            val response = if (accepted) statusJson() else errorJson("COMMAND_REJECTED")
                            val status = if (accepted) HttpStatusCode.OK else HttpStatusCode.Conflict
                            commandId?.let {
                                SignageRuntime.saveCommandResult(
                                    com.wkq.localsignage.feature.app.storage.StoredCommandResult(
                                        commandId = it,
                                        fingerprint = fingerprint,
                                        response = response,
                                        statusCode = status.value,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            call.respondJson(response, status)
                        } catch (error: IllegalArgumentException) {
                            call.respondJson(errorJson(error.message ?: "INVALID_COMMAND"), HttpStatusCode.Conflict)
                        } catch (_: Exception) {
                            call.respondJson(errorJson("INVALID_JSON"), HttpStatusCode.BadRequest)
                        }
                    }
                }
                post("/api/internal/sync/scene") {
                    if (!call.hasDeviceToken()) return@post
                    try {
                        val body = call.receiveText()
                        val scene = SignageScene(
                            id = jsonString(body, "id") ?: throw IllegalArgumentException("SCENE_ID_REQUIRED"),
                            name = jsonString(body, "name") ?: "Scene",
                            resourceId = jsonString(body, "resourceId") ?: throw IllegalArgumentException("RESOURCE_REQUIRED"),
                            fitMode = jsonString(body, "fitMode") ?: "FIT",
                            cropGravity = jsonString(body, "cropGravity") ?: "CENTER",
                            backgroundType = jsonString(body, "backgroundType") ?: "BLACK",
                            backgroundColor = jsonString(body, "backgroundColor"),
                            volume = jsonInt(body, "volume"),
                            muted = jsonBoolean(body, "muted") ?: false,
                            overlays = overlays(JSONObject(body).optJSONArray("overlays"))
                        )
                        call.respondJson(sceneJson(SignageRuntime.saveScene(scene)), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_SCENE"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/internal/sync/resource") {
                    if (!call.hasDeviceToken()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val kind = ResourceKind.valueOf(body.optString("kind").uppercase())
                        val resource = SignageRuntime.saveVirtualResource(
                            name = body.optString("name").ifBlank { kind.name.lowercase() },
                            kind = kind,
                            sourceUri = body.optString("sourceUri").takeIf { it.isNotBlank() },
                            content = body.optString("content").takeIf { it.isNotBlank() },
                            refreshIntervalMs = body.optLongOrNull("refreshIntervalMs")
                        )
                        call.respondJson(resourceJson(resource), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_RESOURCE"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/internal/sync/playlist") {
                    if (!call.hasDeviceToken()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val items = body.optJSONArray("items") ?: JSONArray()
                        val playlist = SignagePlaylist(
                            id = body.optString("id").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("PLAYLIST_ID_REQUIRED"),
                            name = body.optString("name").ifBlank { "Playlist" },
                            items = buildList {
                                for (index in 0 until items.length()) {
                                    val item = items.optJSONObject(index) ?: continue
                                    val sceneId = item.optString("sceneId").takeIf { it.isNotBlank() } ?: continue
                                    add(SignagePlaylistItem(
                                        sceneId,
                                        if (item.has("durationMs") && !item.isNull("durationMs")) item.optLong("durationMs") else null,
                                        item.optBoolean("enabled", true)
                                    ))
                                }
                            },
                            loop = body.optBoolean("loop", true)
                        )
                        call.respondJson(playlistJson(SignageRuntime.savePlaylist(playlist)), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_PLAYLIST"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/devices/sync") {
                    if (!call.authorized()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val resource = SignageRuntime.resource(jsonString(body.toString(), "resourceId"))
                            ?: throw IllegalArgumentException("RESOURCE_NOT_FOUND")
                        val targetIds = jsonStringList(body, "deviceIds")
                        val targets = pairedTargets(targetIds)
                        if (targets.isEmpty()) throw IllegalArgumentException("NO_PAIRED_DEVICES")
                        val results = withContext(Dispatchers.IO) {
                            SignageDeviceFleet.sync(resource, resource.takeIf { it.isLocalFile }?.let(SignageRuntime::fileFor), targets)
                        }
                        call.respondJson(fleetResultsJson(results))
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_SYNC"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/devices/sync-playlist") {
                    if (!call.authorized()) return@post
                    try {
                        val body = JSONObject(call.receiveText())
                        val playlist = SignageRuntime.playlist(body.optString("playlistId"))
                            ?: throw IllegalArgumentException("PLAYLIST_NOT_FOUND")
                        val scenes = playlist.items.mapNotNull { SignageRuntime.scene(it.sceneId) }.distinctBy { it.id }
                        val resources = scenes.mapNotNull { SignageRuntime.resource(it.resourceId) }.associateBy { it.id }
                        val files = resources.values.filter { it.isLocalFile }.associate { it.id to SignageRuntime.fileFor(it) }
                        val targets = pairedTargets(jsonStringList(body, "deviceIds"))
                        if (targets.isEmpty()) throw IllegalArgumentException("NO_PAIRED_DEVICES")
                        val results = withContext(Dispatchers.IO) { SignageDeviceFleet.syncPlaylist(playlist, scenes, resources, files, targets) }
                        call.respondJson(fleetResultsJson(results))
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_PLAYLIST_SYNC"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/devices/play") { call.respondFleetCommand("PLAY") }
                post("/api/devices/play-playlist") { call.respondFleetPlaylistCommand() }
                post("/api/devices/pause") { call.respondFleetCommand("PAUSE") }
                post("/api/devices/stop") { call.respondFleetCommand("STOP") }
                post("/api/devices/volume") { call.respondFleetCommand("VOLUME", jsonValue = true) }
                post("/api/devices/mute") { call.respondFleetCommand("MUTE") }
                post("/api/scenes") {
                    if (!call.authorized()) return@post
                    try {
                        val body = call.receiveText()
                        val scene = SignageScene(
                            id = jsonString(body, "id") ?: java.util.UUID.randomUUID().toString(),
                            name = jsonString(body, "name") ?: "Scene",
                            resourceId = jsonString(body, "resourceId") ?: throw IllegalArgumentException("RESOURCE_REQUIRED"),
                            fitMode = jsonString(body, "fitMode") ?: "FIT",
                            cropGravity = jsonString(body, "cropGravity") ?: "CENTER",
                            backgroundType = jsonString(body, "backgroundType") ?: "BLACK",
                            backgroundColor = jsonString(body, "backgroundColor"),
                            volume = jsonInt(body, "volume"),
                            muted = jsonBoolean(body, "muted") ?: false,
                            overlays = overlays(JSONObject(body).optJSONArray("overlays"))
                        )
                        call.respondJson(sceneJson(SignageRuntime.saveScene(scene)), HttpStatusCode.Created)
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "INVALID_SCENE"), HttpStatusCode.BadRequest)
                    }
                }
                delete("/api/scenes/{id}") {
                    if (!call.authorized()) return@delete
                    val deleted = SignageRuntime.deleteScene(call.parameters["id"].orEmpty())
                    call.respondJson("{\"deleted\":$deleted}", if (deleted) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
                post("/api/playlists") {
                    if (!call.authorized()) return@post
                    try {
                        val body = call.receiveText()
                        val bodyJson = JSONObject(body)
                        val itemsJson = bodyJson.optJSONArray("items") ?: JSONArray()
                        val itemJson = buildList {
                            for (index in 0 until itemsJson.length()) {
                                val item = itemsJson.optJSONObject(index) ?: continue
                                val sceneId = item.optString("sceneId").takeIf { it.isNotBlank() } ?: continue
                                add(SignagePlaylistItem(
                                    sceneId,
                                    if (item.has("durationMs") && !item.isNull("durationMs")) item.optLong("durationMs") else null,
                                    item.optBoolean("enabled", true)
                                ))
                            }
                        }
                        val playlist = SignagePlaylist(
                            id = jsonString(body, "id") ?: java.util.UUID.randomUUID().toString(),
                            name = jsonString(body, "name") ?: "Playlist",
                            items = itemJson,
                            loop = jsonBoolean(body, "loop") ?: true
                        )
                        call.respondJson(playlistJson(SignageRuntime.savePlaylist(playlist)), HttpStatusCode.Created)
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "INVALID_PLAYLIST"), HttpStatusCode.BadRequest)
                    }
                }
                delete("/api/playlists/{id}") {
                    if (!call.authorized()) return@delete
                    val deleted = SignageRuntime.deletePlaylist(call.parameters["id"].orEmpty())
                    call.respondJson("{\"deleted\":$deleted}", if (deleted) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
                delete("/api/resources/{id}") {
                    if (!call.authorized()) return@delete
                    val deleted = SignageRuntime.deleteResource(call.parameters["id"].orEmpty())
                    call.respondJson("{\"deleted\":$deleted}", if (deleted) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
            }
        }
        server.start(wait = false)
        engine = server.engine
    }

    fun stop() {
        SignageRuntime.unregisterStateListener(stateListener)
        webSocketSessions.clear()
        broadcastScope?.cancel()
        broadcastScope = null
        engine?.stop(1000, 2000)
        engine = null
    }

    private fun broadcastState() {
        val event = deviceStatusEventJson()
        broadcastScope?.launch {
            webSocketSessions.toList().forEach { session ->
                runCatching { session.send(Frame.Text(event)) }
                    .onFailure { webSocketSessions.remove(session) }
            }
        }
    }

    private suspend fun ApplicationCall.authorized(requireSession: Boolean = true): Boolean {
        val token = request.headers["X-Local-Signage-Token"]
        if (!SignageRuntime.hasWebAccessToken(token)) {
            respondJson(errorJson("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
            return false
        }
        if (requireSession && !SignageRuntime.hasControlSession(sessionId().orEmpty())) {
            respondJson(errorJson("CONTROL_SESSION_INVALID"), HttpStatusCode.Conflict)
            return false
        }
        return true
    }

    private fun ApplicationCall.hasDeviceToken(): Boolean {
        val supplied = request.headers["X-Local-Signage-Device-Token"]?.toByteArray() ?: return false
        return MessageDigest.isEqual(supplied, SignageRuntime.controlToken().toByteArray())
    }

    private suspend fun ApplicationCall.authorizedOrDevice(requireSession: Boolean = true): Boolean =
        hasDeviceToken() || authorized(requireSession)

    private suspend fun ApplicationCall.respondFleetCommand(action: String, jsonValue: Boolean = false) {
        if (!authorized()) return
        try {
            val body = JSONObject(receiveText())
            val targets = pairedTargets(jsonStringList(body, "deviceIds"))
            if (targets.isEmpty()) throw IllegalArgumentException("NO_PAIRED_DEVICES")
            val resource = body.optString("resourceId").takeIf { it.isNotBlank() }?.let(SignageRuntime::resource)
            if (body.has("resourceId") && resource == null) throw IllegalArgumentException("RESOURCE_NOT_FOUND")
            val value = if (jsonValue && body.has("value")) body.optInt("value") else null
            val results = withContext(Dispatchers.IO) { SignageDeviceFleet.command(action, resource, value, targets) }
            respondJson(fleetResultsJson(results))
        } catch (error: Exception) {
            respondJson(errorJson(error.message ?: "INVALID_COMMAND"), HttpStatusCode.BadRequest)
        }
    }

    private suspend fun ApplicationCall.respondFleetPlaylistCommand() {
        if (!authorized()) return
        try {
            val body = JSONObject(receiveText())
            val playlist = SignageRuntime.playlist(body.optString("playlistId"))
                ?: throw IllegalArgumentException("PLAYLIST_NOT_FOUND")
            val targets = pairedTargets(jsonStringList(body, "deviceIds"))
            if (targets.isEmpty()) throw IllegalArgumentException("NO_PAIRED_DEVICES")
            val results = withContext(Dispatchers.IO) { SignageDeviceFleet.command("PLAY_PLAYLIST", null, playlist, null, targets) }
            respondJson(fleetResultsJson(results))
        } catch (error: Exception) {
            respondJson(errorJson(error.message ?: "INVALID_PLAYLIST_COMMAND"), HttpStatusCode.BadRequest)
        }
    }

    private fun pairedTargets(ids: List<String>): List<PairedDevice> {
        val paired = SignageRuntime.pairedDevices().associateBy { it.deviceId }
        return ids.distinct().mapNotNull { paired[it] }
    }

    private fun ApplicationCall.sessionId(): String? =
        request.headers["X-Local-Signage-Session"] ?: request.headers["X-Control-Session"]

    private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respondText(body, ContentType.Application.Json, status)

    private suspend fun ApplicationCall.respondPairing(pairing: TemporaryPairingToken) {
        try {
            respondJson(pairingJson(pairing))
        } catch (error: IllegalStateException) {
            if (error.message == "LOCAL_NETWORK_UNAVAILABLE") {
                respondJson(errorJson("LOCAL_NETWORK_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable)
            } else {
                throw error
            }
        }
    }

    private fun deviceJson(): String = "{" +
        "\"deviceId\":${quote(SignageRuntime.state().deviceId)}," +
        "\"deviceName\":${quote(SignageRuntime.state().deviceName)}," +
        "\"port\":${SignageRuntime.state().serverPort}" + "}"

    private fun pairingJson(pairing: TemporaryPairingToken): String {
        val code = com.wkq.localsignage.feature.app.pairing.PairingCodeProvider.create(
            pairing.token,
            pairing.expiresAt,
            SignageRuntime.SERVER_PORT
        )
        val url = code.pairingUrl ?: throw IllegalStateException("LOCAL_NETWORK_UNAVAILABLE")
        val qr = checkNotNull(code.qrBitmap) { "LOCAL_NETWORK_UNAVAILABLE" }
        val qrDataUrl = "data:image/png;base64,${Base64.encodeToString(qrPng(qr), Base64.NO_WRAP)}"
        return "{\"url\":${quote(url)},\"token\":${quote(pairing.token)}," +
            "\"expiresAt\":${pairing.expiresAt},\"expiresInMs\":${(pairing.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)}," +
            "\"qrDataUrl\":${quote(qrDataUrl)}}"
    }

    private fun qrPng(bitmap: android.graphics.Bitmap): ByteArray {
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun devicesJson(): String = LocalDeviceDiscovery.snapshot().joinToString("[", "]") { device ->
        "{\"deviceId\":${quote(device.deviceId)},\"deviceName\":${quote(device.deviceName)}," +
            "\"host\":${quote(device.host)},\"port\":${device.port}," +
            "\"serviceName\":${quote(device.serviceName)},\"lastSeenAt\":${device.lastSeenAt}}"
    }

    private fun pairedDevicesJson(): String = SignageRuntime.pairedDevices().joinToString("[", "]", transform = ::pairedDeviceJson)
    private fun pairedDeviceJson(device: PairedDevice): String = "{\"deviceId\":${quote(device.deviceId)},\"deviceName\":${quote(device.deviceName)},\"host\":${quote(device.host)},\"port\":${device.port},\"pairedAt\":${device.pairedAt}}"
    private fun fleetResultsJson(results: List<SignageDeviceFleet.FleetResult>): String = results.joinToString("[", "]") { result ->
        "{\"deviceId\":${quote(result.deviceId)},\"deviceName\":${quote(result.deviceName)},\"success\":${result.success},\"skipped\":${result.skipped},\"code\":${quote(result.code)}}"
    }
    private fun fleetStatusesJson(statuses: List<SignageDeviceFleet.FleetStatus>): String = statuses.joinToString("[", "]") { status ->
        "{\"deviceId\":${quote(status.deviceId)},\"deviceName\":${quote(status.deviceName)},\"host\":${quote(status.host)},\"port\":${status.port}," +
            "\"state\":${quote(status.state.name)},\"checkedAt\":${status.checkedAt}," +
            "\"currentResourceId\":${status.currentResourceId?.let(::quote) ?: "null"},\"currentSceneId\":${status.currentSceneId?.let(::quote) ?: "null"}," +
            "\"currentPlaylistId\":${status.currentPlaylistId?.let(::quote) ?: "null"},\"playing\":${status.playing},\"volume\":${status.volume},\"muted\":${status.muted}," +
            "\"error\":${status.error?.let(::quote) ?: "null"},\"commandRevision\":${status.commandRevision}}"
    }

    private fun statusJson(): String {
        val state = SignageRuntime.state()
        return "{" +
            "\"deviceId\":${quote(state.deviceId)},\"deviceName\":${quote(state.deviceName)}," +
            "\"currentResourceId\":${state.currentResourceId?.let(::quote) ?: "null"}," +
            "\"currentSceneId\":${state.currentSceneId?.let(::quote) ?: "null"}," +
            "\"currentPlaylistId\":${state.currentPlaylistId?.let(::quote) ?: "null"}," +
            "\"playing\":${state.playing},\"volume\":${state.volume},\"muted\":${state.muted}," +
            "\"positionMs\":${state.positionMs},\"error\":${state.error?.let(::quote) ?: "null"},\"serverPort\":${state.serverPort},\"commandRevision\":${state.commandRevision}" + "}"
    }

    private fun deviceStatusEventJson(): String = "{\"type\":\"DEVICE_STATUS\",\"state\":${statusJson()}}"

    private fun settingsJson(): String {
        val settings = SignageRuntime.settings()
        return "{\"fallbackSceneId\":${settings.fallbackSceneId?.let(::quote) ?: "null"}," +
            "\"keepScreenAwake\":${settings.keepScreenAwake},\"autoResume\":${settings.autoResume},\"fullscreen\":${settings.fullscreen}}"
    }

    private fun errorsJson(): String = SignageRuntime.playbackErrors().joinToString("[", "]") { error ->
        "{\"id\":${error.id},\"mediaId\":${error.mediaId?.let(::quote) ?: "null"}," +
            "\"sceneId\":${error.sceneId?.let(::quote) ?: "null"},\"errorCode\":${quote(error.errorCode)}," +
            "\"action\":${quote(error.action)},\"attempt\":${error.attempt},\"createdAt\":${error.createdAt}}"
    }

    private fun diagnosticsJson(): String {
        val state = SignageRuntime.state()
        val storage = SignageRuntime.resourceStorageSummary()
        return "{" +
            "\"deviceId\":${quote(state.deviceId)},\"deviceName\":${quote(state.deviceName)}," +
            "\"androidVersion\":${quote(Build.VERSION.RELEASE)},\"sdkInt\":${Build.VERSION.SDK_INT}," +
            "\"manufacturer\":${quote(Build.MANUFACTURER)},\"model\":${quote(Build.MODEL)}," +
            "\"uptimeMs\":${SystemClock.elapsedRealtime()},\"playing\":${state.playing}," +
            "\"currentResourceId\":${state.currentResourceId?.let(::quote) ?: "null"}," +
            "\"currentSceneId\":${state.currentSceneId?.let(::quote) ?: "null"}," +
            "\"currentPlaylistId\":${state.currentPlaylistId?.let(::quote) ?: "null"}," +
            "\"resources\":${SignageRuntime.resources().size},\"scenes\":${SignageRuntime.scenes().size}," +
            "\"playlists\":${SignageRuntime.playlists().size},\"pairedDevices\":${SignageRuntime.pairedDevices().size}," +
            "\"storage\":{\"usedBytes\":${storage.usedBytes},\"availableBytes\":${storage.availableBytes},\"quotaBytes\":${storage.quotaBytes}}," +
            "\"recentErrors\":${SignageRuntime.playbackErrors(20).size},\"lastError\":${state.error?.let(::quote) ?: "null"}}"
    }

    private fun diagnosticsText(): String {
        val state = SignageRuntime.state()
        val storage = SignageRuntime.resourceStorageSummary()
        return buildString {
            appendLine("Local Signage Diagnostics")
            appendLine("Generated: ${System.currentTimeMillis()}")
            appendLine("Device ID: ${state.deviceId}")
            appendLine("Device name: ${state.deviceName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Hardware: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Uptime ms: ${SystemClock.elapsedRealtime()}")
            appendLine("Playing: ${state.playing}")
            appendLine("Current resource: ${state.currentResourceId ?: "none"}")
            appendLine("Current scene: ${state.currentSceneId ?: "none"}")
            appendLine("Current playlist: ${state.currentPlaylistId ?: "none"}")
            appendLine("Content: ${SignageRuntime.resources().size} resources, ${SignageRuntime.scenes().size} scenes, ${SignageRuntime.playlists().size} playlists")
            appendLine("Paired devices: ${SignageRuntime.pairedDevices().size}")
            appendLine("Storage: ${storage.usedBytes} used, ${storage.availableBytes} available, ${storage.quotaBytes} quota")
            appendLine("Recent playback errors:")
            SignageRuntime.playbackErrors(20).forEach { error ->
                appendLine("${error.createdAt} ${error.errorCode} action=${error.action} attempt=${error.attempt} media=${error.mediaId ?: "none"} scene=${error.sceneId ?: "none"}")
            }
        }
    }

    private fun resourcesJson(): String = SignageRuntime.resources().joinToString("[", "]") { resource ->
        resourceJson(resource)
    }
    private fun resourceJson(resource: com.wkq.localsignage.feature.app.model.SignageResource): String =
        "{\"id\":${quote(resource.id)},\"name\":${quote(resource.name)},\"kind\":${quote(resource.kind)},\"mimeType\":${quote(resource.mimeType)},\"hash\":${quote(resource.hash)},\"sizeBytes\":${resource.sizeBytes}," +
            "\"sourceUri\":${resource.sourceUri?.let(::quote) ?: "null"},\"content\":${resource.content?.let(::quote) ?: "null"},\"refreshIntervalMs\":${resource.refreshIntervalMs ?: "null"}," +
            "\"url\":${if (resource.isLocalFile) quote("/media/${resource.id}") else "null"}}"

    private fun scenesJson(): String = SignageRuntime.scenes().joinToString("[", "]", transform = ::sceneJson)
    private fun sceneJson(scene: SignageScene): String = "{\"id\":${quote(scene.id)},\"name\":${quote(scene.name)},\"resourceId\":${quote(scene.resourceId)},\"fitMode\":${quote(scene.fitMode)},\"cropGravity\":${quote(scene.cropGravity)},\"backgroundType\":${quote(scene.backgroundType)},\"backgroundColor\":${scene.backgroundColor?.let(::quote) ?: "null"},\"volume\":${scene.volume ?: "null"},\"muted\":${scene.muted},\"overlays\":${overlaysJson(scene.overlays)}}"
    private fun playlistsJson(): String = SignageRuntime.playlists().joinToString("[", "]", transform = ::playlistJson)
    private fun playlistJson(playlist: SignagePlaylist): String = "{\"id\":${quote(playlist.id)},\"name\":${quote(playlist.name)},\"loop\":${playlist.loop},\"items\":[${playlist.items.joinToString { "{\"sceneId\":${quote(it.sceneId)},\"durationMs\":${it.durationMs ?: "null"},\"enabled\":${it.enabled}}" }}]}"
    private fun sessionJson(session: com.wkq.localsignage.feature.app.model.ControlSession?): String = session?.let { "{\"sessionId\":${quote(it.sessionId)},\"clientName\":${quote(it.clientName)},\"expiresAt\":${it.expiresAt}}" } ?: "null"
    private fun controlSessionStatusJson(session: com.wkq.localsignage.feature.app.model.ControlSession?): String = session?.let {
        "{\"active\":true,\"clientName\":${quote(it.clientName)},\"expiresAt\":${it.expiresAt}}"
    } ?: "{\"active\":false,\"clientName\":null,\"expiresAt\":null}"
    private fun controlSessionBusyJson(session: com.wkq.localsignage.feature.app.model.ControlSession?): String =
        "{\"error\":{\"code\":\"CONTROL_SESSION_BUSY\",\"message\":\"Another control session currently owns write access\",\"details\":${controlSessionStatusJson(session)}}}"
    private fun errorJson(code: String): String =
        "{\"error\":{\"code\":${quote(code)},\"message\":${quote(errorMessage(code))},\"details\":{}}}"
    private fun errorMessage(code: String): String = when (code) {
        "UNAUTHORIZED" -> "Authentication is required or has expired"
        "CONTROL_SESSION_BUSY" -> "Another control session currently owns write access"
        "CONTROL_SESSION_INVALID" -> "The control session is missing or expired"
        "COMMAND_REJECTED" -> "The command revision was rejected"
        "REMOTE_URL_PROTOCOL_NOT_ALLOWED" -> "Only approved remote URL protocols are supported"
        "REMOTE_HOST_NOT_ALLOWED" -> "The remote host is not allowed"
        "REMOTE_RESOURCE_TOO_LARGE" -> "The remote resource exceeds the size limit"
        else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
    private fun quote(value: String): String = JSONObject.quote(value)
    private fun commandFingerprint(body: JSONObject): String {
        return CommandRequestFingerprint.create(
            listOf("action", "resourceId", "sceneId", "playlistId", "value", "revision").associateWith { key ->
                if (body.has(key) && !body.isNull(key)) body.optString(key) else null
            }
        )
    }
    private fun jsonString(body: String, key: String): String? = runCatching { JSONObject(body).optString(key).takeIf { it.isNotBlank() } }.getOrNull()
    private fun jsonInt(body: String, key: String): Int? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optInt(key) }.getOrNull()
    private fun jsonLong(body: String, key: String): Long? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optLong(key) }.getOrNull()
    private fun jsonBoolean(body: String, key: String): Boolean? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optBoolean(key) }.getOrNull()
    private fun jsonStringList(body: JSONObject, key: String): List<String> = buildList {
        val array = body.optJSONArray(key) ?: return@buildList
        for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun overlays(array: JSONArray?): List<SignageOverlay> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val content = item.optString("content").takeIf { it.isNotBlank() } ?: continue
            add(SignageOverlay(
                id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                type = item.optString("type", "TEXT").uppercase(),
                content = content,
                horizontalPosition = item.optString("horizontalPosition", "CENTER").uppercase(),
                verticalPosition = item.optString("verticalPosition", "BOTTOM").uppercase(),
                textSizeSp = item.optInt("textSizeSp", 28).coerceIn(8, 160),
                textColor = item.optString("textColor", "#FFFFFFFF"),
                backgroundColor = item.optString("backgroundColor", "#99000000"),
                paddingDp = item.optInt("paddingDp", 12).coerceIn(0, 96),
                speedDpPerSecond = item.optInt("speedDpPerSecond", 80).coerceIn(10, 500),
                enabled = item.optBoolean("enabled", true),
                zIndex = item.optInt("zIndex", index)
            ))
        }
    }
    private fun overlaysJson(overlays: List<SignageOverlay>): String = JSONArray().apply {
        overlays.forEach { overlay -> put(JSONObject().apply {
            put("id", overlay.id); put("type", overlay.type); put("content", overlay.content)
            put("horizontalPosition", overlay.horizontalPosition); put("verticalPosition", overlay.verticalPosition)
            put("textSizeSp", overlay.textSizeSp); put("textColor", overlay.textColor); put("backgroundColor", overlay.backgroundColor)
            put("paddingDp", overlay.paddingDp); put("speedDpPerSecond", overlay.speedDpPerSecond)
            put("enabled", overlay.enabled); put("zIndex", overlay.zIndex)
        }) }
    }.toString()

    private companion object {
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        const val WEB_CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; connect-src 'self' ws: wss:; " +
                "style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; " +
                "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
        const val WEB_ACCESS_TOKEN_TTL_MS = 8 * 60 * 60 * 1000L
        const val DEFAULT_SLIDE_DURATION_MS = 8_000L
        const val MAX_IMAGE_BATCH_SIZE = 20
    }

    private val webConsoleTemplate: String by lazy {
        applicationContext.resources.openRawResource(R.raw.web_console).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
