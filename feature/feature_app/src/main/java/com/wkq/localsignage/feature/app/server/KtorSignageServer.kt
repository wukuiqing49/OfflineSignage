package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream

class KtorSignageServer(private val port: Int) {
    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return
        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            routing {
                get("/") { call.respondText(WEB_APP, ContentType.Text.Html) }
                get("/api/device") { call.respondJson(deviceJson()) }
                get("/api/status") { call.respondJson(statusJson()) }
                get("/api/resources") { call.respondJson(resourcesJson()) }
                get("/api/scenes") { call.respondJson(scenesJson()) }
                get("/api/playlists") { call.respondJson(playlistsJson()) }
                get("/media/{id}") {
                    val resource = SignageRuntime.resource(call.parameters["id"])
                    val file = resource?.let(SignageRuntime::fileFor)
                    if (file?.isFile == true) call.respondFile(file)
                    else call.respondJson(errorJson("RESOURCE_NOT_FOUND"), HttpStatusCode.NotFound)
                }
                post("/api/resources/upload") {
                    if (!call.authorized()) return@post
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
                            SignagePlaybackController.applyCommand("PLAY", resourceId = uploadedId)
                            call.respondJson("{\"id\":${quote(uploadedId)}}", HttpStatusCode.Created)
                        }
                    } catch (error: IllegalArgumentException) {
                        call.respondJson(errorJson(error.message ?: "INVALID_UPLOAD"), HttpStatusCode.BadRequest)
                    }
                }
                post("/api/control") {
                    if (!call.authorized()) return@post
                    val body = call.receiveText()
                    val action = jsonString(body, "action") ?: "TOGGLE"
                    val accepted = SignagePlaybackController.applyCommand(
                        action = action,
                        resourceId = jsonString(body, "resourceId"),
                        sceneId = jsonString(body, "sceneId"),
                        playlistId = jsonString(body, "playlistId"),
                        value = jsonInt(body, "value")
                    )
                    if (accepted) call.respondJson(statusJson())
                    else call.respondJson(errorJson("UNSUPPORTED_ACTION"), HttpStatusCode.BadRequest)
                }
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
                        val itemJson = Regex("\\{[^{}]*\\}").findAll(body).mapNotNull { match ->
                            jsonString(match.value, "sceneId")?.let { SignagePlaylistItem(it, jsonLong(match.value, "durationMs"), jsonBoolean(match.value, "enabled") ?: true) }
                        }.toList()
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

    fun stop() { engine?.stop(1000, 2000); engine = null }

    private suspend fun ApplicationCall.authorized(): Boolean {
        val token = request.headers["X-Local-Signage-Token"]
        if (token == SignageRuntime.controlToken()) return true
        respondJson(errorJson("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
        return false
    }

    private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respondText(body, ContentType.Application.Json, status)

    private fun deviceJson(): String = "{" +
        "\"deviceId\":${quote(SignageRuntime.state().deviceId)}," +
        "\"deviceName\":${quote(SignageRuntime.state().deviceName)}," +
        "\"port\":${SignageRuntime.state().serverPort}," +
        "\"controlToken\":${quote(SignageRuntime.controlToken())}" + "}"

    private fun statusJson(): String {
        val state = SignageRuntime.state()
        return "{" +
            "\"deviceId\":${quote(state.deviceId)},\"deviceName\":${quote(state.deviceName)}," +
            "\"currentResourceId\":${state.currentResourceId?.let(::quote) ?: "null"}," +
            "\"currentSceneId\":${state.currentSceneId?.let(::quote) ?: "null"}," +
            "\"currentPlaylistId\":${state.currentPlaylistId?.let(::quote) ?: "null"}," +
            "\"playing\":${state.playing},\"volume\":${state.volume},\"muted\":${state.muted}," +
            "\"positionMs\":${state.positionMs},\"error\":${state.error?.let(::quote) ?: "null"},\"serverPort\":${state.serverPort}" + "}"
    }

    private fun resourcesJson(): String = SignageRuntime.resources().joinToString("[", "]") { resource ->
        "{\"id\":${quote(resource.id)},\"name\":${quote(resource.name)},\"mimeType\":${quote(resource.mimeType)},\"hash\":${quote(resource.hash)},\"sizeBytes\":${resource.sizeBytes},\"url\":${quote("/media/${resource.id}")}}"
    }

    private fun scenesJson(): String = SignageRuntime.scenes().joinToString("[", "]", transform = ::sceneJson)
    private fun sceneJson(scene: SignageScene): String = "{\"id\":${quote(scene.id)},\"name\":${quote(scene.name)},\"resourceId\":${quote(scene.resourceId)},\"fitMode\":${quote(scene.fitMode)},\"cropGravity\":${quote(scene.cropGravity)},\"backgroundType\":${quote(scene.backgroundType)},\"backgroundColor\":${scene.backgroundColor?.let(::quote) ?: "null"},\"volume\":${scene.volume ?: "null"},\"muted\":${scene.muted}}"
    private fun playlistsJson(): String = SignageRuntime.playlists().joinToString("[", "]", transform = ::playlistJson)
    private fun playlistJson(playlist: SignagePlaylist): String = "{\"id\":${quote(playlist.id)},\"name\":${quote(playlist.name)},\"loop\":${playlist.loop},\"items\":[${playlist.items.joinToString { "{\"sceneId\":${quote(it.sceneId)},\"durationMs\":${it.durationMs ?: "null"},\"enabled\":${it.enabled}}" }}]}"
    private fun errorJson(code: String): String = "{\"error\":{\"code\":${quote(code)}}}"
    private fun quote(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun jsonString(body: String, key: String): String? = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    private fun jsonInt(body: String, key: String): Int? = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
    private fun jsonLong(body: String, key: String): Long? = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
    private fun jsonBoolean(body: String, key: String): Boolean? = Regex("\"$key\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBoolean()

    private companion object {
        val WEB_APP = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Signage</title>
            <style>body{font:16px system-ui;max-width:800px;margin:auto;padding:24px;background:#101418;color:#fff}button,input{font:inherit;padding:10px;margin:4px}section{padding:16px;background:#1c242d;border-radius:8px;margin:12px 0}.row{display:flex;gap:8px;align-items:center;justify-content:space-between;border-bottom:1px solid #39434d;padding:8px 0}</style>
            <h1>Local Signage</h1><section><p id="state">正在连接...</p><button onclick="control('TOGGLE')">暂停 / 继续</button><button onclick="control('STOP')">停止</button><button onclick="control('NEXT')">下一项</button><button onclick="control('PREVIOUS')">上一项</button><label>音量 <input id="volume" type="range" min="0" max="100" value="80" onchange="control('VOLUME',+this.value)"></label><button onclick="control('MUTE')">静音</button><button onclick="control('UNMUTE')">取消静音</button></section><section><input id="file" type="file" accept="image/*,video/*"><button onclick="upload()">上传并播放</button></section><section><h2>资源</h2><div id="resources"></div></section>
            <script>let token='';const $=id=>document.getElementById(id);async function init(){token=(await(await fetch('/api/device')).json()).controlToken;refresh()}async function refresh(){const s=await(await fetch('/api/status')).json();$('state').textContent=(s.playing?'播放中':'已暂停')+' · '+(s.error||'正常');$('volume').value=s.volume;const rs=await(await fetch('/api/resources')).json();$('resources').innerHTML=rs.map(r=>'<div class="row"><span>'+r.name+'</span><button onclick="play(\''+r.id+'\')">播放</button><button onclick="removeResource(\''+r.id+'\')">删除</button></div>').join('')||'暂无资源'}async function control(action,value=null){await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json','X-Local-Signage-Token':token},body:JSON.stringify({action,value})});refresh()}async function play(resourceId){await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json','X-Local-Signage-Token':token},body:JSON.stringify({action:'PLAY',resourceId})});refresh()}async function upload(){const f=$('file').files[0];if(!f)return;const d=new FormData();d.append('file',f);await fetch('/api/resources/upload',{method:'POST',headers:{'X-Local-Signage-Token':token},body:d});$('file').value='';refresh()}async function removeResource(id){await fetch('/api/resources/'+id,{method:'DELETE',headers:{'X-Local-Signage-Token':token}});refresh()}init();setInterval(refresh,2000)</script>
        """.trimIndent()
    }
}
