package com.wkq.localsignage.feature.app.storage

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** 上传暂存不持有状态锁，仅在提交元数据时串行化，避免慢速上传阻塞播放。 */
internal class ResourceUploadWriter(
    private val directory: File,
    private val commitLock: Any,
    private val maxBytes: Long
) {
    fun <T> save(input: InputStream, commit: (File, String, Long) -> T): T {
        val temporary = File.createTempFile(".upload-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    require(count.toLong() <= maxBytes - size) { "Resource is too large" }
                    size += count
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return synchronized(commitLock) { commit(temporary, hash, size) }
        } finally {
            // 输入流由调用方管理；成功、重复、超限和异常均清理暂存文件。
            temporary.delete()
        }
    }
}
