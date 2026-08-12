package com.wkq.localsignage.feature.app.device

import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignageScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

object SignageDeviceFleet {
    suspend fun sync(resource: SignageResource, file: File, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) {
                val client = LocalDeviceClient(target)
                val exists = client.resourceExists(resource.hash)
                if (exists.exists) {
                    FleetResult(target.deviceId, target.deviceName, true, true, "ALREADY_EXISTS")
                } else {
                    val uploaded = client.upload(resource, file)
                    FleetResult(target.deviceId, target.deviceName, uploaded.exists, false, if (uploaded.exists) "UPLOADED" else "UPLOAD_FAILED")
                }
            }
        }.awaitAll()
    }

    suspend fun command(action: String, resource: SignageResource?, value: Int?, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        command(action, resource, null, value, targets)
    }

    suspend fun command(action: String, resource: SignageResource?, playlist: SignagePlaylist?, value: Int?, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) {
                val client = LocalDeviceClient(target)
                val remoteResourceId = resource?.let {
                    val exists = client.resourceExists(it.hash)
                    exists.resourceId
                }
                if (resource != null && remoteResourceId == null) {
                    FleetResult(target.deviceId, target.deviceName, false, false, "RESOURCE_NOT_SYNCED")
                } else {
                    val status = client.status()
                    val result = client.command(action, remoteResourceId, playlist?.id, value, status.commandRevision + 1L)
                    FleetResult(target.deviceId, target.deviceName, result.success, false, if (result.success) "COMMAND_ACCEPTED" else "COMMAND_FAILED")
                }
            }
        }.awaitAll()
    }

    suspend fun syncPlaylist(playlist: SignagePlaylist, scenes: List<SignageScene>, resources: Map<String, SignageResource>, files: Map<String, File>, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) {
                val client = LocalDeviceClient(target)
                val remoteResourceIds = mutableMapOf<String, String>()
                var failure: String? = null
                scenes.forEach { scene ->
                    if (failure != null) return@forEach
                    val resource = resources[scene.resourceId]
                    val file = resource?.let { files[it.id] }
                    if (resource == null || file == null) {
                        failure = "RESOURCE_NOT_FOUND"
                        return@forEach
                    }
                    val exists = client.resourceExists(resource.hash)
                    val remoteId = exists.resourceId ?: client.upload(resource, file).resourceId
                    if (remoteId == null) failure = "RESOURCE_SYNC_FAILED" else remoteResourceIds[resource.id] = remoteId
                }
                if (failure != null) {
                    FleetResult(target.deviceId, target.deviceName, false, false, failure.toString())
                } else {
                    val scenesSaved = scenes.all { scene ->
                        val remoteId = remoteResourceIds[scene.resourceId] ?: return@all false
                        client.saveScene(scene, remoteId)
                    }
                    val playlistSaved = scenesSaved && client.savePlaylist(playlist)
                    FleetResult(target.deviceId, target.deviceName, playlistSaved, false, if (playlistSaved) "PLAYLIST_SYNCED" else "PLAYLIST_SYNC_FAILED")
                }
            }
        }.awaitAll()
    }

    data class FleetResult(
        val deviceId: String,
        val deviceName: String,
        val success: Boolean,
        val skipped: Boolean,
        val code: String
    )
}
