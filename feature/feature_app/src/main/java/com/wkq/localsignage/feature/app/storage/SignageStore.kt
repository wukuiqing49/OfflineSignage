package com.wkq.localsignage.feature.app.storage

import android.content.Context
import android.net.Uri
import com.wkq.localsignage.feature.app.model.SignageResource
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

    fun deviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val value = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    fun deviceName(): String = preferences.getString(KEY_DEVICE_NAME, null)
        ?: "Local Signage"

    fun resources(): List<SignageResource> = readResources()

    fun resource(id: String?): SignageResource? = readResources().firstOrNull { it.id == id }

    fun fileFor(resource: SignageResource): File = File(resource.path)

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
        val updated = readResources() + resource
        writeResources(updated)
        if (currentResourceId() == null) setCurrentResource(resource.id)
        return resource
    }

    fun deleteResource(id: String): Boolean {
        val resource = resource(id) ?: return false
        fileFor(resource).delete()
        val remaining = readResources().filterNot { it.id == id }
        writeResources(remaining)
        if (currentResourceId() == id) setCurrentResource(remaining.firstOrNull()?.id)
        return true
    }

    fun currentResourceId(): String? = preferences.getString(KEY_CURRENT_RESOURCE, null)

    fun setCurrentResource(id: String?) = preferences.edit().putString(KEY_CURRENT_RESOURCE, id).apply()

    fun state(port: Int): SignageState = SignageState(
        deviceId = deviceId(),
        deviceName = deviceName(),
        currentResourceId = currentResourceId(),
        playing = preferences.getBoolean(KEY_PLAYING, false),
        volume = preferences.getInt(KEY_VOLUME, 80),
        muted = preferences.getBoolean(KEY_MUTED, false),
        positionMs = preferences.getLong(KEY_POSITION, 0L),
        serverPort = port
    )

    fun setPlaying(value: Boolean) = preferences.edit().putBoolean(KEY_PLAYING, value).apply()
    fun setVolume(value: Int) = preferences.edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()
    fun setMuted(value: Boolean) = preferences.edit().putBoolean(KEY_MUTED, value).apply()
    fun setPosition(value: Long) = preferences.edit().putLong(KEY_POSITION, value.coerceAtLeast(0L)).apply()

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

    private fun normalizeMimeType(value: String): String = when {
        value.startsWith("image/") -> value
        value.startsWith("video/") -> value
        else -> "application/octet-stream"
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_RESOURCES = "resources"
        const val KEY_CURRENT_RESOURCE = "current_resource"
        const val KEY_PLAYING = "playing"
        const val KEY_VOLUME = "volume"
        const val KEY_MUTED = "muted"
        const val KEY_POSITION = "position"
        const val MAX_RESOURCE_BYTES = 200L * 1024L * 1024L
    }
}
