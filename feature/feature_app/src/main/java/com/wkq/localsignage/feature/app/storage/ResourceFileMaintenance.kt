package com.wkq.localsignage.feature.app.storage

import java.io.File

/** 仅回收本应用生成且超过保留期的未引用文件，不触碰目录、链接或用户其他文件。 */
internal object ResourceFileMaintenance {
    private val ownedName = Regex("(?:\\.(?:upload|remote)-.+\\.tmp|[0-9a-fA-F-]{36}_.+)")
    const val RETENTION_MS = 24 * 60 * 60 * 1_000L

    fun clean(directory: File, referencedPaths: Set<String>, now: Long = System.currentTimeMillis()): Int {
        val root = directory.canonicalFile
        var removed = 0
        directory.listFiles()?.forEach { file ->
            if (!file.isFile || !ownedName.matches(file.name)) return@forEach
            val canonical = file.canonicalFile
            if (canonical.parentFile != root || canonical != File(root, file.name)) return@forEach
            if (canonical.path in referencedPaths || now - file.lastModified() < RETENTION_MS) return@forEach
            check(file.delete()) { "RESOURCE_ORPHAN_DELETE_FAILED" }
            removed += 1
        }
        return removed
    }
}
