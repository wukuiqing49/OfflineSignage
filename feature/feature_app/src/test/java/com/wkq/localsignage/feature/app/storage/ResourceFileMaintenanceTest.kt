package com.wkq.localsignage.feature.app.storage

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceFileMaintenanceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun removesOnlyOldOwnedUnreferencedFiles() {
        val now = System.currentTimeMillis()
        val old = now - ResourceFileMaintenance.RETENTION_MS - 1_000
        fun file(name: String, age: Long = old) = File(folder.root, name).apply {
            writeText("content"); assertTrue(setLastModified(age))
        }
        val temporary = file(".upload-aborted.tmp")
        val orphan = file("12345678-1234-1234-1234-123456789abc_clip.mp4")
        val live = file("12345678-1234-1234-1234-123456789abc_live.mp4")
        val recent = file(".remote-active.tmp", now)
        val unknown = file("customer-note.txt")
        val directory = File(folder.root, ".upload-directory.tmp").apply { mkdir() }
        assertEquals(2, ResourceFileMaintenance.clean(folder.root, setOf(live.canonicalPath), now))
        assertFalse(temporary.exists()); assertFalse(orphan.exists())
        assertTrue(live.exists()); assertTrue(recent.exists()); assertTrue(unknown.exists()); assertTrue(directory.exists())
    }
}
