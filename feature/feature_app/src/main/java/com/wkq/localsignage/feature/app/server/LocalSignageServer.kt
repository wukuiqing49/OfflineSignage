package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalSignageServer(
    private val port: Int,
    private val runtime: SignageRuntime
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handle(socket) }
                }
            } catch (_: Exception) {
                if (running) running = false
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            try {
                val request = readRequest(input) ?: return
                val response = route(request)
                writeResponse(output, response)
            } catch (error: Exception) {
                writeResponse(output, Response.json(500, error.message ?: "Internal server error"))
            }
        }
    }

    private fun route(request: Request): Response {
        val path = request.path.substringBefore('?')
        return when {
            request.method == "GET" && path == "/" -> Response.html(WEB_APP)
            request.method == "GET" && path == "/api/status" -> Response.json(200, statusJson())
            request.method == "GET" && path == "/api/resources" -> Response.json(200, resourcesJson())
            request.method == "GET" && path.startsWith("/media/") -> media(path.removePrefix("/media/"))
            request.method == "POST" && path == "/api/resources/upload" -> upload(request)
            request.method == "POST" && path == "/api/control" -> command(request)
            request.method == "DELETE" && path.startsWith("/api/resources/") -> {
                val id = path.removePrefix("/api/resources/")
                if (runtime.deleteResource(id)) Response.json(200, "{\"deleted\":true}")
                else Response.json(404, "{\"error\":{\"code\":\"RESOURCE_NOT_FOUND\"}}")
            }
            else -> Response.json(404, "{\"error\":{\"code\":\"NOT_FOUND\"}}")
        }
    }

    private fun command(request: Request): Response {
        val body = request.body.toString(StandardCharsets.UTF_8)
        val action = jsonString(body, "action") ?: return Response.json(400, "{\"error\":{\"code\":\"INVALID_ACTION\"}}")
        val resourceId = jsonString(body, "resourceId")
        val value = jsonNumber(body, "value")
        return Response.json(200, stateJson(runtime.command(action, resourceId, value)))
    }

    private fun upload(request: Request): Response {
        val contentType = request.headers["content-type"] ?: return Response.json(400, "{\"error\":{\"code\":\"MISSING_CONTENT_TYPE\"}}")
        val boundary = Regex("boundary=([^;]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)?.trim('"')
            ?: return Response.json(400, "{\"error\":{\"code\":\"MISSING_BOUNDARY\"}}")
        val marker = "--$boundary".toByteArray(StandardCharsets.ISO_8859_1)
        val parts = splitMultipart(request.body, marker)
        val part = parts.firstOrNull { it.headers["content-disposition"]?.contains("filename=") == true }
            ?: return Response.json(400, "{\"error\":{\"code\":\"MISSING_FILE\"}}")
        val name = Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE).find(part.headers["content-disposition"].orEmpty())?.groupValues?.get(1) ?: "resource"
        val mimeType = part.headers["content-type"] ?: "application/octet-stream"
        val resource = runtime.saveUpload(name, mimeType, ByteArrayInputStream(part.body))
        runtime.command("PLAY", resource.id)
        return Response.json(201, resourceJson(resource))
    }

    private fun media(id: String): Response {
        val resource = runtime.resource(id) ?: return Response.json(404, "{\"error\":{\"code\":\"RESOURCE_NOT_FOUND\"}}")
        val file = runtime.fileFor(resource)
        if (!file.isFile) return Response.json(404, "{\"error\":{\"code\":\"FILE_NOT_FOUND\"}}")
        return Response.binary(200, resource.mimeType, file.readBytes())
    }

    private fun readRequest(input: InputStream): Request? {
        val headerBytes = readUntil(input, "\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)) ?: return null
        val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size < 2) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).lowercase() to line.substring(separator + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toLongOrNull()
            ?.takeIf { it in 0..MAX_REQUEST_BYTES }
            ?.toInt()
            ?: if (headers["content-length"] == null) 0 else throw RequestTooLargeException()
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) break
            offset += count
        }
        return Request(requestLine[0].uppercase(), URLDecoder.decode(requestLine[1], "UTF-8"), headers, body.copyOf(offset))
    }

    private fun writeResponse(output: BufferedOutputStream, response: Response) {
        val header = "HTTP/1.1 ${response.code} ${statusText(response.code)}\r\n" +
            "Content-Type: ${response.contentType}\r\n" +
            "Content-Length: ${response.body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(response.body)
        output.flush()
    }

    private fun statusJson(): String = stateJson(runtime.state())

    private fun stateJson(state: com.wkq.localsignage.feature.app.model.SignageState): String = "{" +
        "\"deviceId\":${quote(state.deviceId)},\"deviceName\":${quote(state.deviceName)}," +
        "\"currentResourceId\":${state.currentResourceId?.let(::quote) ?: "null"}," +
        "\"playing\":${state.playing},\"volume\":${state.volume},\"muted\":${state.muted}," +
        "\"positionMs\":${state.positionMs},\"serverPort\":${state.serverPort}}"

    private fun resourcesJson(): String = runtime.resources().joinToString(prefix = "[", postfix = "]") { resourceJson(it) }

    private fun resourceJson(resource: SignageResource): String = "{" +
        "\"id\":${quote(resource.id)},\"name\":${quote(resource.name)}," +
        "\"mimeType\":${quote(resource.mimeType)},\"hash\":${quote(resource.hash)}," +
        "\"sizeBytes\":${resource.sizeBytes},\"url\":${quote("/media/${resource.id}")}}"

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun jsonString(body: String, key: String): String? = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    private fun jsonNumber(body: String, key: String): Int? = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()

    private fun splitMultipart(body: ByteArray, marker: ByteArray): List<Part> {
        val result = mutableListOf<Part>()
        var start = indexOf(body, marker, 0)
        while (start >= 0) {
            val next = indexOf(body, marker, start + marker.size)
            if (next < 0) break
            val sectionStart = start + marker.size + 2
            val section = body.copyOfRange(sectionStart, next - 2)
            val divider = byteIndexOf(section, "\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            if (divider >= 0) {
                val headers = section.copyOfRange(0, divider).toString(StandardCharsets.ISO_8859_1).split("\r\n").mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) null else line.substring(0, separator).lowercase() to line.substring(separator + 1).trim()
                }.toMap()
                result += Part(headers, section.copyOfRange(divider + 4, section.size))
            }
            start = next
        }
        return result
    }

    private fun readUntil(input: InputStream, delimiter: ByteArray): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val next = input.read()
            if (next < 0) return null
            buffer.write(next)
            if (next == delimiter[matched].toInt()) {
                matched++
                if (matched == delimiter.size) return buffer.toByteArray().copyOf(buffer.size() - delimiter.size)
            } else matched = 0
        }
    }

    private fun indexOf(source: ByteArray, target: ByteArray, from: Int): Int = byteIndexOf(source, target, from)

    private fun byteIndexOf(source: ByteArray, target: ByteArray, from: Int = 0): Int {
        outer@ for (index in from..source.size - target.size) {
            for (offset in target.indices) if (source[index + offset] != target[offset]) continue@outer
            return index
        }
        return -1
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        400 -> "Bad Request"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    private data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)
    private data class Part(val headers: Map<String, String>, val body: ByteArray)
    private data class Response(val code: Int, val contentType: String, val body: ByteArray) {
        companion object {
            fun json(code: Int, body: String) = Response(code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
            fun html(body: String) = Response(200, "text/html; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
            fun binary(code: Int, contentType: String, body: ByteArray) = Response(code, contentType, body)
        }
    }

    private class RequestTooLargeException : IllegalArgumentException("Request body is too large")

    private companion object {
        const val MAX_REQUEST_BYTES = 200L * 1024L * 1024L + 1024L * 1024L
        val WEB_APP = """
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Signage</title>
<style>body{font:16px system-ui;margin:0;background:#101418;color:#edf2f7}main{max-width:900px;margin:auto;padding:24px}section{background:#1b222b;border:1px solid #303b47;border-radius:8px;padding:16px;margin:12px 0}button,input{font:inherit;padding:10px;margin:4px;border-radius:6px;border:1px solid #667788}button{cursor:pointer;background:#37a169;color:white}.resource{display:flex;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid #303b47}.muted{color:#aeb9c5}</style></head><body><main>
<h1>Local Signage</h1><p id="status" class="muted">正在连接广告机...</p><section><h2>上传资源</h2><input id="file" type="file" accept="image/*,video/*"><button onclick="upload()">上传并播放</button></section><section><h2>播放控制</h2><button onclick="command('PLAY')">播放</button><button onclick="command('PAUSE')">暂停</button><button onclick="command('STOP')">停止</button><button onclick="command('PREVIOUS')">上一项</button><button onclick="command('NEXT')">下一项</button><label>音量 <input id="volume" type="range" min="0" max="100" value="80" onchange="command('VOLUME',null,+this.value)"></label><button onclick="command('MUTE')">静音</button><button onclick="command('UNMUTE')">取消静音</button></section><section><h2>资源库</h2><div id="resources"></div></section></main>
<script>const $=id=>document.getElementById(id);async function refresh(){const s=await (await fetch('/api/status')).json();$('status').textContent=`设备 ${'$'}{s.deviceName} · ${'$'}{s.playing?'播放中':'已暂停'} · 音量 ${'$'}{s.volume}%${'$'}{s.muted?' · 静音':''}`;const rs=await (await fetch('/api/resources')).json();$('resources').innerHTML=rs.map(r=>`<div class="resource"><span>${'$'}{r.name}</span><span class="muted">${'$'}{Math.round(r.sizeBytes/1024)} KB</span><button onclick="command('PLAY','${'$'}{r.id}')">播放</button><button onclick="removeResource('${'$'}{r.id}')">删除</button></div>`).join('')||'<span class="muted">暂无资源</span>';$('volume').value=s.volume}async function command(action,resourceId=null,value=null){await fetch('/api/control',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,resourceId,value})});refresh()}async function upload(){const f=$('file').files[0];if(!f)return;const form=new FormData();form.append('file',f);await fetch('/api/resources/upload',{method:'POST',body:form});$('file').value='';refresh()}async function removeResource(id){await fetch('/api/resources/'+id,{method:'DELETE'});refresh()}refresh();setInterval(refresh,3000)</script></body></html>
"""
    }
}
