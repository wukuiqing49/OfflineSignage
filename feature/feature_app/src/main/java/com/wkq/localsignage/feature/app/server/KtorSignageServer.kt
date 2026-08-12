package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignageSettings
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.device.SignageDeviceFleet
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.discovery.LocalDeviceDiscovery
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

class KtorSignageServer(private val port: Int) {
    private var engine: ApplicationEngine? = null
    private var broadcastScope: CoroutineScope? = null
    private val webSocketSessions = CopyOnWriteArraySet<WebSocketSession>()
    private val stateListener: () -> Unit = { broadcastState() }

    fun start() {
        if (engine != null) return
        broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SignageRuntime.registerStateListener(stateListener)
        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                get("/") { call.respondText(WEB_APP_V2.replace("__CONTROL_TOKEN__", quote(SignageRuntime.controlToken())), ContentType.Text.Html) }
                webSocket("/ws") {
                    val token = call.request.queryParameters["token"] ?: call.request.headers["X-Local-Signage-Token"]
                    if (token != SignageRuntime.controlToken()) {
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
                get("/api/devices") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(devicesJson())
                }
                get("/api/devices/paired") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(pairedDevicesJson())
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
                get("/api/status") { call.respondJson(statusJson()) }
                get("/api/resources") { call.respondJson(resourcesJson()) }
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
                get("/api/scenes") { call.respondJson(scenesJson()) }
                get("/api/playlists") { call.respondJson(playlistsJson()) }
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
                delete("/api/errors") {
                    if (!call.authorized()) return@delete
                    SignageRuntime.clearPlaybackErrors()
                    call.respondJson("{\"cleared\":true}")
                }
                get("/api/control/session") {
                    if (!call.authorized(requireSession = false)) return@get
                    call.respondJson(sessionJson(SignageRuntime.controlSession()))
                }
                post("/api/control/session/acquire") {
                    if (!call.authorized(requireSession = false)) return@post
                    val body = call.receiveText()
                    val session = SignageRuntime.acquireControlSession(
                        jsonString(body, "clientName") ?: "Browser",
                        jsonBoolean(body, "takeover") == true
                    )
                    if (session == null) call.respondJson(errorJson("CONTROL_SESSION_BUSY"), HttpStatusCode.Conflict)
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
                    val resource = SignageRuntime.resource(call.parameters["id"])
                    val file = resource?.let(SignageRuntime::fileFor)
                    if (file?.isFile == true) call.respondFile(file)
                    else call.respondJson(errorJson("RESOURCE_NOT_FOUND"), HttpStatusCode.NotFound)
                }
                post("/api/resources/upload") {
                    val deviceRequest = call.hasDeviceToken()
                    if (!deviceRequest && !call.authorized()) return@post
                    try {
                        val multipart = call.receiveMultipart()
                        var uploadedId: String? = null
                        while (true) {
                            val part = multipart.readPart() ?: break
                            try {
                                if (part is PartData.FileItem) {
                                    val resource = SignageRuntime.saveUpload(
                                        part.originalFileName ?: "resource",
                                        part.contentType?.toString() ?: "application/octet-stream",
                                        part.provider().toInputStream()
                                    )
                                    uploadedId = resource.id
                                }
                            } finally { part.dispose() }
                        }
                        if (uploadedId == null) call.respondJson(errorJson("FILE_REQUIRED"), HttpStatusCode.BadRequest)
                        else {
                            if (!deviceRequest) SignagePlaybackController.applyCommand("PLAY", resourceId = uploadedId)
                            call.respondJson("{\"id\":${quote(uploadedId)}}", HttpStatusCode.Created)
                        }
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "INVALID_UPLOAD"), HttpStatusCode.BadRequest)
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
                post("/api/control") {
                    if (!call.authorizedOrDevice()) return@post
                    try {
                        val body = call.receiveText()
                        val action = jsonString(body, "action") ?: "TOGGLE"
                        val accepted = SignagePlaybackController.applyCommand(
                            action = action,
                            resourceId = jsonString(body, "resourceId"),
                            sceneId = jsonString(body, "sceneId"),
                            playlistId = jsonString(body, "playlistId"),
                            value = jsonInt(body, "value"),
                            revision = jsonLong(body, "revision")
                        )
                        if (accepted) call.respondJson(statusJson())
                        else call.respondJson(errorJson("COMMAND_REJECTED"), HttpStatusCode.Conflict)
                    } catch (_: Exception) {
                        call.respondJson(errorJson("INVALID_JSON"), HttpStatusCode.BadRequest)
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
                            muted = jsonBoolean(body, "muted") ?: false
                        )
                        call.respondJson(sceneJson(SignageRuntime.saveScene(scene)), HttpStatusCode.Created)
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "INVALID_SCENE"), HttpStatusCode.BadRequest)
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
                        val file = SignageRuntime.fileFor(resource)
                        val results = withContext(Dispatchers.IO) { SignageDeviceFleet.sync(resource, file, targets) }
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
                        val files = resources.values.associate { it.id to SignageRuntime.fileFor(it) }
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
                            muted = jsonBoolean(body, "muted") ?: false
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
        if (token == SignageRuntime.controlToken() && (!requireSession || SignageRuntime.hasControlSession(sessionId().orEmpty()))) return true
        respondJson(errorJson("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
        return false
    }

    private fun ApplicationCall.hasDeviceToken(): Boolean = request.headers["X-Local-Signage-Device-Token"] == SignageRuntime.controlToken()

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

    private fun ApplicationCall.sessionId(): String? = request.headers["X-Local-Signage-Session"]

    private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respondText(body, ContentType.Application.Json, status)

    private fun deviceJson(): String = "{" +
        "\"deviceId\":${quote(SignageRuntime.state().deviceId)}," +
        "\"deviceName\":${quote(SignageRuntime.state().deviceName)}," +
        "\"port\":${SignageRuntime.state().serverPort}," +
        "\"controlToken\":${quote(SignageRuntime.controlToken())}" + "}"

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

    private fun resourcesJson(): String = SignageRuntime.resources().joinToString("[", "]") { resource ->
        resourceJson(resource)
    }
    private fun resourceJson(resource: com.wkq.localsignage.feature.app.model.SignageResource): String =
        "{\"id\":${quote(resource.id)},\"name\":${quote(resource.name)},\"mimeType\":${quote(resource.mimeType)},\"hash\":${quote(resource.hash)},\"sizeBytes\":${resource.sizeBytes},\"url\":${quote("/media/${resource.id}")}}"

    private fun scenesJson(): String = SignageRuntime.scenes().joinToString("[", "]", transform = ::sceneJson)
    private fun sceneJson(scene: SignageScene): String = "{\"id\":${quote(scene.id)},\"name\":${quote(scene.name)},\"resourceId\":${quote(scene.resourceId)},\"fitMode\":${quote(scene.fitMode)},\"cropGravity\":${quote(scene.cropGravity)},\"backgroundType\":${quote(scene.backgroundType)},\"backgroundColor\":${scene.backgroundColor?.let(::quote) ?: "null"},\"volume\":${scene.volume ?: "null"},\"muted\":${scene.muted}}"
    private fun playlistsJson(): String = SignageRuntime.playlists().joinToString("[", "]", transform = ::playlistJson)
    private fun playlistJson(playlist: SignagePlaylist): String = "{\"id\":${quote(playlist.id)},\"name\":${quote(playlist.name)},\"loop\":${playlist.loop},\"items\":[${playlist.items.joinToString { "{\"sceneId\":${quote(it.sceneId)},\"durationMs\":${it.durationMs ?: "null"},\"enabled\":${it.enabled}}" }}]}"
    private fun sessionJson(session: com.wkq.localsignage.feature.app.model.ControlSession?): String = session?.let { "{\"sessionId\":${quote(it.sessionId)},\"clientName\":${quote(it.clientName)},\"expiresAt\":${it.expiresAt}}" } ?: "null"
    private fun errorJson(code: String): String = "{\"error\":{\"code\":${quote(code)}}}"
    private fun quote(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun jsonString(body: String, key: String): String? = runCatching { JSONObject(body).optString(key).takeIf { it.isNotBlank() } }.getOrNull()
    private fun jsonInt(body: String, key: String): Int? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optInt(key) }.getOrNull()
    private fun jsonLong(body: String, key: String): Long? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optLong(key) }.getOrNull()
    private fun jsonBoolean(body: String, key: String): Boolean? = runCatching { JSONObject(body).takeIf { it.has(key) && !it.isNull(key) }?.optBoolean(key) }.getOrNull()
    private fun jsonStringList(body: JSONObject, key: String): List<String> = buildList {
        val array = body.optJSONArray(key) ?: return@buildList
        for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }

    private companion object {
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        val WEB_APP_V2 = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Signage</title>
            <style>body{font:16px system-ui;max-width:800px;margin:auto;padding:24px;background:#101418;color:#fff}button,input{font:inherit;padding:10px;margin:4px}section{padding:16px;background:#1c242d;border-radius:8px;margin:12px 0}.row{display:flex;gap:8px;align-items:center;justify-content:space-between;border-bottom:1px solid #39434d;padding:8px 0}</style>
            <h1>Local Signage</h1><section><p id="state">Connecting...</p><button onclick="control('TOGGLE')">Pause / Resume</button><button onclick="control('STOP')">Stop</button><button onclick="control('NEXT')">Next</button><button onclick="control('PREVIOUS')">Previous</button><label>Volume <input id="volume" type="range" min="0" max="100" value="80" onchange="control('VOLUME',Number(this.value))"></label><button onclick="control('MUTE')">Mute</button><button onclick="control('UNMUTE')">Unmute</button></section><section><input id="file" type="file" accept="image/*,video/*"><button onclick="upload()">Upload and play</button><input id="remoteUrl" type="url" placeholder="HTTPS media URL"><input id="remoteName" type="text" placeholder="Optional file name"><button onclick="downloadRemote()">Cache remote media</button><p id="remoteStatus"></p></section><section><h2>Resources</h2><div id="resources"></div></section><section><h2>Devices</h2><div id="devices">Discovering...</div></section>
            <script>let token=__CONTROL_TOKEN__,session='',revision=0,ws=null;const el=id=>document.getElementById(id);const jsonHeaders=()=>({'Content-Type':'application/json','X-Local-Signage-Token':token,'X-Local-Signage-Session':session});function connectWebSocket(){if(ws&&ws.readyState===WebSocket.OPEN)return;ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws?token='+encodeURIComponent(token));ws.onmessage=event=>{const message=JSON.parse(event.data);if(message.type==='DEVICE_STATUS'){const state=message.state;revision=Math.max(revision,state.commandRevision||0);el('state').textContent=(state.playing?'Playing':'Paused')+' - '+(state.error||'Ready');el('volume').value=state.volume}};ws.onclose=()=>setTimeout(connectWebSocket,3000);ws.onerror=()=>ws.close()}async function refreshDevices(){const response=await fetch('/api/devices',{headers:{'X-Local-Signage-Token':token}});if(!response.ok)return;const devices=await response.json();el('devices').innerHTML=devices.map(device=>'<div class="row"><span>'+device.deviceName+' ('+device.host+':'+device.port+')</span></div>').join('')||'No peer devices'}async function init(){const result=await fetch('/api/control/session/acquire',{method:'POST',headers:{'Content-Type':'application/json','X-Local-Signage-Token':token},body:JSON.stringify({clientName:'Browser'})});if(result.ok)session=(await result.json()).sessionId;refresh();refreshDevices();connectWebSocket()}async function refresh(){const state=await (await fetch('/api/status')).json();revision=Math.max(revision,state.commandRevision||0);el('state').textContent=(state.playing?'Playing':'Paused')+' - '+(state.error||'Ready');el('volume').value=state.volume;const resources=await (await fetch('/api/resources')).json();el('resources').innerHTML=resources.map(resource=>'<div class="row"><span>'+resource.name+'</span><button onclick="play(\''+resource.id+'\')">Play</button><button onclick="removeResource(\''+resource.id+'\')">Delete</button></div>').join('')||'No resources'}async function control(action,value=null){await fetch('/api/control',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({action:action,value:value,revision:++revision})});refresh()}async function play(resourceId){await fetch('/api/control',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({action:'PLAY',resourceId:resourceId,revision:++revision})});refresh()}async function upload(){const file=el('file').files[0];if(!file)return;const data=new FormData();data.append('file',file);await fetch('/api/resources/upload',{method:'POST',headers:{'X-Local-Signage-Token':token,'X-Local-Signage-Session':session},body:data});el('file').value='';refresh()}async function downloadRemote(){const url=el('remoteUrl').value.trim();if(!url)return;el('remoteStatus').textContent='Downloading...';const response=await fetch('/api/resources/remote',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({url:url,name:el('remoteName').value.trim()||undefined})});if(response.ok){el('remoteUrl').value='';el('remoteName').value='';el('remoteStatus').textContent='Cached and playing';refresh()}else{const body=await response.json().catch(()=>({}));el('remoteStatus').textContent=body.error?.code||'Remote download failed'}}async function removeResource(id){await fetch('/api/resources/'+id,{method:'DELETE',headers:{'X-Local-Signage-Token':token,'X-Local-Signage-Session':session}});refresh()}setInterval(()=>fetch('/api/control/session/heartbeat',{method:'POST',headers:jsonHeaders()}),30000);init();setInterval(refresh,2000);setInterval(refreshDevices,5000)</script>
            <script>const fleetPanel='<section><h2>Paired Devices</h2><div><input id="pairId" placeholder="Device ID"><input id="pairName" placeholder="Name"><input id="pairHost" placeholder="Host"><input id="pairPort" type="number" value="8080"><input id="pairToken" type="password" placeholder="Device token"><button onclick="pairDevice()">Pair</button></div><div id="fleetDevices">Loading...</div><div><select id="fleetResource"><option value="">No resource</option></select><select id="fleetPlaylist"><option value="">No playlist</option></select><button onclick="fleetCommand('PLAY')">Play resource</button><button onclick="fleetCommand('PAUSE')">Pause</button><button onclick="fleetCommand('STOP')">Stop</button><button onclick="fleetCommand('MUTE')">Mute</button><button onclick="fleetCommand('VOLUME')">Volume 80</button><button onclick="syncFleetResource()">Sync resource</button><button onclick="syncFleetPlaylist()">Sync playlist</button><button onclick="playFleetPlaylist()">Play playlist</button></div></section>';document.body.insertAdjacentHTML('beforeend',fleetPanel);async function refreshFleet(){const paired=await(await fetch('/api/devices/paired',{headers:{'X-Local-Signage-Token':token}})).json();const resources=await(await fetch('/api/resources')).json();const playlists=await(await fetch('/api/playlists')).json();el('fleetResource').innerHTML='<option value="">No resource</option>'+resources.map(resource=>'<option value="'+resource.id+'">'+resource.name+'</option>').join('');el('fleetPlaylist').innerHTML='<option value="">No playlist</option>'+playlists.map(playlist=>'<option value="'+playlist.id+'">'+playlist.name+'</option>').join('');el('fleetDevices').innerHTML=paired.map(device=>'<label class="row"><span>'+device.deviceName+' ('+device.host+':'+device.port+')</span><input class="fleetTarget" type="checkbox" value="'+device.deviceId+'"></label>').join('')||'No paired devices'}function fleetIds(){return Array.from(document.querySelectorAll('.fleetTarget:checked')).map(input=>input.value)}async function pairDevice(){const body={deviceId:el('pairId').value,deviceName:el('pairName').value,host:el('pairHost').value,port:Number(el('pairPort').value),token:el('pairToken').value};const response=await fetch('/api/devices/pair',{method:'POST',headers:jsonHeaders(),body:JSON.stringify(body)});if(response.ok){el('pairToken').value='';refreshFleet()}}async function fleetCommand(action){const body={deviceIds:fleetIds(),action:action};const resourceId=el('fleetResource').value;if(resourceId)body.resourceId=resourceId;if(action==='VOLUME')body.value=80;await fetch('/api/devices/'+action.toLowerCase(),{method:'POST',headers:jsonHeaders(),body:JSON.stringify(body)});refreshFleet()}async function syncFleetResource(){const resourceId=el('fleetResource').value;if(!resourceId)return;await fetch('/api/devices/sync',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({resourceId:resourceId,deviceIds:fleetIds()})});refreshFleet()}async function syncFleetPlaylist(){const playlistId=el('fleetPlaylist').value;if(!playlistId)return;await fetch('/api/devices/sync-playlist',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({playlistId:playlistId,deviceIds:fleetIds()})});refreshFleet()}async function playFleetPlaylist(){const playlistId=el('fleetPlaylist').value;if(!playlistId)return;await fetch('/api/devices/play-playlist',{method:'POST',headers:jsonHeaders(),body:JSON.stringify({playlistId:playlistId,deviceIds:fleetIds()})});refreshFleet()}refreshFleet();setInterval(refreshFleet,5000)</script>
        """.trimIndent()
        val WEB_APP = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Signage</title>
            <style>body{font:16px system-ui;max-width:800px;margin:auto;padding:24px;background:#101418;color:#fff}button,input{font:inherit;padding:10px;margin:4px}section{padding:16px;background:#1c242d;border-radius:8px;margin:12px 0}.row{display:flex;gap:8px;align-items:center;justify-content:space-between;border-bottom:1px solid #39434d;padding:8px 0}</style>
            <h1>Local Signage</h1><section><p id="state">正在连接...</p><button onclick="control('TOGGLE')">暂停 / 继续</button><button onclick="control('STOP')">停止</button><button onclick="control('NEXT')">下一项</button><button onclick="control('PREVIOUS')">上一项</button><label>音量 <input id="volume" type="range" min="0" max="100" value="80" onchange="control('VOLUME',+this.value)"></label><button onclick="control('MUTE')">静音</button><button onclick="control('UNMUTE')">取消静音</button></section><section><input id="file" type="file" accept="image/*,video/*"><button onclick="upload()">上传并播放</button></section><section><h2>资源</h2><div id="resources"></div></section>
            <script>let token='';const $=id=>document.getElementById(id);async function init(){token=(await(await fetch('/api/device')).json()).controlToken;refresh()}async function refresh(){const s=await(await fetch('/api/status')).json();$('state').textContent=(s.playing?'播放中':'已暂停')+' · '+(s.error||'正常');$('volume').value=s.volume;const rs=await(await fetch('/api/resources')).json();$('resources').innerHTML=rs.map(r=>'<div class="row"><span>'+r.name+'</span><button onclick="play(\''+r.id+'\')">播放</button><button onclick="removeResource(\''+r.id+'\')">删除</button></div>').join('')||'暂无资源'}async function control(action,value=null){await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json','X-Local-Signage-Token':token},body:JSON.stringify({action,value})});refresh()}async function play(resourceId){await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json','X-Local-Signage-Token':token},body:JSON.stringify({action:'PLAY',resourceId})});refresh()}async function upload(){const f=$('file').files[0];if(!f)return;const d=new FormData();d.append('file',f);await fetch('/api/resources/upload',{method:'POST',headers:{'X-Local-Signage-Token':token},body:d});$('file').value='';refresh()}async function removeResource(id){await fetch('/api/resources/'+id,{method:'DELETE',headers:{'X-Local-Signage-Token':token}});refresh()}init();setInterval(refresh,2000)</script>
        """.trimIndent()
    }
}
