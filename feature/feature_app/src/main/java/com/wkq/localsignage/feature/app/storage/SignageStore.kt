package com.wkq.localsignage.feature.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.StatFs
import android.util.Log
import android.util.Base64
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.model.ControlSession
import com.wkq.localsignage.feature.app.model.PlaybackErrorRecord
import com.wkq.localsignage.feature.app.model.SignageSettings
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.model.PlaybackTimingPolicy
import com.wkq.localsignage.feature.app.model.PlaylistPolicy
import com.wkq.localsignage.feature.app.model.ResourceKind
import com.wkq.localsignage.feature.app.model.SecondPhasePolicy
import com.wkq.localsignage.feature.app.model.SignageOverlay
import com.wkq.localsignage.feature.app.model.TextStylePolicy
import com.wkq.localsignage.feature.app.security.LocalSecretCipher
import com.wkq.localsignage.feature.app.security.CredentialDecryptionException
import com.wkq.localsignage.feature.app.security.CommandRevisionPolicy
import com.wkq.localsignage.feature.app.security.ExpiringTokenPolicy
import com.wkq.localsignage.feature.app.security.AccessTokenHistory
import com.wkq.localsignage.feature.app.security.ExpiringAccessToken
import com.wkq.localsignage.feature.app.security.PairingAccessCode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class TemporaryPairingToken(val token: String, val expiresAt: Long)
data class ResourceStorageSummary(val usedBytes: Long, val availableBytes: Long, val quotaBytes: Long)

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
            if (playlists.isEmpty()) {
                val playlist = SignagePlaylist(
                    UUID.randomUUID().toString(),
                    "Default",
                    refreshedScenes.map { SignagePlaylistItem(it.id) }
                )
                insertPlaylist(this, playlist)
                setMeta(this, KEY_CURRENT_PLAYLIST, playlist.id)
            } else if (currentPlaylist != null) {
                val validIds = refreshedScenes.mapTo(mutableSetOf()) { it.id }
                val validItems = currentPlaylist.items.filter { it.sceneId in validIds }
                if (validItems != currentPlaylist.items) {
                    replacePlaylist(this, currentPlaylist.copy(items = validItems))
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
        val stored = getMeta(database.readableDatabase, KEY_CONTROL_TOKEN)
        if (stored == null) {
            return@synchronized UUID.randomUUID().toString().replace("-", "").also {
                setMeta(database.writableDatabase, KEY_CONTROL_TOKEN, LocalSecretCipher.encrypt(it))
            }
        }
        try {
            LocalSecretCipher.decrypt(stored).also { token ->
                if (!LocalSecretCipher.isEncrypted(stored)) {
                    setMeta(database.writableDatabase, KEY_CONTROL_TOKEN, LocalSecretCipher.encrypt(token))
                }
            }
        } catch (error: CredentialDecryptionException) {
            recoverEncryptedCredentials(error)
        }
    }

    /** Short-lived browser credential; the durable device token stays internal. */
    fun webAccessToken(): String = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = getMeta(database.readableDatabase, KEY_WEB_ACCESS_TOKEN)
        val expiresAt = getMeta(database.readableDatabase, KEY_WEB_ACCESS_EXPIRES)?.toLongOrNull() ?: 0L
        if (!current.isNullOrBlank() && expiresAt > now) return@synchronized current
        issueWebAccessTokenLocked(database.writableDatabase, now)
    }

    fun rotateWebAccessToken(): String = synchronized(lock) {
        issueWebAccessTokenLocked(database.writableDatabase, System.currentTimeMillis())
    }

    fun revokeWebAccessToken() = synchronized(lock) {
        setMeta(database.writableDatabase, KEY_WEB_ACCESS_TOKEN, null)
        setMeta(database.writableDatabase, KEY_WEB_ACCESS_EXPIRES, null)
        setMeta(database.writableDatabase, KEY_WEB_ACCESS_HISTORY, null)
    }

    fun hasWebAccessToken(token: String?): Boolean = synchronized(lock) {
        val normalized = token?.trim().orEmpty()
        if (normalized.isEmpty()) return@synchronized false
        val now = System.currentTimeMillis()
        val current = getMeta(database.readableDatabase, KEY_WEB_ACCESS_TOKEN)
        val currentExpires = getMeta(database.readableDatabase, KEY_WEB_ACCESS_EXPIRES)?.toLongOrNull() ?: 0L
        val currentMatch = current != null && currentExpires > now &&
            MessageDigest.isEqual(normalized.toByteArray(), current.toByteArray())
        if (currentMatch) return@synchronized true
        AccessTokenHistory.contains(readWebAccessHistory(database.readableDatabase), normalized, now)
    }

    /** Rotates the independent QR and computer-code credentials together. */
    fun issuePairingToken(): TemporaryPairingToken = synchronized(lock) {
        val now = System.currentTimeMillis()
        val pairing = TemporaryPairingToken(randomToken(), now + PAIRING_TOKEN_TTL_MS)
        val accessCode = TemporaryPairingToken(randomToken(), now + PAIRING_TOKEN_TTL_MS)
        database.writableDatabase.inTransaction {
            setMeta(this, KEY_PAIRING_TOKEN, pairing.token)
            setMeta(this, KEY_PAIRING_EXPIRES, pairing.expiresAt.toString())
            setMeta(this, KEY_PAIRING_CODE_TOKEN, accessCode.token)
            setMeta(this, KEY_PAIRING_CODE_EXPIRES, accessCode.expiresAt.toString())
        }
        pairing
    }

    /** Consumes the active pairing credential exactly once. */
    fun consumePairingToken(token: String?): Boolean = synchronized(lock) {
        val normalized = token?.trim().orEmpty()
        if (normalized.isBlank()) return@synchronized false
        database.writableDatabase.inTransaction {
            val current = getMeta(this, KEY_PAIRING_TOKEN)
            val expiresAt = getMeta(this, KEY_PAIRING_EXPIRES)?.toLongOrNull() ?: 0L
            val valid = ExpiringTokenPolicy.matches(current, expiresAt, normalized, System.currentTimeMillis())
            if (valid) {
                setMeta(this, KEY_PAIRING_TOKEN, null)
                setMeta(this, KEY_PAIRING_EXPIRES, null)
            }
            valid
        }
    }

    fun consumePairingCode(code: String?): Boolean = synchronized(lock) {
        if (code.isNullOrBlank()) return@synchronized false
        database.writableDatabase.inTransaction {
            val current = getMeta(this, KEY_PAIRING_CODE_TOKEN)
            val expiresAt = getMeta(this, KEY_PAIRING_CODE_EXPIRES)?.toLongOrNull() ?: 0L
            val valid = expiresAt > System.currentTimeMillis() && PairingAccessCode.matches(current, code)
            if (valid) {
                setMeta(this, KEY_PAIRING_CODE_TOKEN, null)
                setMeta(this, KEY_PAIRING_CODE_EXPIRES, null)
            }
            valid
        }
    }

    fun pairingToken(): TemporaryPairingToken = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = getMeta(database.readableDatabase, KEY_PAIRING_TOKEN)
        val expiresAt = getMeta(database.readableDatabase, KEY_PAIRING_EXPIRES)?.toLongOrNull() ?: 0L
        if (!current.isNullOrBlank() && expiresAt > now) {
            return@synchronized TemporaryPairingToken(current, expiresAt)
        }
        issueQrPairingTokenLocked(System.currentTimeMillis())
    }

    fun pairingCodeToken(): TemporaryPairingToken = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = getMeta(database.readableDatabase, KEY_PAIRING_CODE_TOKEN)
        val expiresAt = getMeta(database.readableDatabase, KEY_PAIRING_CODE_EXPIRES)?.toLongOrNull() ?: 0L
        if (!current.isNullOrBlank() && expiresAt > now) {
            return@synchronized TemporaryPairingToken(current, expiresAt)
        }
        issueAccessCodeTokenLocked(System.currentTimeMillis())
    }

    fun revokePairingToken() = synchronized(lock) {
        database.writableDatabase.inTransaction {
            setMeta(this, KEY_PAIRING_TOKEN, null)
            setMeta(this, KEY_PAIRING_EXPIRES, null)
            setMeta(this, KEY_PAIRING_CODE_TOKEN, null)
            setMeta(this, KEY_PAIRING_CODE_EXPIRES, null)
        }
    }

    fun commandResult(commandId: String, fingerprint: String): StoredCommandResult? = synchronized(lock) {
        database.readableDatabase.query(
            "command_results",
            COMMAND_RESULT_COLUMNS,
            "command_id = ?",
            arrayOf(commandId),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@synchronized null
            val stored = StoredCommandResult(
                commandId = cursor.getString(0),
                fingerprint = cursor.getString(1),
                response = cursor.getString(2),
                statusCode = cursor.getInt(3),
                createdAt = cursor.getLong(4)
            )
            if (stored.fingerprint != fingerprint) throw IllegalArgumentException("COMMAND_ID_REUSED")
            stored
        }
    }

    fun saveCommandResult(result: StoredCommandResult) = synchronized(lock) {
        database.writableDatabase.inTransaction {
            insertWithOnConflict("command_results", null, ContentValues().apply {
                put("command_id", result.commandId)
                put("fingerprint", result.fingerprint)
                put("response", result.response)
                put("status_code", result.statusCode)
                put("created_at", result.createdAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            delete("command_results", "command_id NOT IN (SELECT command_id FROM command_results ORDER BY created_at DESC LIMIT ?)", arrayOf(MAX_COMMAND_RESULTS.toString()))
        }
    }

    fun pairedDevices(): List<PairedDevice> = synchronized(lock) {
        try {
            readPairedDevices(database.readableDatabase)
        } catch (error: CredentialDecryptionException) {
            recoverEncryptedCredentials(error)
            emptyList()
        }
    }

    fun resourceStorageSummary(): ResourceStorageSummary = synchronized(lock) {
        val stat = StatFs(resourceDirectory.absolutePath)
        ResourceStorageSummary(
            usedBytes = totalResourceBytes(database.readableDatabase),
            availableBytes = stat.availableBytes,
            quotaBytes = MAX_TOTAL_RESOURCE_BYTES
        )
    }

    fun pairedDevice(deviceId: String): PairedDevice? = synchronized(lock) {
        readPairedDevices(database.readableDatabase).firstOrNull { it.deviceId == deviceId }
    }

    fun savePairedDevice(device: PairedDevice): PairedDevice = synchronized(lock) {
        require(device.deviceId.isNotBlank()) { "DEVICE_ID_REQUIRED" }
        require(device.token.isNotBlank()) { "DEVICE_TOKEN_REQUIRED" }
        require(device.host.isNotBlank() && device.port in 1..65535) { "DEVICE_ADDRESS_INVALID" }
        val normalized = device.copy(
            deviceName = device.deviceName.trim().take(MAX_DEVICE_NAME_LENGTH).ifBlank { device.deviceId },
            host = device.host.trim(),
            token = device.token.trim()
        )
        database.writableDatabase.inTransaction {
            insertPairedDevice(this, normalized)
        }
        normalized
    }

    fun deletePairedDevice(deviceId: String): Boolean = synchronized(lock) {
        database.writableDatabase.delete("paired_devices", "device_id = ?", arrayOf(deviceId)) > 0
    }

    fun controlSession(sessionId: String? = null): ControlSession? = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val sessions = controlSessionsLocked(this)
            writeControlSessions(this, sessions)
            sessionId?.let { id -> sessions.firstOrNull { it.sessionId == id } } ?: sessions.firstOrNull()
        }
    }

    fun acquireControlSession(clientName: String, takeover: Boolean): ControlSession = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val existing = controlSessionsLocked(this).toMutableList()
            val session = ControlSession(
                UUID.randomUUID().toString(),
                clientName.trim().take(MAX_CLIENT_NAME_LENGTH).ifBlank { "Browser" },
                System.currentTimeMillis() + CONTROL_SESSION_TTL_MS
            )
            existing += session
            writeControlSessions(this, existing.takeLast(MAX_CONTROL_SESSIONS))
            clearLegacySession(this)
            session
        }
    }

    fun heartbeatControlSession(sessionId: String): ControlSession? = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val sessions = controlSessionsLocked(this).toMutableList()
            val index = sessions.indexOfFirst { it.sessionId == sessionId }
            if (index < 0) return@inTransaction null
            val current = sessions[index]
            val refreshed = current.copy(expiresAt = System.currentTimeMillis() + CONTROL_SESSION_TTL_MS)
            sessions[index] = refreshed
            writeControlSessions(this, sessions)
            refreshed
        }
    }

    fun releaseControlSession(sessionId: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val sessions = controlSessionsLocked(this)
            val remaining = sessions.filterNot { it.sessionId == sessionId }
            if (remaining.size == sessions.size) return@inTransaction false
            writeControlSessions(this, remaining)
            true
        }
    }

    fun hasControlSession(sessionId: String): Boolean = synchronized(lock) {
        database.writableDatabase.inTransaction {
            val sessions = controlSessionsLocked(this)
            writeControlSessions(this, sessions)
            sessions.any { it.sessionId == sessionId }
        }
    }

    fun acceptCommandRevision(revision: Long?): Boolean = synchronized(lock) {
        if (revision == null) return@synchronized true
        database.writableDatabase.inTransaction {
            val current = getMeta(this, KEY_COMMAND_REVISION)?.toLongOrNull() ?: 0L
            if (!CommandRevisionPolicy.canAccept(current, revision)) return@inTransaction false
            setMeta(this, KEY_COMMAND_REVISION, revision.toString())
            true
        }
    }

    fun canAcceptCommandRevision(revision: Long?): Boolean = synchronized(lock) {
        CommandRevisionPolicy.canAccept(
            getMeta(database.readableDatabase, KEY_COMMAND_REVISION)?.toLongOrNull() ?: 0L,
            revision
        )
    }

    fun commitCommandRevision(revision: Long?): Boolean = acceptCommandRevision(revision)

    fun resources(): List<SignageResource> = synchronized(lock) { readResources(database.readableDatabase) }
    fun resource(id: String?): SignageResource? = synchronized(lock) { readResources(database.readableDatabase).firstOrNull { it.id == id } }
    fun resourceByHash(hash: String?): SignageResource? = synchronized(lock) {
        val normalized = hash?.trim()?.lowercase()?.takeIf { it.matches(SHA256_PATTERN) } ?: return@synchronized null
        readResources(database.readableDatabase).firstOrNull { it.hash == normalized }
    }

    fun fileFor(resource: SignageResource): File {
        require(resource.isLocalFile) { "Resource is not a local file" }
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

    fun setPlaybackSelection(resourceId: String, sceneId: String, playlistId: String?) = synchronized(lock) {
        database.writableDatabase.inTransaction {
            require(resourceExists(this, resourceId)) { "RESOURCE_NOT_FOUND" }
            val scene = readScenes(this).firstOrNull { it.id == sceneId && it.resourceId == resourceId }
                ?: throw IllegalArgumentException("SCENE_NOT_FOUND")
            if (playlistId != null) {
                val playlist = readPlaylists(this).firstOrNull { it.id == playlistId }
                    ?: throw IllegalArgumentException("PLAYLIST_NOT_FOUND")
                require(playlist.items.any { it.enabled && it.sceneId == scene.id }) { "SCENE_NOT_IN_PLAYLIST" }
            }
            setMeta(this, KEY_CURRENT_RESOURCE, resourceId)
            setMeta(this, KEY_CURRENT_SCENE, sceneId)
            setMeta(this, KEY_CURRENT_PLAYLIST, playlistId)
            setMeta(this, KEY_POSITION, "0")
        }
    }

    fun saveScene(scene: SignageScene): SignageScene = synchronized(lock) {
        val normalizedScene = scene.copy(
            playbackSpeed = PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(scene.playbackSpeed)
        )
        database.writableDatabase.inTransaction {
            require(resourceExists(this, normalizedScene.resourceId)) { "Resource does not exist" }
            insertScene(this, normalizedScene)
        }
        normalizedScene
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
            require(PlaylistPolicy.hasKnownScenes(playlist, readScenes(this).mapTo(mutableSetOf()) { it.id })) {
                "Playlist contains an unknown scene"
            }
            replacePlaylist(this, playlist)
            if (getMeta(this, KEY_CURRENT_PLAYLIST) == null) setMeta(this, KEY_CURRENT_PLAYLIST, playlist.id)
        }
        playlist
    }

    fun saveVirtualResource(
        name: String,
        kind: ResourceKind,
        sourceUri: String?,
        content: String?,
        refreshIntervalMs: Long?,
        textSizeSp: Int? = null,
        textColor: String? = null,
        textBackgroundColor: String? = null,
        fontFamily: String? = null,
        textSpeedDpPerSecond: Int? = null,
        textRepeatCount: Int? = null
    ): SignageResource = synchronized(lock) {
        SecondPhasePolicy.validateVirtualResource(kind, sourceUri, content)
        val normalizedName = name.trim().take(MAX_RESOURCE_NAME_LENGTH).ifBlank { kind.name.lowercase() }
        val normalizedUri = sourceUri?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedContent = content?.take(MAX_VIRTUAL_CONTENT_LENGTH)?.takeIf { it.isNotBlank() }
        val normalizedTextSize = TextStylePolicy.normalizeTextSize(textSizeSp)
        val normalizedTextColor = TextStylePolicy.normalizeColor(textColor, TextStylePolicy.DEFAULT_TEXT_COLOR)
        val normalizedBackgroundColor = TextStylePolicy.normalizeColor(textBackgroundColor, TextStylePolicy.DEFAULT_BACKGROUND_COLOR)
        val normalizedFontFamily = TextStylePolicy.fontFamilyForText(fontFamily, normalizedContent)
        val normalizedTextSpeed = PlaybackTimingPolicy.normalizeTextSpeed(textSpeedDpPerSecond)
        val normalizedTextRepeatCount = PlaybackTimingPolicy.normalizeTextRepeatCount(textRepeatCount)
        val styleIdentity = if (kind == ResourceKind.TEXT) {
            listOf(
                normalizedTextSize,
                normalizedTextColor,
                normalizedBackgroundColor,
                normalizedFontFamily,
                normalizedTextSpeed,
                normalizedTextRepeatCount
            )
        } else emptyList()
        val identity = (listOf(kind.name, normalizedUri.orEmpty(), normalizedContent.orEmpty()) + styleIdentity).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        database.writableDatabase.inTransaction {
            val existing = readResources(this).firstOrNull { it.hash == hash }
            if (existing != null) {
                ensureDefaultScene(this, existing)
                existing
            } else run {
                val id = UUID.randomUUID().toString()
                val resource = SignageResource(
                    id = id,
                    name = normalizedName,
                    mimeType = when (kind) {
                        ResourceKind.WEB -> "text/html"
                        ResourceKind.STREAM -> streamMimeType(normalizedUri.orEmpty())
                        ResourceKind.TEXT -> "text/plain"
                        ResourceKind.REMOTE_FILE -> error("Use saveRemoteReference")
                        ResourceKind.LOCAL_FILE -> error("Validated above")
                    },
                    path = "",
                    hash = hash,
                    sizeBytes = normalizedContent?.toByteArray()?.size?.toLong() ?: 0L,
                    createdAt = System.currentTimeMillis(),
                    kind = kind.name,
                    sourceUri = normalizedUri,
                    content = normalizedContent,
                    refreshIntervalMs = SecondPhasePolicy.normalizedRefreshInterval(refreshIntervalMs),
                    textSizeSp = normalizedTextSize,
                    textColor = normalizedTextColor,
                    textBackgroundColor = normalizedBackgroundColor,
                    fontFamily = normalizedFontFamily,
                    textSpeedDpPerSecond = normalizedTextSpeed,
                    textRepeatCount = normalizedTextRepeatCount
                )
                insertResource(this, resource)
                val scene = SignageScene(UUID.randomUUID().toString(), resource.name, resource.id)
                insertScene(this, scene)
                resource
            }
        }
    }

    fun updateDefaultSceneFit(resourceIds: Collection<String>, fitMode: String, cropGravity: String = "CENTER") = synchronized(lock) {
        val normalizedFit = fitMode.trim().uppercase().takeIf { it in SUPPORTED_FIT_MODES } ?: "FIT"
        val normalizedGravity = cropGravity.trim().uppercase().takeIf { it in SUPPORTED_CROP_GRAVITIES } ?: "CENTER"
        val targets = resourceIds.toSet()
        database.writableDatabase.inTransaction {
            readScenes(this)
                .filter { it.resourceId in targets }
                .groupBy { it.resourceId }
                .values
                .mapNotNull { scenes -> scenes.firstOrNull() }
                .forEach { scene -> insertScene(this, scene.copy(fitMode = normalizedFit, cropGravity = normalizedGravity)) }
        }
    }

    fun updateDefaultScenePlaybackSpeed(resourceIds: Collection<String>, playbackSpeed: Float) = synchronized(lock) {
        val normalizedSpeed = PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(playbackSpeed)
        val targets = resourceIds.toSet()
        database.writableDatabase.inTransaction {
            readScenes(this)
                .filter { it.resourceId in targets }
                .groupBy { it.resourceId }
                .values
                .mapNotNull { scenes -> scenes.firstOrNull() }
                .forEach { scene -> insertScene(this, scene.copy(playbackSpeed = normalizedSpeed)) }
        }
    }

    fun saveRemoteReference(
        name: String,
        sourceUri: String,
        mediaType: String
    ): SignageResource = synchronized(lock) {
        SecondPhasePolicy.validateVirtualResource(ResourceKind.REMOTE_FILE, sourceUri, null)
        val normalizedType = mediaType.uppercase()
        require(normalizedType == "IMAGE" || normalizedType == "VIDEO") { "REMOTE_MEDIA_TYPE_INVALID" }
        val normalizedUri = sourceUri.trim()
        val mimeType = if (normalizedType == "IMAGE") "image/remote" else "video/remote"
        val normalizedName = name.trim().take(MAX_RESOURCE_NAME_LENGTH).ifBlank { normalizedType.lowercase() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("REMOTE_FILE\u0000$mimeType\u0000$normalizedUri".toByteArray())
            .joinToString("") { "%02x".format(it) }
        database.writableDatabase.inTransaction {
            val existing = readResources(this).firstOrNull { it.hash == hash }
            if (existing != null) {
                ensureDefaultScene(this, existing)
                existing
            } else run {
                val resource = SignageResource(
                    id = UUID.randomUUID().toString(),
                    name = normalizedName,
                    mimeType = mimeType,
                    path = "",
                    hash = hash,
                    sizeBytes = 0L,
                    createdAt = System.currentTimeMillis(),
                    kind = ResourceKind.REMOTE_FILE.name,
                    sourceUri = normalizedUri
                )
                insertResource(this, resource)
                val scene = SignageScene(UUID.randomUUID().toString(), resource.name, resource.id)
                insertScene(this, scene)
                resource
            }
        }
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
        val normalizedMimeType = supportedUploadMimeType(name, mimeType)
        require(normalizedMimeType != null) {
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
                if (duplicate != null) {
                    ensureDefaultScene(this, duplicate)
                    return@inTransaction duplicate
                }
                val resource = SignageResource(id, safeName, normalizedMimeType, File(resourceDirectory, "${id}_$safeName").absolutePath, hash, size, System.currentTimeMillis())
                try {
                    Files.move(temporary.toPath(), File(resource.path).toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), File(resource.path).toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                committedFile = File(resource.path)
                insertResource(this, resource)
                val scene = SignageScene(UUID.randomUUID().toString(), resource.name, resource.id)
                insertScene(this, scene)
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

    /** Downloads an HTTPS media resource and stores it as a normal local resource. */
    fun saveRemote(rawUrl: String, requestedName: String? = null): SignageResource {
        var current = validateRemoteUrl(rawUrl)
        var redirects = 0
        val temporary = File(resourceDirectory, ".remote-${UUID.randomUUID()}.tmp")
        var mimeType: String? = null
        var name: String? = requestedName?.trim()?.takeIf { it.isNotBlank() }
        try {
            while (true) {
                val connection = (current.toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
                    readTimeout = REMOTE_READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/*,video/*")
                }
                try {
                    val status = connection.responseCode
                    if (status in REDIRECT_STATUSES) {
                        if (++redirects > MAX_REMOTE_REDIRECTS) throw IllegalArgumentException("REMOTE_TOO_MANY_REDIRECTS")
                        val location = connection.getHeaderField("Location")
                            ?: throw IllegalArgumentException("REMOTE_REDIRECT_INVALID")
                        current = validateRemoteUrl(current.resolve(location).toString())
                        continue
                    }
                    if (status !in 200..299) throw IllegalArgumentException("REMOTE_HTTP_$status")
                    val contentLength = connection.contentLengthLong
                    require(contentLength <= MAX_RESOURCE_BYTES) { "REMOTE_RESOURCE_TOO_LARGE" }
                    name = name ?: remoteFileName(current)
                    mimeType = remoteMimeType(connection.contentType, name, current)
                    temporary.outputStream().buffered().use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var size = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                size += count
                                require(size <= MAX_RESOURCE_BYTES) { "REMOTE_RESOURCE_TOO_LARGE" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    break
                } finally {
                    connection.disconnect()
                }
            }
            val downloadedName = name
            val downloadedMimeType = mimeType
            FileInputStream(temporary).use { input ->
                return saveUpload(downloadedName, downloadedMimeType, input)
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("REMOTE_DOWNLOAD_FAILED")
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
        if (deleted && resource.isLocalFile) fileFor(resource).delete()
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

    fun settings(): SignageSettings = synchronized(lock) {
        SignageSettings(
            fallbackSceneId = getMeta(database.readableDatabase, KEY_FALLBACK_SCENE),
            keepScreenAwake = getMeta(database.readableDatabase, KEY_KEEP_SCREEN_AWAKE)?.toBoolean() ?: true,
            autoResume = getMeta(database.readableDatabase, KEY_AUTO_RESUME)?.toBoolean() ?: true,
            fullscreen = getMeta(database.readableDatabase, KEY_FULLSCREEN)?.toBoolean() ?: true
        )
    }

    fun setSettings(value: SignageSettings) = synchronized(lock) {
        database.writableDatabase.inTransaction {
            if (value.fallbackSceneId == null || sceneExists(this, value.fallbackSceneId)) {
                setMeta(this, KEY_FALLBACK_SCENE, value.fallbackSceneId)
            } else {
                throw IllegalArgumentException("Fallback scene does not exist")
            }
            setMeta(this, KEY_KEEP_SCREEN_AWAKE, value.keepScreenAwake.toString())
            setMeta(this, KEY_AUTO_RESUME, value.autoResume.toString())
            setMeta(this, KEY_FULLSCREEN, value.fullscreen.toString())
        }
    }

    fun recordPlaybackError(mediaId: String?, sceneId: String?, errorCode: String, action: String, attempt: Int) = synchronized(lock) {
        database.writableDatabase.insert("playback_errors", null, ContentValues().apply {
            put("media_id", mediaId)
            put("scene_id", sceneId)
            put("error_code", errorCode.take(MAX_ERROR_CODE_LENGTH))
            put("action", action.take(MAX_ERROR_ACTION_LENGTH))
            put("attempt", attempt)
            put("created_at", System.currentTimeMillis())
        })
        database.writableDatabase.execSQL(
            "DELETE FROM playback_errors WHERE id NOT IN (SELECT id FROM playback_errors ORDER BY id DESC LIMIT ?)",
            arrayOf(MAX_ERROR_HISTORY.toString())
        )
    }

    fun playbackErrors(limit: Int = MAX_ERROR_HISTORY): List<PlaybackErrorRecord> = synchronized(lock) {
        buildList {
            database.readableDatabase.query(
                "playback_errors",
                ERROR_COLUMNS,
                null,
                null,
                null,
                null,
                "id DESC",
                limit.coerceIn(1, MAX_ERROR_HISTORY).toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(PlaybackErrorRecord(
                        id = cursor.getLong(0),
                        mediaId = cursor.getStringOrNull(1),
                        sceneId = cursor.getStringOrNull(2),
                        errorCode = cursor.getString(3),
                        action = cursor.getString(4),
                        attempt = cursor.getInt(5),
                        createdAt = cursor.getLong(6)
                    ))
                }
            }
        }
    }

    fun clearPlaybackErrors() = synchronized(lock) {
        database.writableDatabase.delete("playback_errors", null, null)
    }

    private fun controlSessionsLocked(db: SQLiteDatabase): List<ControlSession> {
        val now = System.currentTimeMillis()
        val stored = runCatching {
            val array = JSONArray(getMeta(db, KEY_CONTROL_SESSIONS) ?: "[]")
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val sessionId = item.optString("sessionId")
                    val expiresAt = item.optLong("expiresAt")
                    if (sessionId.isNotBlank() && expiresAt > now) {
                        add(ControlSession(sessionId, item.optString("clientName", "Browser"), expiresAt))
                    }
                }
            }
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored
        val legacyId = getMeta(db, KEY_SESSION_ID) ?: return emptyList()
        val legacyExpiry = getMeta(db, KEY_SESSION_EXPIRES)?.toLongOrNull() ?: return emptyList()
        return if (legacyExpiry > now) {
            listOf(ControlSession(legacyId, getMeta(db, KEY_SESSION_CLIENT) ?: "Browser", legacyExpiry))
        } else emptyList()
    }

    private fun writeControlSessions(db: SQLiteDatabase, sessions: List<ControlSession>) {
        val array = JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject().apply {
                    put("sessionId", session.sessionId)
                    put("clientName", session.clientName)
                    put("expiresAt", session.expiresAt)
                })
            }
        }
        setMeta(db, KEY_CONTROL_SESSIONS, array.toString())
    }

    private fun clearLegacySession(db: SQLiteDatabase) {
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
            while (cursor.moveToNext()) add(SignageResource(
                id = cursor.getString(0),
                name = cursor.getString(1),
                mimeType = cursor.getString(2),
                path = cursor.getString(3),
                hash = cursor.getString(4),
                sizeBytes = cursor.getLong(5),
                createdAt = cursor.getLong(6),
                kind = cursor.getString(7),
                sourceUri = cursor.getStringOrNull(8),
                content = cursor.getStringOrNull(9),
                refreshIntervalMs = cursor.getLongOrNull(10),
                textSizeSp = cursor.getInt(11),
                textColor = cursor.getString(12),
                textBackgroundColor = cursor.getString(13),
                fontFamily = cursor.getString(14),
                textSpeedDpPerSecond = cursor.getInt(15),
                textRepeatCount = cursor.getInt(16)
            ))
        }
    }

    private fun readPairedDevices(db: SQLiteDatabase): List<PairedDevice> = buildList {
        db.query("paired_devices", PAIRED_DEVICE_COLUMNS, null, null, null, null, "device_name COLLATE NOCASE ASC").use { cursor ->
            while (cursor.moveToNext()) add(PairedDevice(
                deviceId = cursor.getString(0),
                deviceName = cursor.getString(1),
                host = cursor.getString(2),
                port = cursor.getInt(3),
                token = LocalSecretCipher.decrypt(cursor.getString(4)),
                pairedAt = cursor.getLong(5)
            ))
        }
    }

    private fun recoverEncryptedCredentials(cause: Exception): String {
        Log.e(TAG, "Encrypted device credentials are unavailable; rotating credentials and requiring re-pairing", cause)
        LocalSecretCipher.resetKey()
        val replacement = randomToken()
        database.writableDatabase.inTransaction {
            delete("paired_devices", null, null)
            setMeta(this, KEY_CONTROL_TOKEN, LocalSecretCipher.encrypt(replacement))
            setMeta(this, KEY_WEB_ACCESS_TOKEN, null)
            setMeta(this, KEY_WEB_ACCESS_EXPIRES, null)
            setMeta(this, KEY_WEB_ACCESS_HISTORY, null)
            setMeta(this, KEY_PAIRING_TOKEN, null)
            setMeta(this, KEY_PAIRING_EXPIRES, null)
            setMeta(this, KEY_CONTROL_SESSIONS, null)
            clearLegacySession(this)
        }
        return replacement
    }

    private fun readScenes(db: SQLiteDatabase): List<SignageScene> = buildList {
        db.query("scenes", SCENE_COLUMNS, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) add(SignageScene(
                id = cursor.getString(0),
                name = cursor.getString(1),
                resourceId = cursor.getString(2),
                fitMode = cursor.getString(3),
                cropGravity = cursor.getString(4),
                backgroundType = cursor.getString(5),
                backgroundColor = cursor.getStringOrNull(6),
                volume = cursor.getIntOrNull(7),
                muted = cursor.getInt(8) != 0,
                createdAt = cursor.getLong(9),
                overlays = parseOverlays(cursor.getStringOrNull(10)),
                playbackSpeed = cursor.getFloat(11)
            ))
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
        db.insertOrThrow("resources", null, ContentValues().apply {
            put("id", resource.id); put("name", resource.name); put("mime_type", resource.mimeType)
            put("path", resource.path); put("hash", resource.hash); put("size_bytes", resource.sizeBytes)
            put("created_at", resource.createdAt); put("kind", resource.kind); put("source_uri", resource.sourceUri)
            put("content", resource.content); put("refresh_interval_ms", resource.refreshIntervalMs)
            put("text_size_sp", resource.textSizeSp); put("text_color", resource.textColor)
            put("text_background_color", resource.textBackgroundColor); put("font_family", resource.fontFamily)
            put("text_speed_dp_per_second", resource.textSpeedDpPerSecond); put("text_repeat_count", resource.textRepeatCount)
        })
    }

    private fun insertPairedDevice(db: SQLiteDatabase, device: PairedDevice) {
        db.insertWithOnConflict("paired_devices", null, ContentValues().apply {
            put("device_id", device.deviceId)
            put("device_name", device.deviceName)
            put("host", device.host)
            put("port", device.port)
            put("token", LocalSecretCipher.encrypt(device.token))
            put("paired_at", device.pairedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertScene(db: SQLiteDatabase, scene: SignageScene) {
        val values = ContentValues().apply {
            put("id", scene.id)
            put("name", scene.name)
            put("resource_id", scene.resourceId)
            put("fit_mode", scene.fitMode)
            put("crop_gravity", scene.cropGravity)
            put("background_type", scene.backgroundType)
            put("background_color", scene.backgroundColor)
            put("volume", scene.volume)
            put("muted", if (scene.muted) 1 else 0)
            put("created_at", scene.createdAt)
            put("overlays_json", overlaysJson(scene.overlays))
            put("playback_speed", PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(scene.playbackSpeed))
        }
        if (db.update("scenes", values, "id = ?", arrayOf(scene.id)) == 0) {
            db.insertOrThrow("scenes", null, values)
        }
    }

    private fun ensureDefaultScene(
        db: SQLiteDatabase,
        resource: SignageResource
    ): SignageScene {
        readScenes(db).firstOrNull { it.resourceId == resource.id }?.let { return it }
        val scene = SignageScene(UUID.randomUUID().toString(), resource.name, resource.id)
        insertScene(db, scene)
        if (getMeta(db, KEY_CURRENT_SCENE) == null) setMeta(db, KEY_CURRENT_SCENE, scene.id)
        return scene
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
    private fun totalResourceBytes(db: SQLiteDatabase): Long = db.rawQuery("SELECT COALESCE(SUM(size_bytes), 0) FROM resources WHERE kind = ?", arrayOf(ResourceKind.LOCAL_FILE.name)).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    private fun overlaysJson(overlays: List<SignageOverlay>): String = JSONArray().apply {
        overlays.sortedBy { it.zIndex }.forEach { overlay ->
            put(JSONObject().apply {
                put("id", overlay.id); put("type", overlay.type); put("content", overlay.content)
                put("horizontalPosition", overlay.horizontalPosition); put("verticalPosition", overlay.verticalPosition)
                put("textSizeSp", overlay.textSizeSp); put("textColor", overlay.textColor)
                put("backgroundColor", overlay.backgroundColor); put("paddingDp", overlay.paddingDp)
                put("cornerRadiusDp", overlay.cornerRadiusDp); put("fontFamily", overlay.fontFamily)
                put("speedDpPerSecond", overlay.speedDpPerSecond); put("enabled", overlay.enabled); put("zIndex", overlay.zIndex)
            })
        }
    }.toString()

    private fun parseOverlays(raw: String?): List<SignageOverlay> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val content = item.optString("content")
                add(SignageOverlay(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    type = item.optString("type", "TEXT"),
                    content = content,
                    horizontalPosition = item.optString("horizontalPosition", "CENTER"),
                    verticalPosition = item.optString("verticalPosition", "BOTTOM"),
                    textSizeSp = item.optInt("textSizeSp", 28).coerceIn(8, 160),
                    textColor = item.optString("textColor", "#FFFFFFFF"),
                    backgroundColor = item.optString("backgroundColor", "#99000000"),
                    paddingDp = item.optInt("paddingDp", 12).coerceIn(0, 96),
                    cornerRadiusDp = item.optInt("cornerRadiusDp", 8).coerceIn(0, 64),
                    fontFamily = TextStylePolicy.fontFamilyForText(item.optString("fontFamily"), content),
                    speedDpPerSecond = item.optInt("speedDpPerSecond", 80).coerceIn(10, 500),
                    enabled = item.optBoolean("enabled", true),
                    zIndex = item.optInt("zIndex", index)
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun streamMimeType(uri: String): String = when (uri.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        else -> "application/x-rtsp"
    }
    private fun getMeta(db: SQLiteDatabase, key: String): String? = db.query("meta", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }
    private fun setMeta(db: SQLiteDatabase, key: String, value: String?) { if (value == null) delete(db, "meta", "key = ?", arrayOf(key)) else db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    private fun delete(db: SQLiteDatabase, table: String, where: String, args: Array<String>) { db.delete(table, where, args) }
    private fun normalizeMimeType(value: String) = value.lowercase().substringBefore(';')
    private fun supportedUploadMimeType(name: String, mimeType: String): String? {
        val normalized = normalizeMimeType(mimeType)
        if (normalized.startsWith("image/") || normalized.startsWith("video/")) return normalized
        if (normalized.isNotBlank() && normalized != "application/octet-stream") return null
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            else -> null
        }
    }
    private fun issueWebAccessTokenLocked(db: SQLiteDatabase, now: Long): String {
        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val history = readWebAccessHistory(db).filter { it.expiresAt > now }.toMutableList()
        getMeta(db, KEY_WEB_ACCESS_TOKEN)?.let { current ->
            val expiresAt = getMeta(db, KEY_WEB_ACCESS_EXPIRES)?.toLongOrNull() ?: 0L
            if (expiresAt > now) history += ExpiringAccessToken(current, expiresAt)
        }
        setMeta(db, KEY_WEB_ACCESS_TOKEN, token)
        setMeta(db, KEY_WEB_ACCESS_EXPIRES, (now + WEB_ACCESS_TOKEN_TTL_MS).toString())
        setMeta(db, KEY_WEB_ACCESS_HISTORY, AccessTokenHistory.encode(history, now, MAX_WEB_ACCESS_HISTORY))
        return token
    }

    private fun readWebAccessHistory(db: SQLiteDatabase): List<ExpiringAccessToken> =
        AccessTokenHistory.parse(getMeta(db, KEY_WEB_ACCESS_HISTORY))

    private fun issueQrPairingTokenLocked(now: Long): TemporaryPairingToken {
        val pairing = TemporaryPairingToken(randomToken(), now + PAIRING_TOKEN_TTL_MS)
        database.writableDatabase.inTransaction {
            setMeta(this, KEY_PAIRING_TOKEN, pairing.token)
            setMeta(this, KEY_PAIRING_EXPIRES, pairing.expiresAt.toString())
        }
        return pairing
    }

    private fun issueAccessCodeTokenLocked(now: Long): TemporaryPairingToken {
        val pairing = TemporaryPairingToken(randomToken(), now + PAIRING_TOKEN_TTL_MS)
        database.writableDatabase.inTransaction {
            setMeta(this, KEY_PAIRING_CODE_TOKEN, pairing.token)
            setMeta(this, KEY_PAIRING_CODE_EXPIRES, pairing.expiresAt.toString())
        }
        return pairing
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun validateRemoteUrl(rawUrl: String): URI {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("REMOTE_URL_INVALID")
        require(uri.scheme.equals("https", ignoreCase = true)) { "REMOTE_URL_PROTOCOL_NOT_ALLOWED" }
        require(uri.userInfo == null && !uri.host.isNullOrBlank()) { "REMOTE_URL_INVALID" }
        require(uri.port == -1 || uri.port == 443) { "REMOTE_URL_PORT_NOT_ALLOWED" }
        val addresses = runCatching { InetAddress.getAllByName(uri.host) }.getOrElse {
            throw IllegalArgumentException("REMOTE_HOST_UNRESOLVED")
        }
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }) { "REMOTE_HOST_NOT_ALLOWED" }
        return uri
    }

    private fun remoteFileName(uri: URI): String = uri.path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "remote-resource"

    private fun remoteMimeType(contentType: String?, name: String?, uri: URI): String {
        val headerType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (headerType.startsWith("image/") || headerType.startsWith("video/")) return headerType
        val guessed = URLConnectionMime.guess(name ?: "").takeIf { it != "application/octet-stream" }
            ?: URLConnectionMime.guess(uri.path)
        require(guessed.startsWith("image/") || guessed.startsWith("video/")) { "REMOTE_CONTENT_TYPE_UNSUPPORTED" }
        return guessed
    }

    private object URLConnectionMime {
        fun guess(name: String): String {
            val extension = name.substringBefore('?').substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "mp4", "m4v" -> "video/mp4"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                else -> "application/octet-stream"
            }
        }
    }

    private class SignageDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE resources (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, mime_type TEXT NOT NULL, path TEXT NOT NULL, hash TEXT NOT NULL UNIQUE, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'LOCAL_FILE', source_uri TEXT, content TEXT, refresh_interval_ms INTEGER, text_size_sp INTEGER NOT NULL DEFAULT 48, text_color TEXT NOT NULL DEFAULT '#FFFFFFFF', text_background_color TEXT NOT NULL DEFAULT '#FF000000', font_family TEXT NOT NULL DEFAULT 'SYSTEM_SANS', text_speed_dp_per_second INTEGER NOT NULL DEFAULT 90, text_repeat_count INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE scenes (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, resource_id TEXT NOT NULL, fit_mode TEXT NOT NULL, crop_gravity TEXT NOT NULL, background_type TEXT NOT NULL, background_color TEXT, volume INTEGER, muted INTEGER NOT NULL, created_at INTEGER NOT NULL, overlays_json TEXT NOT NULL DEFAULT '[]', playback_speed REAL NOT NULL DEFAULT 1.0, FOREIGN KEY(resource_id) REFERENCES resources(id))")
            db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, loop INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, scene_id TEXT NOT NULL, duration_ms INTEGER, enabled INTEGER NOT NULL, PRIMARY KEY(playlist_id, position), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE, FOREIGN KEY(scene_id) REFERENCES scenes(id))")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE playback_errors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, media_id TEXT, scene_id TEXT, error_code TEXT NOT NULL, action TEXT NOT NULL, attempt INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE paired_devices (device_id TEXT PRIMARY KEY NOT NULL, device_name TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, token TEXT NOT NULL, paired_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE command_results (command_id TEXT PRIMARY KEY NOT NULL, fingerprint TEXT NOT NULL, response TEXT NOT NULL, status_code INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX scenes_resource_idx ON scenes(resource_id)")
            db.execSQL("CREATE INDEX playlist_items_scene_idx ON playlist_items(scene_id)")
            db.execSQL("CREATE INDEX playback_errors_created_idx ON playback_errors(created_at DESC)")
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
        }
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
        const val TAG = "SignageStore"
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        const val DATABASE_NAME = "signage.db"
        const val DATABASE_VERSION = 8
        const val LEGACY_PREFERENCES = "local_signage"
        const val KEY_SCHEMA_MIGRATED = "schema_migrated"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_CONTROL_TOKEN = "control_token"
        const val KEY_WEB_ACCESS_TOKEN = "web_access_token"
        const val KEY_WEB_ACCESS_EXPIRES = "web_access_expires"
        const val KEY_WEB_ACCESS_HISTORY = "web_access_history"
        const val KEY_PAIRING_TOKEN = "pairing_token"
        const val KEY_PAIRING_EXPIRES = "pairing_expires"
        const val KEY_PAIRING_CODE_TOKEN = "pairing_code_token"
        const val KEY_PAIRING_CODE_EXPIRES = "pairing_code_expires"
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
        const val KEY_CONTROL_SESSIONS = "control_sessions"
        const val KEY_FALLBACK_SCENE = "fallback_scene"
        const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_FULLSCREEN = "fullscreen"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SESSION_CLIENT = "session_client"
        const val KEY_SESSION_EXPIRES = "session_expires"
        const val CONTROL_SESSION_TTL_MS = 60_000L
        const val WEB_ACCESS_TOKEN_TTL_MS = 8 * 60 * 60 * 1000L
        const val PAIRING_TOKEN_TTL_MS = 5 * 60 * 1000L
        const val MAX_WEB_ACCESS_HISTORY = 32
        const val MAX_COMMAND_RESULTS = 256
        const val MAX_CLIENT_NAME_LENGTH = 80
        const val MAX_CONTROL_SESSIONS = 16
        const val MAX_DEVICE_NAME_LENGTH = 80
        const val MAX_RESOURCE_NAME_LENGTH = 120
        const val MAX_VIRTUAL_CONTENT_LENGTH = 100_000
        const val MAX_RESOURCE_BYTES = 200L * 1024L * 1024L
        const val MAX_TOTAL_RESOURCE_BYTES = 2L * 1024L * 1024L * 1024L
        const val REMOTE_CONNECT_TIMEOUT_MS = 10_000
        const val REMOTE_READ_TIMEOUT_MS = 30_000
        const val MAX_REMOTE_REDIRECTS = 5
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        const val MAX_ERROR_HISTORY = 100
        const val MAX_ERROR_CODE_LENGTH = 120
        const val MAX_ERROR_ACTION_LENGTH = 32
        val SUPPORTED_FIT_MODES = setOf("FIT", "FILL", "CROP", "STRETCH", "CENTER")
        val SUPPORTED_CROP_GRAVITIES = setOf("CENTER", "TOP", "BOTTOM", "LEFT", "RIGHT")
        val RESOURCE_COLUMNS = arrayOf("id", "name", "mime_type", "path", "hash", "size_bytes", "created_at", "kind", "source_uri", "content", "refresh_interval_ms", "text_size_sp", "text_color", "text_background_color", "font_family", "text_speed_dp_per_second", "text_repeat_count")
        val SCENE_COLUMNS = arrayOf("id", "name", "resource_id", "fit_mode", "crop_gravity", "background_type", "background_color", "volume", "muted", "created_at", "overlays_json", "playback_speed")
        val ERROR_COLUMNS = arrayOf("id", "media_id", "scene_id", "error_code", "action", "attempt", "created_at")
        val PAIRED_DEVICE_COLUMNS = arrayOf("device_id", "device_name", "host", "port", "token", "paired_at")
        val COMMAND_RESULT_COLUMNS = arrayOf("command_id", "fingerprint", "response", "status_code", "created_at")
    }
}

data class StoredCommandResult(
    val commandId: String,
    val fingerprint: String,
    val response: String,
    val statusCode: Int,
    val createdAt: Long
)
