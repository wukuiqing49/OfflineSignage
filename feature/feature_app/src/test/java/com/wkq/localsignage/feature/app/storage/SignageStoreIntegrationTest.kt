package com.wkq.localsignage.feature.app.storage

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], application = Application::class)
class SignageStoreIntegrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun internationalUploadNameIsPreservedWithoutUsingItAsADiskPath() {
        SignageStore(context).use { store ->
            val name = "广告_日本語_é_🌏.png"
            val resource = store.saveUpload("../../$name", "image/png", ByteArrayInputStream(byteArrayOf(1)))
            assertEquals(name, resource.name)
            assertEquals(name, store.scenes().single().name)
            assertEquals(context.filesDir.resolve("shared/resources").canonicalFile, store.fileFor(resource).canonicalFile.parentFile)
            assertTrue(store.fileFor(resource).name.matches(Regex("[A-Za-z0-9._-]+")))
            val longName = "图".repeat(200) + ".png"
            val longResource = store.saveUpload(longName, "image/png", ByteArrayInputStream(byteArrayOf(2)))
            assertEquals(120, longResource.name.length)
            assertTrue(store.fileFor(longResource).isFile)
        }
    }

    @Test fun concurrentDuplicateUploadsCommitOneResourceAndOneScene() {
        SignageStore(context).use { store ->
            val executor = Executors.newFixedThreadPool(2)
            val start = CountDownLatch(1)
            try {
                val uploads = (1..2).map {
                    executor.submit<String> {
                        start.await()
                        store.saveUpload("image.png", "image/png", ByteArrayInputStream(byteArrayOf(1, 2, 3))).id
                    }
                }
                start.countDown()
                assertEquals(uploads[0].get(10, TimeUnit.SECONDS), uploads[1].get(10, TimeUnit.SECONDS))
                assertEquals(1, store.resources().size)
                assertEquals(1, store.scenes().size)
                assertEquals(1, context.filesDir.resolve("shared/resources").listFiles()!!.size)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun failedDatabaseCommitRollsBackResourceAndFile() {
        SignageStore(context).use { store ->
            context.openOrCreateDatabase("signage.db", 0, null).use { db ->
                db.execSQL("CREATE TRIGGER reject_scene BEFORE INSERT ON scenes BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            }
            assertThrows(Exception::class.java) {
                store.saveUpload("image.png", "image/png", ByteArrayInputStream(byteArrayOf(1)))
            }
            assertTrue(store.resources().isEmpty())
            assertTrue(context.filesDir.resolve("shared/resources").listFiles()!!.isEmpty())
        }
    }

    @Test fun latestPositionSurvivesReopenAndOldPositionDoesNotCrossScenes() {
        SignageStore(context).use { store ->
            val first = store.saveUpload("first.png", "image/png", ByteArrayInputStream(byteArrayOf(1)))
            val second = store.saveUpload("second.png", "image/png", ByteArrayInputStream(byteArrayOf(2)))
            val scene = store.scenes().first { it.resourceId == second.id }
            repeat(500) { store.setPosition(it.toLong()) }
            store.setPlaybackSelection(second.id, scene.id, null)
            assertEquals(0L, store.state(8080).positionMs)
            store.setPosition(1234)
            assertEquals(1234L, store.state(8080).positionMs)
            assertNotEquals(first.id, store.state(8080).currentResourceId)
        }
        SignageStore(context).use { assertEquals(1234L, it.state(8080).positionMs) }
    }

    @Test fun versionOneDatabaseUpgradesWithoutLosingPlaylistContent() {
        val path = context.getDatabasePath("signage.db")
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE resources (id TEXT PRIMARY KEY, name TEXT NOT NULL, mime_type TEXT NOT NULL, path TEXT NOT NULL, hash TEXT NOT NULL UNIQUE, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE scenes (id TEXT PRIMARY KEY, name TEXT NOT NULL, resource_id TEXT NOT NULL, fit_mode TEXT NOT NULL, crop_gravity TEXT NOT NULL, background_type TEXT NOT NULL, background_color TEXT, volume INTEGER, muted INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, loop INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, scene_id TEXT NOT NULL, duration_ms INTEGER, enabled INTEGER NOT NULL, PRIMARY KEY(playlist_id, position))")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO resources VALUES ('resource', 'Legacy image', 'image/png', '/legacy/image.png', 'hash', 3, 1)")
            db.execSQL("INSERT INTO scenes VALUES ('scene', 'Legacy scene', 'resource', 'FIT', 'CENTER', 'BLACK', NULL, NULL, 0, 1)")
            db.execSQL("INSERT INTO playlists VALUES ('playlist', 'Legacy playlist', 1, 1)")
            db.execSQL("INSERT INTO playlist_items VALUES ('playlist', 0, 'scene', 10000, 1)")
            db.version = 1
        }
        SignageStore(context).use { store ->
            assertEquals("Legacy image", store.resource("resource")!!.name)
            assertEquals("FADE", store.scene("scene")!!.transitionEffect)
            assertEquals("scene", store.playlist("playlist")!!.items.single().sceneId)
            assertTrue(store.operationRecords().isEmpty())
        }
        context.openOrCreateDatabase("signage.db", 0, null).use { assertEquals(14, it.version) }
    }

    @Test fun checkpointFailureIsVisibleAndLaterWriteCanRecover() {
        SignageStore(context).use { store ->
            context.openOrCreateDatabase("signage.db", 0, null).use { db ->
                db.execSQL("CREATE TRIGGER reject_position BEFORE INSERT ON meta WHEN NEW.key = 'position' BEGIN SELECT RAISE(ABORT, 'disk failure'); END")
            }
            fun awaitCondition(condition: () -> Boolean) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
                assertTrue(condition())
            }
            store.setPosition(13)
            awaitCondition { store.state(8080).error == "PLAYBACK_CHECKPOINT_WRITE_FAILED" }
            context.openOrCreateDatabase("signage.db", 0, null).use { it.execSQL("DROP TRIGGER reject_position") }
            store.setPosition(14)
            awaitCondition { store.state(8080).error == null }
        }
        SignageStore(context).use { assertEquals(14L, it.state(8080).positionMs) }
    }
}
