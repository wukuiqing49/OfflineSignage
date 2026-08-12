package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import io.ktor.http.ContentType
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
                get("/api/status") { call.respondJson(statusJson()) }
                get("/api/resources") { call.respondJson(resourcesJson()) }
                get("/media/{id}") {
                    val resource = SignageRuntime.resource(call.parameters["id"])
                    val file = resource?.let(SignageRuntime::fileFor)
                    if (file?.isFile == true) {
                        call.respondFile(file)
                    } else {
                        call.respondJson("{\"error\":\"RESOURCE_NOT_FOUND\"}", HttpStatusCode.NotFound)
                    }
                }
                post("/api/resources/upload") {
                    val multipart = call.receiveMultipart()
                    var uploadedId: String? = null
                    while (true) {
                        val part = multipart.readPart() ?: break
                        try {
                            if (part is PartData.FileItem) {
                                val name = part.originalFileName ?: "resource"
                                val mime = part.contentType?.toString() ?: "application/octet-stream"
                                val resource = SignageRuntime.saveUpload(
                                    name,
                                    mime,
                                    part.provider().toInputStream()
                                )
                                uploadedId = resource.id
                            }
                        } finally {
                            part.dispose()
                        }
                    }
                    if (uploadedId == null) {
                        call.respondJson("{\"error\":\"FILE_REQUIRED\"}", HttpStatusCode.BadRequest)
                    } else {
                        SignagePlaybackController.applyCommand("PLAY", uploadedId)
                        call.respondJson("{\"id\":${quote(uploadedId)}}", HttpStatusCode.Created)
                    }
                }
                post("/api/control") {
                    val body = call.receiveText()
                    val action = jsonString(body, "action") ?: "TOGGLE"
                    val resourceId = jsonString(body, "resourceId")
                    if (SignagePlaybackController.applyCommand(action, resourceId)) {
                        call.respondJson(statusJson())
                    } else {
                        call.respondJson("{\"error\":\"UNSUPPORTED_ACTION\"}", HttpStatusCode.BadRequest)
                    }
                }
                delete("/api/resources/{id}") {
                    val deleted = SignageRuntime.deleteResource(call.parameters["id"].orEmpty())
                    val status = if (deleted) HttpStatusCode.OK else HttpStatusCode.NotFound
                    call.respondJson("{\"deleted\":$deleted}", status)
                }
            }
        }
        server.start(wait = false)
        engine = server.engine
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }

    private suspend fun ApplicationCall.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        respondText(body, ContentType.Application.Json, status)
    }

    private fun statusJson(): String {
        val state = SignageRuntime.state()
        return "{" +
            "\"deviceId\":${quote(state.deviceId)}," +
            "\"deviceName\":${quote(state.deviceName)}," +
            "\"currentResourceId\":${state.currentResourceId?.let(::quote) ?: "null"}," +
            "\"playing\":${state.playing}," +
            "\"serverPort\":${state.serverPort}" +
            "}"
    }

    private fun resourcesJson(): String = SignageRuntime.resources().joinToString(
        prefix = "[",
        postfix = "]"
    ) { resource ->
        "{" +
            "\"id\":${quote(resource.id)}," +
            "\"name\":${quote(resource.name)}," +
            "\"mimeType\":${quote(resource.mimeType)}," +
            "\"url\":${quote("/media/${resource.id}")}" +
            "}"
    }

    private fun jsonString(body: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            .find(body)
            ?.groupValues
            ?.get(1)

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private companion object {
        val WEB_APP = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Local Signage</title>
            <style>
              body{font:16px system-ui;max-width:720px;margin:auto;padding:24px;background:#101418;color:#fff}
              button,input{font:inherit;padding:10px;margin:4px}
              section{padding:16px;background:#1c242d;border-radius:8px}
            </style>
            <h1>Local Signage</h1>
            <section>
              <p id="state">正在连接...</p>
              <button onclick="control('TOGGLE')">暂停 / 继续</button>
              <input id="file" type="file" accept="image/*,video/*">
              <button onclick="upload()">上传并播放</button>
            </section>
            <script>
              async function refresh(){
                const s=await (await fetch('/api/status')).json();
                document.getElementById('state').textContent=s.playing?'播放中':'已暂停';
              }
              async function control(action){
                await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action})});
                refresh();
              }
              async function upload(){
                const f=document.getElementById('file').files[0];
                if(!f)return;
                const d=new FormData();d.append('file',f);
                await fetch('/api/resources/upload',{method:'POST',body:d});
                refresh();
              }
              refresh();setInterval(refresh,1000);
            </script>
        """.trimIndent()
    }
}
