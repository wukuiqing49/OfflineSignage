package com.wkq.localsignage.feature.app.storage

import android.content.Context
import android.net.Uri
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.SignageState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class SignageStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("local_signage", Context.MODE_PRIVATE)
    private val resourceDirectory = File(appContext.filesDir, "shared/resources").apply { mkdirs() }

    fun ensureDefaultContent() {
        val allResources = resources()
        val existingScenes = scenes().toMutableList()
        val existingSceneResourceIds = existingScenes.mapTo(mutableSetOf()) { it.resourceId }
        allResources.filterNot { it.id in existingSceneResourceIds }.forEach { resource ->
            existingScenes += SignageScene(
                id = UUID.randomUUID().toString(),
                name = resource.name,
                resourceId = resource.id
            )
        }
        writeScenes(existingScenes)
        val currentPlaylist = playlists().firstOrNull()
        val validSceneIds = existingScenes.mapTo(mutableSetOf()) { it.id }
        val items = existingScenes
            .filter { it.id in validSceneIds }
            .map { SignagePlaylistItem(it.id) }
        if (currentPlaylist == null) {
            val playlist = SignagePlaylist(UUID.randomUUID().toString(), "Default", items)
            writePlaylists(listOf(playlist))
            setCurrentPlaylistId(playlist.id)
        } else {
            val validItems = currentPlaylist.items.filter { it.sceneId in validSceneIds }
            if (validItems != currentPlaylist.items) {
                writePlaylists(playlists().map { if (it.id == currentPlaylist.id) it.copy(items = validItems) else it })
            }
            if (currentPlaylist.id != currentPlaylistId()) setCurrentPlaylistId(currentPlaylist.id)
        }
        if (currentSceneId() == null) setCurrentSceneId(existingScenes.firstOrNull()?.id)
    }

    fun deviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val value = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    fun deviceName(): String = preferences.getString(KEY_DEVICE_NAME, null)
        ?: "Local Signage"

    fun controlToken(): String {
        val existing = preferences.getString(KEY_CONTROL_TOKEN, null)
        if (existing != null) return existing
        val value = UUID.randomUUID().toString().replace("-", "")
        preferences.edit().putString(KEY_CONTROL_TOKEN, value).apply()
        return value
    }

    fun resources(): List<SignageResource> = readResources()

    fun resource(id: String?): SignageResource? = readResources().firstOrNull { it.id == id }

    fun fileFor(resource: SignageResource): File = File(resource.path)

    fun scenes(): List<SignageScene> = readScenes()
    fun scene(id: String?): SignageScene? = scenes().firstOrNull { it.id == id }
    fun playlists(): List<SignagePlaylist> = readPlaylists()
    fun playlist(id: String?): SignagePlaylist? = playlists().firstOrNull { it.id == id }
    fun currentSceneId(): String? = preferences.getString(KEY_CURRENT_SCENE, null)
    fun currentPlaylistId(): String? = preferences.getString(KEY_CURRENT_PLAYLIST, null)
    fun setCurrentSceneId(id: String?) = preferences.edit().putString(KEY_CURRENT_SCENE, id).apply()
    fun setCurrentPlaylistId(id: String?) = preferences.edit().putString(KEY_CURRENT_PLAYLIST, id).apply()

    fun saveScene(scene: SignageScene): SignageScene {
        require(resource(scene.resourceId) != null) { "Resource does not exist" }
        writeScenes(scenes().filterNot { it.id == scene.id } + scene)
        return scene
    }

    fun deleteScene(id: String): Boolean {
        if (scene(id) == null) return false
        writeScenes(scenes().filterNot { it.id == id })
        writePlaylists(playlists().map { playlist -> playlist.copy(items = playlist.items.filterNot { it.sceneId == id }) })
        if (currentSceneId() == id) setCurrentSceneId(scenes().firstOrNull()?.id)
        return true
    }

    fun savePlaylist(playlist: SignagePlaylist): SignagePlaylist {
        require(playlist.items.all { scene(it.sceneId) != null }) { "Playlist contains an unknown scene" }
        writePlaylists(playlists().filterNot { it.id == playlist.id } + playlist)
        if (currentPlaylistId() == null) setCurrentPlaylistId(playlist.id)
        return playlist
    }

    fun deletePlaylist(id: String): Boolean {
        if (playlist(id) == null) return false
        val remaining = playlists().filterNot { it.id == id }
        writePlaylists(remaining)
        if (currentPlaylistId() == id) setCurrentPlaylistId(remaining.firstOrNull()?.id)
        return true
    }

    fun importUri(uri: Uri, name: String, mimeType: String): SignageResource {
        return appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            saveUpload(name, mimeType, input)
        }
    }

    fun saveUpload(name: String, mimeType: String, input: InputStream): SignageResource {
        require(mimeType == "image/*" || mimeType == "video/*" || mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            "Only image and video resources are supported"
        }
        val id = UUID.randomUUID().toString()
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resource" }
        val target = File(resourceDirectory, "${id}_$safeName")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                size += count
                require(size <= MAX_RESOURCE_BYTES) { "Resource is too large" }
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
        val resource = SignageResource(
            id = id,
            name = safeName,
            mimeType = normalizeMimeType(mimeType),
            path = target.absolutePath,
            hash = digest.digest().joinToString("") { "%02x".format(it) },
            sizeBytes = size,
            createdAt = System.currentTimeMillis()
        )
        val duplicate = readResources().firstOrNull { it.hash == resource.hash }
        if (duplicate != null) {
            target.delete()
            return duplicate
        }
        val updated = readResources() + resource
        writeResources(updated)
        saveScene(SignageScene(UUID.randomUUID().toString(), resource.name, resource.id))
        val playlist = playlists().firstOrNull()
        if (playlist != null) {
            savePlaylist(playlist.copy(items = playlist.items + SignagePlaylistItem(scenes().last().id)))
        }
        if (currentResourceId() == null) setCurrentResource(resource.id)
        if (currentSceneId() == null) setCurrentSceneId(scenes().last().id)
        return resource
    }

    fun deleteResource(id: String): Boolean {
        val resource = resource(id) ?: return false
        fileFor(resource).delete()
        val remaining = readResources().filterNot { it.id == id }
        writeResources(remaining)
        scenes().filter { it.resourceId == id }.forEach { deleteScene(it.id) }
        if (currentResourceId() == id) setCurrentResource(remaining.firstOrNull()?.id)
        return true
    }

    fun currentResourceId(): String? = preferences.getString(KEY_CURRENT_RESOURCE, null)

    fun setCurrentResource(id: String?) = preferences.edit().putString(KEY_CURRENT_RESOURCE, id).apply()

    fun state(port: Int): SignageState = SignageState(
        deviceId = deviceId(),
        deviceName = deviceName(),
        currentResourceId = currentResourceId(),
        currentSceneId = currentSceneId(),
        currentPlaylistId = currentPlaylistId(),
        playing = preferences.getBoolean(KEY_PLAYING, false),
        volume = preferences.getInt(KEY_VOLUME, 80),
        muted = preferences.getBoolean(KEY_MUTED, false),
        positionMs = preferences.getLong(KEY_POSITION, 0L),
        error = preferences.getString(KEY_ERROR, null),
        serverPort = port
    )

    fun setPlaying(value: Boolean) = preferences.edit().putBoolean(KEY_PLAYING, value).apply()
    fun setVolume(value: Int) = preferences.edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()
    fun setMuted(value: Boolean) = preferences.edit().putBoolean(KEY_MUTED, value).apply()
    fun setPosition(value: Long) = preferences.edit().putLong(KEY_POSITION, value.coerceAtLeast(0L)).apply()
    fun setError(value: String?) = preferences.edit().putString(KEY_ERROR, value).apply()

    private fun readResources(): List<SignageResource> {
        val raw = preferences.getString(KEY_RESOURCES, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SignageResource(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        mimeType = item.getString("mimeType"),
                        path = item.getString("path"),
                        hash = item.getString("hash"),
                        sizeBytes = item.getLong("sizeBytes"),
                        createdAt = item.getLong("createdAt")
                    )
                )
            }
        }
    }

    private fun writeResources(resources: List<SignageResource>) {
        val array = JSONArray()
        resources.forEach { resource ->
            array.put(JSONObject().apply {
                put("id", resource.id)
                put("name", resource.name)
                put("mimeType", resource.mimeType)
                put("path", resource.path)
                put("hash", resource.hash)
                put("sizeBytes", resource.sizeBytes)
                put("createdAt", resource.createdAt)
            })
        }
        preferences.edit().putString(KEY_RESOURCES, array.toString()).apply()
    }

    private fun readScenes(): List<SignageScene> {
        val raw = preferences.getString(KEY_SCENES, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(SignageScene(
                    id = item.getString("id"), name = item.getString("name"), resourceId = item.getString("resourceId"),
                    fitMode = item.optString("fitMode", "FIT"), cropGravity = item.optString("cropGravity", "CENTER"),
                    backgroundType = item.optString("backgroundType", "BLACK"), backgroundColor = item.optString("backgroundColor").ifBlank { null },
                    volume = if (item.has("volume") && !item.isNull("volume")) item.getInt("volume") else null,
                    muted = item.optBoolean("muted", false), createdAt = item.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }
    }

    private fun writeScenes(scenes: List<SignageScene>) {
        val array = JSONArray()
        scenes.forEach { scene -> array.put(JSONObject().apply {
            put("id", scene.id); put("name", scene.name); put("resourceId", scene.resourceId); put("fitMode", scene.fitMode)
            put("cropGravity", scene.cropGravity); put("backgroundType", scene.backgroundType); put("backgroundColor", scene.backgroundColor)
            put("volume", scene.volume); put("muted", scene.muted); put("createdAt", scene.createdAt)
        }) }
        preferences.edit().putString(KEY_SCENES, array.toString()).apply()
    }

    private fun readPlaylists(): List<SignagePlaylist> {
        val raw = preferences.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val items = item.optJSONArray("items") ?: JSONArray()
                add(SignagePlaylist(
                    id = item.getString("id"), name = item.getString("name"), loop = item.optBoolean("loop", true),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    items = buildList {
                        for (itemIndex in 0 until items.length()) {
                            val playlistItem = items.getJSONObject(itemIndex)
                            add(SignagePlaylistItem(playlistItem.getString("sceneId"), if (playlistItem.has("durationMs") && !playlistItem.isNull("durationMs")) playlistItem.getLong("durationMs") else null, playlistItem.optBoolean("enabled", true)))
                        }
                    }
                ))
            }
        }
    }

    private fun writePlaylists(playlists: List<SignagePlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist -> array.put(JSONObject().apply {
            put("id", playlist.id); put("name", playlist.name); put("loop", playlist.loop); put("updatedAt", playlist.updatedAt)
            put("items", JSONArray().apply { playlist.items.forEach { item -> put(JSONObject().apply { put("sceneId", item.sceneId); put("durationMs", item.durationMs); put("enabled", item.enabled) }) } })
        }) }
        preferences.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun normalizeMimeType(value: String): String = when {
        value.startsWith("image/") -> value
        value.startsWith("video/") -> value
        else -> "application/octet-stream"
    }

    private companion object {
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
        const val MAX_RESOURCE_BYTES = 200L * 1024L * 1024L
    }
}
