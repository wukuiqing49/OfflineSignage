package com.wkq.localsignage.feature.app.device

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection

/** 设备凭据只发送到验证过的目标；流式发送大文件并限制响应内存。 */
internal class DeviceHttpTransport(private val open: (String, String) -> HttpURLConnection) {
    data class Response(val status: Int, val body: String)

    fun request(method: String, path: String, body: String? = null): Response {
        val bytes = body?.toByteArray(Charsets.UTF_8)
        return execute(method, path, if (bytes == null) null else "application/json; charset=UTF-8",
            bytes?.size?.toLong(), bytes?.let { data -> { output: OutputStream -> output.write(data) } })
    }

    fun execute(
        method: String,
        path: String,
        contentType: String? = null,
        contentLength: Long? = null,
        writeBody: ((OutputStream) -> Unit)? = null
    ): Response {
        var connection: HttpURLConnection? = null
        return try {
            val current = open(method, path)
            connection = current
            current.instanceFollowRedirects = false
            if (writeBody != null) {
                current.doOutput = true
                current.setRequestProperty("Content-Type", contentType)
                if (contentLength != null) current.setFixedLengthStreamingMode(contentLength)
                else current.setChunkedStreamingMode(64 * 1024)
                current.outputStream.use(writeBody)
            }
            val status = current.responseCode
            val stream = if (status in 200..299) current.inputStream else current.errorStream
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("DEVICE_RESPONSE_TOO_LARGE")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }.orEmpty()
            Response(status, body)
        } catch (_: Exception) {
            Response(-1, "")
        } finally {
            connection?.disconnect()
        }
    }

    companion object { const val MAX_RESPONSE_BYTES = 1024 * 1024 }
}
