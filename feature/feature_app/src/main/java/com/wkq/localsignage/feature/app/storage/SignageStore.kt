package com.wkq.localsignage.feature.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.model.ControlSession
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Transactional local store for durable signage state and content metadata. */
class SignageStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = SignageDatabase(appContext)
    private val lock = Any()
    private val resourceDirectory = File(appContext.filesDir, "shared/resources").apply { mkdirs() }
    private val resourceRoot = resourceDirectory.canonicalFile

    init {
        migrateLegacyPreferences()
    }

    fun ensureDefaultContent() = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val scenes = readScenes(this).toMutableList()
            val sceneResources = scenes.mapTo(mutableSetOf()) { it.resourceId }
            readResources(this).filterNot { it.id in sceneResources }.forEach { resource ->
                insertScene(this, SignageScene(UUID.randomUUID().toString(), resource.name, resource.id))
            }
            val refreshedScenes = readScenes(this)
            val playlists = readPlaylists(this)
            val currentPlaylist = playlists.firstOrNull { it.id == getMeta(this, KEY_CURRENT_PLAYLIST) }
                ?: playlists.firstOrNull()
            if (currentPlaylist == null) {
                val playlist = SignagePlaylist(
                    UUID.randomUUID().toString(),
                    "Default",
                    refreshedScenes.map { SignagePlaylistItem(it.id) }
                )
                insertPlaylist(this, playlist)
                setMeta(this, KEY_CURRENT_PLAYLIST, playlist.id)
            } else {
                val validIds = refreshedScenes.mapTo(mutableSetOf()) { it.id }
                val validItems = currentPlaylist.items.filter { it.sceneId in validIds }
                if (validItems != currentPlaylist.items) {
                    replacePlaylist(this, currentPlaylist.copy(items = validItems))
                }
                if (getMeta(this, KEY_CURRENT_PLAYLIST) != currentPlaylist.id) {
                    setMeta(this, KEY_CURRENT_PLAYLIST, currentPlaylist.id)
                }
            }
            if (getMeta(this, KEY_CURRENT_SCENE) == null) {
                refreshedScenes.firstOrNull()?.id?.let { setMeta(this, KEY_CURRENT_SCENE, it) }
            }
        }
    }

    fun deviceId(): String = synchronized(lock) {
        getMeta(database.readableDatabase, KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also {
            setMeta(database.writableDatabase, KEY_DEVICE_ID, it)
        }
    }

    fun deviceName(): String = synchronized(lock) { getMeta(database.readableDatabase, KEY_DEVICE_NAME) ?: "Local Signage" }

    fun controlToken(): String = synchronized(lock) {
        getMeta(database.readableDatabase, KEY_CONTROL_TOKEN) ?: UUID.randomUUID().toString().replace("-", "").also {
            setMeta(database.writableDatabase, KEY_CONTROL_TOKEN, it)
        }
    }

    fun controlSession(): ControlSession? = synchronized(lock) {
        val db = database.readableDatabase
        val sessionId = getMeta(db, KEY_SESSION_ID) ?: return@synchronized null
        val expiresAt = getMeta(db, KEY_SESSION_EXPIRES)?.toLongOrNull() ?: return@synchronized null
        if (expiresAt <= System.currentTimeMillis()) {
            clearSession(db)
            return@synchronized null
        }
        ControlSession(sessionId, getMeta(db, KEY_SESSION_CLIENT) ?: "Unknown", expiresAt)
    }

    fun acquireControlSession(clientName: String, takeover: Boolean): ControlSession? = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val existing = controlSessionLocked(this)
            if (existing != null && !takeover) return@inTransaction null
            val session = ControlSession(
                UUID.randomUUID().toString(),
                clientName.trim().take(MAX_CLIENT_NAME_LENGTH).ifBlank { "Browser" },
                System.currentTimeMillis() + CONTROL_SESSION_TTL_MS
            )
            setMeta(this, KEY_SESSION_ID, session.sessionId)
            setMeta(this, KEY_SESSION_CLIENT, session.clientName)
            setMeta(this, KEY_SESSION_EXPIRES, session.expiresAt.toString())
            session
        }
    }

    fun heartbeatControlSession(sessionId: String): ControlSession? = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val current = controlSessionLocked(this) ?: return@inTransaction null
            if (current.sessionId != sessionId) return@inTransaction null
            val refreshed = current.copy(expiresAt = System.currentTimeMillis() + CONTROL_SESSION_TTL_MS)
            setMeta(this, KEY_SESSION_EXPIRES, refreshed.expiresAt.toString())
            refreshed
        }
    }

    fun releaseControlSession(sessionId: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val current = controlSessionLocked(this) ?: return@inTransaction false
            if (current.sessionId != sessionId) return@inTransaction false
            clearSession(this)
            true
        }
    }

    fun hasControlSession(sessionId: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val current = controlSessionLocked(this) ?: return@inTransaction false
            current.sessionId == sessionId
        }
    }

    fun acceptCommandRevision(revision: Long?): Boolean = synchronized(lock) {
        if (revision == null) return@synchronized true
        database.writableDatabase.inTransaction {
            val current = getMeta(this, KEY_COMMAND_REVISION)?.toLongOrNull() ?: 0L
            if (revision <= current) return@inTransaction false
            setMeta(this, KEY_COMMAND_REVISION, revision.toString())
            true
        }
    }

    fun resources(): List<SignageResource> = synchronized(lock) { readResources(database.readableDatabase) }
    fun resource(id: String?): SignageResource? = synchronized(lock) { readResources(database.readableDatabase).firstOrNull { it.id == id } }

    fun fileFor(resource: SignageResource): File {
        val file = File(resource.path).canonicalFile
        require(file.path == resourceRoot.path || file.path.startsWith(resourceRoot.path + File.separator)) {
            "Resource path is outside the managed directory"
        }
        return file
    }

    fun scenes(): List<SignageScene> = synchronized(lock) { readScenes(database.readableDatabase) }
    fun scene(id: String?): SignageScene? = synchronized(lock) { readScenes(database.readableDatabase).firstOrNull { it.id == id } }
    fun playlists(): List<SignagePlaylist> = synchronized(lock) { readPlaylists(database.readableDatabase) }
    fun playlist(id: String?): SignagePlaylist? = synchronized(lock) { readPlaylists(database.readableDatabase).firstOrNull { it.id == id } }

    fun currentSceneId(): String? = synchronized(lock) { getMeta(database.readableDatabase, KEY_CURRENT_SCENE) }
    fun currentPlaylistId(): String? = synchronized(lock) { getMeta(database.readableDatabase, KEY_CURRENT_PLAYLIST) }
    fun setCurrentSceneId(id: String?) = synchronized(lock) { setMeta(database.writableDatabase, KEY_CURRENT_SCENE, id) }
    fun setCurrentPlaylistId(id: String?) = synchronized(lock) { setMeta(database.writableDatabase, KEY_CURRENT_PLAYLIST, id) }

    fun saveScene(scene: SignageScene): SignageScene = synchronized(lock) {
        database.writableDatabase.inTransaction {
            require(resourceExists(this, scene.resourceId)) { "Resource does not exist" }
            insertScene(this, scene)
        }
        scene
    }

    fun deleteScene(id: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            if (!sceneExists(this, id)) return@inTransaction false
            delete(this, "playlist_items", "scene_id = ?", arrayOf(id))
            delete(this, "scenes", "id = ?", arrayOf(id))
            if (getMeta(this, KEY_CURRENT_SCENE) == id) {
                readScenes(this).firstOrNull()?.id?.let { setMeta(this, KEY_CURRENT_SCENE, it) }
                    ?: setMeta(this, KEY_CURRENT_SCENE, null)
            }
            true
        }
    }

    fun savePlaylist(playlist: SignagePlaylist): SignagePlaylist = synchronized(lock) {
        database.writableDatabase.inTransaction {
            require(playlist.items.all { sceneExists(this, it.sceneId) }) { "Playlist contains an unknown scene" }
            replacePlaylist(this, playlist)
            if (getMeta(this, KEY_CURRENT_PLAYLIST) == null) setMeta(this, KEY_CURRENT_PLAYLIST, playlist.id)
        }
        playlist
    }

    fun deletePlaylist(id: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            if (!playlistExists(this, id)) return@inTransaction false
            delete(this, "playlists", "id = ?", arrayOf(id))
            if (getMeta(this, KEY_CURRENT_PLAYLIST) == id) {
                readPlaylists(this).firstOrNull()?.id?.let { setMeta(this, KEY_CURRENT_PLAYLIST, it) }
                    ?: setMeta(this, KEY_CURRENT_PLAYLIST, null)
            }
            true
        }
    }

    fun importUri(uri: Uri, name: String, mimeType: String): SignageResource =
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            saveUpload(name, mimeType, input)
        }

    fun saveUpload(name: String, mimeType: String, input: InputStream): SignageResource = synchronized(lock) {
        require(mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            "Only image and video resources are supported"
        }
        val id = UUID.randomUUID().toString()
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resource" }
        val temporary = File(resourceDirectory, ".upload-$id.tmp")
        var committedFile: File? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    size += count
                    require(size <= MAX_RESOURCE_BYTES) { "Resource is too large" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            database.writableDatabase.inTransaction {
                require(totalResourceBytes(this) - 0L + size <= MAX_TOTAL_RESOURCE_BYTES) {
                    "Resource storage quota exceeded"
                }
                val duplicate = readResources(this).firstOrNull { it.hash == hash }
                if (duplicate != null) return@inTransaction duplicate
                val resource = SignageResource(id, safeName, normalizeMimeType(mimeType), File(resourceDirectory, "${id}_$safeName").absolutePath, hash, size, System.currentTimeMillis())
                try {
                    Files.move(temporary.toPath(), File(resource.path).toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), File(resource.path).toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                committedFile = File(resource.path)
                insertResource(this, resource)
                val scene = SignageScene(UUID.randomUUID().toString(), resource.name, resource.id)
                insertScene(this, scene)
                readPlaylists(this).firstOrNull()?.let { playlist ->
                    replacePlaylist(this, playlist.copy(items = playlist.items + SignagePlaylistItem(scene.id)))
                }
                if (getMeta(this, KEY_CURRENT_RESOURCE) == null) setMeta(this, KEY_CURRENT_RESOURCE, resource.id)
                if (getMeta(this, KEY_CURRENT_SCENE) == null) setMeta(this, KEY_CURRENT_SCENE, scene.id)
                resource
            }
        } catch (error: Exception) {
            temporary.delete()
            committedFile?.delete()
            throw error
        } finally {
            temporary.delete()
        }
    }

    fun deleteResource(id: String): Boolean = synchronized(lock) {
        val resource = resource(id) ?: return@synchronized false
        val deleted = database.writableDatabase.inTransaction {
            readScenes(this).filter { it.resourceId == id }.forEach { scene ->
                delete(this, "playlist_items", "scene_id = ?", arrayOf(scene.id))
                if (getMeta(this, KEY_CURRENT_SCENE) == scene.id) setMeta(this, KEY_CURRENT_SCENE, null)
                delete(this, "scenes", "id = ?", arrayOf(scene.id))
            }
            delete(this, "resources", "id = ?", arrayOf(id))
            if (getMeta(this, KEY_CURRENT_RESOURCE) == id) {
                readResources(this).firstOrNull()?.id?.let { setMeta(this, KEY_CURRENT_RESOURCE, it) }
                    ?: setMeta(this, KEY_CURRENT_RESOURCE, null)
            }
            if (getMeta(this, KEY_CURRENT_SCENE) == null) {
                readScenes(this).firstOrNull()?.id?.let { setMeta(this, KEY_CURRENT_SCENE, it) }
            }
            true
        }
        if (deleted) fileFor(resource).delete()
        deleted
    }

    fun currentResourceId(): String? = synchronized(lock) { getMeta(database.readableDatabase, KEY_CURRENT_RESOURCE) }
    fun setCurrentResource(id: String?) = synchronized(lock) { setMeta(database.writableDatabase, KEY_CURRENT_RESOURCE, id) }

    fun state(port: Int): SignageState = synchronized(lock) {
        SignageState(
            deviceId(), deviceName(), getMeta(database.readableDatabase, KEY_CURRENT_RESOURCE),
            getMeta(database.readableDatabase, KEY_CURRENT_SCENE), getMeta(database.readableDatabase, KEY_CURRENT_PLAYLIST),
            getMeta(database.readableDatabase, KEY_PLAYING)?.toBoolean() ?: false,
            getMeta(database.readableDatabase, KEY_VOLUME)?.toIntOrNull() ?: 80,
            getMeta(database.readableDatabase, KEY_MUTED)?.toBoolean() ?: false,
            getMeta(database.readableDatabase, KEY_POSITION)?.toLongOrNull() ?: 0L,
            getMeta(database.readableDatabase, KEY_ERROR), port,
            getMeta(database.readableDatabase, KEY_COMMAND_REVISION)?.toLongOrNull() ?: 0L
        )
    }

    fun setPlaying(value: Boolean) = synchronized(lock) { setMeta(database.writableDatabase, KEY_PLAYING, value.toString()) }
    fun setVolume(value: Int) = synchronized(lock) { setMeta(database.writableDatabase, KEY_VOLUME, value.coerceIn(0, 100).toString()) }
    fun setMuted(value: Boolean) = synchronized(lock) { setMeta(database.writableDatabase, KEY_MUTED, value.toString()) }
    fun setPosition(value: Long) = synchronized(lock) { setMeta(database.writableDatabase, KEY_POSITION, value.coerceAtLeast(0L).toString()) }
    fun setError(value: String?) = synchronized(lock) { setMeta(database.writableDatabase, KEY_ERROR, value) }

    private fun controlSessionLocked(db: SQLiteDatabase): ControlSession? {
        val sessionId = getMeta(db, KEY_SESSION_ID) ?: return null
        val expiresAt = getMeta(db, KEY_SESSION_EXPIRES)?.toLongOrNull() ?: return null
        if (expiresAt <= System.currentTimeMillis()) {
            clearSession(db)
            return null
        }
        return ControlSession(sessionId, getMeta(db, KEY_SESSION_CLIENT) ?: "Unknown", expiresAt)
    }

    private fun clearSession(db: SQLiteDatabase) {
        setMeta(db, KEY_SESSION_ID, null)
        setMeta(db, KEY_SESSION_CLIENT, null)
        setMeta(db, KEY_SESSION_EXPIRES, null)
    }

    private fun migrateLegacyPreferences() {
        synchronized(lock) {
        val db = database.writableDatabase
        if (getMeta(db, KEY_SCHEMA_MIGRATED) == "1") return
        val preferences = appContext.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        db.inTransaction {
            val legacyResources = parseResources(preferences.getString(KEY_RESOURCES, null))
            legacyResources.forEach { resource -> if (!resourceExists(this, resource.id)) insertResource(this, resource) }
            val legacySceneIds = parseScenes(preferences.getString(KEY_SCENES, null))
                .filter { resourceExists(this, it.resourceId) }
                .onEach { scene -> if (!sceneExists(this, scene.id)) insertScene(this, scene) }
                .mapTo(mutableSetOf()) { it.id }
            parsePlaylists(preferences.getString(KEY_PLAYLISTS, null)).forEach { playlist ->
                val validPlaylist = playlist.copy(items = playlist.items.filter { it.sceneId in legacySceneIds || sceneExists(this, it.sceneId) })
                if (!playlistExists(this, validPlaylist.id)) insertPlaylist(this, validPlaylist)
            }
            val legacyKeys = listOf(KEY_DEVICE_ID, KEY_DEVICE_NAME, KEY_CONTROL_TOKEN, KEY_CURRENT_RESOURCE, KEY_CURRENT_SCENE, KEY_CURRENT_PLAYLIST, KEY_PLAYING, KEY_VOLUME, KEY_MUTED, KEY_POSITION, KEY_ERROR)
            legacyKeys.forEach { key -> preferences.all[key]?.toString()?.let { setMeta(this, key, it) } }
            setMeta(this, KEY_SCHEMA_MIGRATED, "1")
        }
        }
    }

    private fun readResources(db: SQLiteDatabase): List<SignageResource> = buildList {
        db.query("resources", RESOURCE_COLUMNS, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) add(SignageResource(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getLong(6)))
        }
    }

    private fun readScenes(db: SQLiteDatabase): List<SignageScene> = buildList {
        db.query("scenes", SCENE_COLUMNS, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) add(SignageScene(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getStringOrNull(6), cursor.getIntOrNull(7), cursor.getInt(8) != 0, cursor.getLong(9)))
        }
    }

    private fun readPlaylists(db: SQLiteDatabase): List<SignagePlaylist> = buildList {
        db.query("playlists", arrayOf("id", "name", "loop", "updated_at"), null, null, null, null, "updated_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val items = buildList {
                    db.query("playlist_items", arrayOf("scene_id", "duration_ms", "enabled"), "playlist_id = ?", arrayOf(id), null, null, "position ASC").use { itemCursor ->
                        while (itemCursor.moveToNext()) add(SignagePlaylistItem(itemCursor.getString(0), itemCursor.getLongOrNull(1), itemCursor.getInt(2) != 0))
                    }
                }
                add(SignagePlaylist(id, cursor.getString(1), items, cursor.getInt(2) != 0, cursor.getLong(3)))
            }
        }
    }

    private fun insertResource(db: SQLiteDatabase, resource: SignageResource) {
        db.insertOrThrow("resources", null, ContentValues().apply { put("id", resource.id); put("name", resource.name); put("mime_type", resource.mimeType); put("path", resource.path); put("hash", resource.hash); put("size_bytes", resource.sizeBytes); put("created_at", resource.createdAt) })
    }

    private fun insertScene(db: SQLiteDatabase, scene: SignageScene) {
        db.insertWithOnConflict("scenes", null, ContentValues().apply { put("id", scene.id); put("name", scene.name); put("resource_id", scene.resourceId); put("fit_mode", scene.fitMode); put("crop_gravity", scene.cropGravity); put("background_type", scene.backgroundType); put("background_color", scene.backgroundColor); put("volume", scene.volume); put("muted", if (scene.muted) 1 else 0); put("created_at", scene.createdAt) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertPlaylist(db: SQLiteDatabase, playlist: SignagePlaylist) {
        db.insertOrThrow("playlists", null, ContentValues().apply { put("id", playlist.id); put("name", playlist.name); put("loop", if (playlist.loop) 1 else 0); put("updated_at", playlist.updatedAt) })
        insertPlaylistItems(db, playlist)
    }

    private fun replacePlaylist(db: SQLiteDatabase, playlist: SignagePlaylist) {
        delete(db, "playlist_items", "playlist_id = ?", arrayOf(playlist.id))
        db.insertWithOnConflict("playlists", null, ContentValues().apply { put("id", playlist.id); put("name", playlist.name); put("loop", if (playlist.loop) 1 else 0); put("updated_at", playlist.updatedAt) }, SQLiteDatabase.CONFLICT_REPLACE)
        insertPlaylistItems(db, playlist)
    }

    private fun insertPlaylistItems(db: SQLiteDatabase, playlist: SignagePlaylist) {
        playlist.items.forEachIndexed { index, item ->
            db.insertOrThrow("playlist_items", null, ContentValues().apply { put("playlist_id", playlist.id); put("position", index); put("scene_id", item.sceneId); put("duration_ms", item.durationMs); put("enabled", if (item.enabled) 1 else 0) })
        }
    }

    private fun resourceExists(db: SQLiteDatabase, id: String) = exists(db, "resources", "id = ?", arrayOf(id))
    private fun sceneExists(db: SQLiteDatabase, id: String) = exists(db, "scenes", "id = ?", arrayOf(id))
    private fun playlistExists(db: SQLiteDatabase, id: String) = exists(db, "playlists", "id = ?", arrayOf(id))
    private fun exists(db: SQLiteDatabase, table: String, selection: String, args: Array<String>): Boolean = db.query(table, arrayOf("1"), selection, args, null, null, null, "1").use { it.moveToFirst() }
    private fun totalResourceBytes(db: SQLiteDatabase): Long = db.rawQuery("SELECT COALESCE(SUM(size_bytes), 0) FROM resources", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    private fun getMeta(db: SQLiteDatabase, key: String): String? = db.query("meta", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }
    private fun setMeta(db: SQLiteDatabase, key: String, value: String?) { if (value == null) delete(db, "meta", "key = ?", arrayOf(key)) else db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    private fun delete(db: SQLiteDatabase, table: String, where: String, args: Array<String>) { db.delete(table, where, args) }
    private fun normalizeMimeType(value: String) = value.lowercase().substringBefore(';')

    private class SignageDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE resources (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, mime_type TEXT NOT NULL, path TEXT NOT NULL, hash TEXT NOT NULL UNIQUE, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE scenes (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, resource_id TEXT NOT NULL, fit_mode TEXT NOT NULL, crop_gravity TEXT NOT NULL, background_type TEXT NOT NULL, background_color TEXT, volume INTEGER, muted INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(resource_id) REFERENCES resources(id))")
            db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, loop INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, scene_id TEXT NOT NULL, duration_ms INTEGER, enabled INTEGER NOT NULL, PRIMARY KEY(playlist_id, position), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE, FOREIGN KEY(scene_id) REFERENCES scenes(id))")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL("CREATE INDEX scenes_resource_idx ON scenes(resource_id)")
            db.execSQL("CREATE INDEX playlist_items_scene_idx ON playlist_items(scene_id)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS resources_hash_idx ON resources(hash)") }
    }

    private fun parseResources(raw: String?): List<SignageResource> = runCatching { JSONArray(raw ?: "[]").let { array -> buildList { for (i in 0 until array.length()) { val item = array.getJSONObject(i); add(SignageResource(item.getString("id"), item.getString("name"), item.getString("mimeType"), item.getString("path"), item.getString("hash"), item.getLong("sizeBytes"), item.getLong("createdAt"))) } } } }.getOrDefault(emptyList())
    private fun parseScenes(raw: String?): List<SignageScene> = runCatching { JSONArray(raw ?: "[]").let { array -> buildList { for (i in 0 until array.length()) { val item = array.getJSONObject(i); add(SignageScene(item.getString("id"), item.getString("name"), item.getString("resourceId"), item.optString("fitMode", "FIT"), item.optString("cropGravity", "CENTER"), item.optString("backgroundType", "BLACK"), item.optString("backgroundColor").ifBlank { null }, if (item.has("volume") && !item.isNull("volume")) item.getInt("volume") else null, item.optBoolean("muted", false), item.optLong("createdAt", System.currentTimeMillis()))) } } } }.getOrDefault(emptyList())
    private fun parsePlaylists(raw: String?): List<SignagePlaylist> = runCatching { JSONArray(raw ?: "[]").let { array -> buildList { for (i in 0 until array.length()) { val item = array.getJSONObject(i); val jsonItems = item.optJSONArray("items") ?: JSONArray(); add(SignagePlaylist(item.getString("id"), item.getString("name"), buildList { for (j in 0 until jsonItems.length()) { val child = jsonItems.getJSONObject(j); add(SignagePlaylistItem(child.getString("sceneId"), if (child.has("durationMs") && !child.isNull("durationMs")) child.getLong("durationMs") else null, child.optBoolean("enabled", true))) } }, item.optBoolean("loop", true), item.optLong("updatedAt", System.currentTimeMillis()))) } } } }.getOrDefault(emptyList())

    private fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }
    private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun android.database.Cursor.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
    private fun android.database.Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private companion object {
        const val DATABASE_NAME = "signage.db"
        const val DATABASE_VERSION = 2
        const val LEGACY_PREFERENCES = "local_signage"
        const val KEY_SCHEMA_MIGRATED = "schema_migrated"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_CONTROL_TOKEN = "control_token"
        const val KEY_RESOURCES = "resources"
        const val KEY_CURRENT_RESOURCE = "current_resource"
        const val KEY_CURRENT_SCENE = "current_scene"
        const val KEY_CURRENT_PLAYLIST = "current_playlist"
        const val KEY_SCENES = "scenes"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_PLAYING = "playing"
        const val KEY_VOLUME = "volume"
        const val KEY_MUTED = "muted"
        const val KEY_POSITION = "position"
        const val KEY_ERROR = "error"
        const val KEY_COMMAND_REVISION = "command_revision"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SESSION_CLIENT = "session_client"
        const val KEY_SESSION_EXPIRES = "session_expires"
        const val CONTROL_SESSION_TTL_MS = 60_000L
        const val MAX_CLIENT_NAME_LENGTH = 80
        const val MAX_RESOURCE_BYTES = 200L * 1024L * 1024L
        const val MAX_TOTAL_RESOURCE_BYTES = 2L * 1024L * 1024L * 1024L
        val RESOURCE_COLUMNS = arrayOf("id", "name", "mime_type", "path", "hash", "size_bytes", "created_at")
        val SCENE_COLUMNS = arrayOf("id", "name", "resource_id", "fit_mode", "crop_gravity", "background_type", "background_color", "volume", "muted", "created_at")
    }
}
