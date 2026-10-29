package com.wkq.localsignage.feature.app.storage

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wkq.localsignage.feature.app.model.SceneLayoutTemplate
import org.json.JSONArray

// Schema 与升级路径集中维护；数据库名称、版本及 SQL 保持兼容。
internal class SignageDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE resources (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, mime_type TEXT NOT NULL, path TEXT NOT NULL, hash TEXT NOT NULL UNIQUE, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'LOCAL_FILE', source_uri TEXT, content TEXT, refresh_interval_ms INTEGER, text_size_sp INTEGER NOT NULL DEFAULT 48, text_color TEXT NOT NULL DEFAULT '#FFFFFFFF', text_background_color TEXT NOT NULL DEFAULT '#FF000000', font_family TEXT NOT NULL DEFAULT 'SYSTEM_SANS', text_speed_dp_per_second INTEGER NOT NULL DEFAULT 90, text_repeat_count INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE scenes (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, resource_id TEXT NOT NULL, fit_mode TEXT NOT NULL, crop_gravity TEXT NOT NULL, background_type TEXT NOT NULL, background_color TEXT, volume INTEGER, muted INTEGER NOT NULL, created_at INTEGER NOT NULL, overlays_json TEXT NOT NULL DEFAULT '[]', playback_speed REAL NOT NULL DEFAULT 1.0, transition_effect TEXT NOT NULL DEFAULT 'FADE', layout_template TEXT NOT NULL DEFAULT 'FULLSCREEN', sidebar_resource_id TEXT, FOREIGN KEY(resource_id) REFERENCES resources(id), FOREIGN KEY(sidebar_resource_id) REFERENCES resources(id))")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, loop INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, scene_id TEXT NOT NULL, duration_ms INTEGER, enabled INTEGER NOT NULL, PRIMARY KEY(playlist_id, position), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE, FOREIGN KEY(scene_id) REFERENCES scenes(id))")
        db.execSQL("CREATE TABLE playlist_schedules (id TEXT PRIMARY KEY NOT NULL, playlist_id TEXT NOT NULL, weekdays TEXT NOT NULL, start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, priority INTEGER NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playback_errors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, media_id TEXT, scene_id TEXT, error_code TEXT NOT NULL, action TEXT NOT NULL, attempt INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE paired_devices (device_id TEXT PRIMARY KEY NOT NULL, device_name TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, token TEXT NOT NULL, paired_at INTEGER NOT NULL, group_name TEXT)")
        db.execSQL("CREATE TABLE device_assignments (device_id TEXT PRIMARY KEY NOT NULL, playlist_id TEXT NOT NULL, desired_revision INTEGER NOT NULL, desired_playing INTEGER NOT NULL, state TEXT NOT NULL, applied_revision INTEGER, last_sync_at INTEGER, last_error TEXT, FOREIGN KEY(device_id) REFERENCES paired_devices(device_id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE command_results (command_id TEXT PRIMARY KEY NOT NULL, fingerprint TEXT NOT NULL, response TEXT NOT NULL, status_code INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE operation_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, created_at INTEGER NOT NULL, client_name TEXT NOT NULL, device_id TEXT NOT NULL, action TEXT NOT NULL, result TEXT NOT NULL, status_code INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX scenes_resource_idx ON scenes(resource_id)")
        db.execSQL("CREATE INDEX playlist_items_scene_idx ON playlist_items(scene_id)")
        db.execSQL("CREATE INDEX playlist_schedules_playlist_idx ON playlist_schedules(playlist_id)")
        db.execSQL("CREATE INDEX playback_errors_created_idx ON playback_errors(created_at DESC)")
        db.execSQL("CREATE INDEX operation_records_created_idx ON operation_records(created_at DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS resources_hash_idx ON resources(hash)")
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS playback_errors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, media_id TEXT, scene_id TEXT, error_code TEXT NOT NULL, action TEXT NOT NULL, attempt INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS playback_errors_created_idx ON playback_errors(created_at DESC)")
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS paired_devices (device_id TEXT PRIMARY KEY NOT NULL, device_name TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, token TEXT NOT NULL, paired_at INTEGER NOT NULL)")
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE IF NOT EXISTS command_results (command_id TEXT PRIMARY KEY NOT NULL, fingerprint TEXT NOT NULL, response TEXT NOT NULL, status_code INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE resources ADD COLUMN kind TEXT NOT NULL DEFAULT 'LOCAL_FILE'")
            db.execSQL("ALTER TABLE resources ADD COLUMN source_uri TEXT")
            db.execSQL("ALTER TABLE resources ADD COLUMN content TEXT")
            db.execSQL("ALTER TABLE resources ADD COLUMN refresh_interval_ms INTEGER")
            db.execSQL("ALTER TABLE scenes ADD COLUMN overlays_json TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE resources ADD COLUMN text_size_sp INTEGER NOT NULL DEFAULT 48")
            db.execSQL("ALTER TABLE resources ADD COLUMN text_color TEXT NOT NULL DEFAULT '#FFFFFFFF'")
            db.execSQL("ALTER TABLE resources ADD COLUMN text_background_color TEXT NOT NULL DEFAULT '#FF000000'")
            db.execSQL("ALTER TABLE resources ADD COLUMN font_family TEXT NOT NULL DEFAULT 'SYSTEM_SANS'")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE resources ADD COLUMN text_speed_dp_per_second INTEGER NOT NULL DEFAULT 90")
            db.execSQL("ALTER TABLE resources ADD COLUMN text_repeat_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE scenes ADD COLUMN playback_speed REAL NOT NULL DEFAULT 1.0")
        }
        if (oldVersion < 9) {
            db.execSQL("CREATE TABLE IF NOT EXISTS device_assignments (device_id TEXT PRIMARY KEY NOT NULL, playlist_id TEXT NOT NULL, desired_revision INTEGER NOT NULL, desired_playing INTEGER NOT NULL, state TEXT NOT NULL, applied_revision INTEGER, last_sync_at INTEGER, last_error TEXT, FOREIGN KEY(device_id) REFERENCES paired_devices(device_id) ON DELETE CASCADE)")
        }
        if (oldVersion < 10) {
            db.execSQL("CREATE TABLE IF NOT EXISTS operation_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, created_at INTEGER NOT NULL, client_name TEXT NOT NULL, device_id TEXT NOT NULL, action TEXT NOT NULL, result TEXT NOT NULL, status_code INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS operation_records_created_idx ON operation_records(created_at DESC)")
        }
        if (oldVersion < 11) removeLegacyHtmlOverlays(db)
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE scenes ADD COLUMN transition_effect TEXT NOT NULL DEFAULT 'FADE'")
        }
        if (oldVersion < 13) db.execSQL("ALTER TABLE paired_devices ADD COLUMN group_name TEXT")
        if (oldVersion < 14) {
            db.execSQL("CREATE TABLE IF NOT EXISTS playlist_schedules (id TEXT PRIMARY KEY NOT NULL, playlist_id TEXT NOT NULL, weekdays TEXT NOT NULL, start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, priority INTEGER NOT NULL, enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS playlist_schedules_playlist_idx ON playlist_schedules(playlist_id)")
        }
        if (oldVersion < 15) {
            db.execSQL("ALTER TABLE scenes ADD COLUMN layout_template TEXT NOT NULL DEFAULT '${SceneLayoutTemplate.FULLSCREEN}'")
            db.execSQL("ALTER TABLE scenes ADD COLUMN sidebar_resource_id TEXT REFERENCES resources(id)")
        }
    }

    private fun removeLegacyHtmlOverlays(db: SQLiteDatabase) {
        db.query("scenes", arrayOf("id", "overlays_json"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val sceneId = cursor.getString(0)
                val source = runCatching { JSONArray(cursor.getString(1)) }.getOrNull() ?: continue
                val retained = JSONArray()
                for (index in 0 until source.length()) {
                    val overlay = source.optJSONObject(index) ?: continue
                    if (!overlay.optString("type").equals("HTML", ignoreCase = true)) retained.put(overlay)
                }
                if (retained.length() != source.length()) {
                    db.update("scenes", ContentValues().apply {
                        put("overlays_json", retained.toString())
                    }, "id = ?", arrayOf(sceneId))
                }
            }
        }
    }
    companion object {
        const val DATABASE_NAME = "signage.db"
        const val DATABASE_VERSION = 15
    }
}
