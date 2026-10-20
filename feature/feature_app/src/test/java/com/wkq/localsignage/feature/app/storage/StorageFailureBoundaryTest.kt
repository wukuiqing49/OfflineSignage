package com.wkq.localsignage.feature.app.storage

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StorageFailureBoundaryTest {
    @Test fun initializationFailureCanBeRetriedWithoutReplacingTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val store = SignageStore(context)
        val runtime = com.wkq.localsignage.feature.app.runtime.SignageRuntime
        val runtimeField = runtime::class.java.getDeclaredField("store").apply { isAccessible = true }
        val previous = runtimeField.get(runtime)
        val databaseField = SignageStore::class.java.getDeclaredField("database").apply { isAccessible = true }
        val db = (databaseField.get(store) as SQLiteOpenHelper).writableDatabase
        runtimeField.set(runtime, store)
        try {
            db.execSQL("PRAGMA query_only = ON")
            assertFalse(runtime.tryInitialize(context))
            assertSame(store, runtimeField.get(runtime))
            db.execSQL("PRAGMA query_only = OFF")
            assertTrue(runtime.tryInitialize(context))
            assertSame(store, runtimeField.get(runtime))
            assertTrue(runtime.playlists().isNotEmpty())
        } finally {
            db.execSQL("PRAGMA query_only = OFF")
            runtimeField.set(runtime, previous)
            store.close()
        }
    }

    @Test fun diagnosticWritesSurviveReadOnlyDatabaseAndRecover() {
        val store = SignageStore(RuntimeEnvironment.getApplication())
        store.ensureDefaultContent()
        val before = store.state(8080)
        val field = SignageStore::class.java.getDeclaredField("database").apply { isAccessible = true }
        val db: SQLiteDatabase = (field.get(store) as SQLiteOpenHelper).writableDatabase
        try {
            db.execSQL("PRAGMA query_only = ON")
            store.setError("WEB_TIMEOUT")
            assertEquals("WEB_TIMEOUT", store.state(8080).error)
            store.recordPlaybackError(null, null, "WEB_TIMEOUT", "RETRY", 1)
            assertEquals("PLAYBACK_ERROR_HISTORY_WRITE_FAILED", store.state(8080).error)
            assertEquals(before.deviceId, store.state(8080).deviceId)
            db.execSQL("PRAGMA query_only = OFF")
            store.recordPlaybackError(null, null, "WEB_TIMEOUT", "RETRY", 2)
            store.setError(null)
            assertNull(store.state(8080).error)
            assertTrue(store.playbackErrors().any { it.attempt == 2 })
        } finally {
            db.execSQL("PRAGMA query_only = OFF")
            store.close()
        }
    }

    @Test fun programmingErrorsAreNotHidden() {
        val expected = IllegalStateException("Unexpected invariant")
        try {
            storageAttempt({ fail("Not a storage failure") }) { throw expected }
            fail("Must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
    }

    @Test fun sqliteFailureIsReported() {
        var reported = false
        assertFalse(storageAttempt({ reported = true }) { throw SQLiteException("disk full") })
        assertTrue(reported)
    }
}
