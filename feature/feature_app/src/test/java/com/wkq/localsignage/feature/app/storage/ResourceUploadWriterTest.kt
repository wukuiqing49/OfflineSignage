package com.wkq.localsignage.feature.app.storage

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceUploadWriterTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun commitsCompleteContentWithHashAtExactSizeLimit() {
        val lock = Any()
        val result = ResourceUploadWriter(folder.root, lock, 3).save(ByteArrayInputStream("abc".toByteArray())) { file, hash, size ->
            assertTrue(Thread.holdsLock(lock))
            assertEquals("abc", file.readText())
            assertEquals(3L, size)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
            "committed"
        }
        assertEquals("committed", result)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun rejectsOversizedInputWithoutCommittingAndCleansTemporaryFile() {
        assertThrows(IllegalArgumentException::class.java) {
            ResourceUploadWriter(folder.root, Any(), 2).save(ByteArrayInputStream("abc".toByteArray())) { _, _, _ ->
                fail("Oversized input must not commit")
            }
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun interruptedReadCleansTemporaryFileWithoutCommitting() {
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("interrupted upload")
        }
        assertThrows(IOException::class.java) {
            ResourceUploadWriter(folder.root, Any(), 10).save(input) { _, _, _ ->
                fail("Incomplete input must not commit")
            }
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun failedCommitCleansTemporaryFileAndPreservesError() {
        val error = IOException("database commit failed")
        val actual = assertThrows(IOException::class.java) {
            ResourceUploadWriter(folder.root, Any(), 10).save(ByteArrayInputStream(byteArrayOf(1))) { _, _, _ ->
                throw error
            }
        }
        assertSame(error, actual)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun slowUploadLeavesStateLockAvailable() {
        val lock = Any()
        val reading = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val input = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                check(resume.await(5, TimeUnit.SECONDS)) { "Upload was not resumed" }
                return -1
            }
        }
        try {
            val upload = executor.submit<String> {
                ResourceUploadWriter(folder.root, lock, 10).save(input) { _, _, _ -> "uploaded" }
            }
            assertTrue(reading.await(3, TimeUnit.SECONDS))
            val stateRead = executor.submit<String> { synchronized(lock) { "playing" } }
            assertEquals("playing", stateRead.get(3, TimeUnit.SECONDS))
            resume.countDown()
            assertEquals("uploaded", upload.get(3, TimeUnit.SECONDS))
        } finally {
            resume.countDown()
            executor.shutdownNow()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }
}
