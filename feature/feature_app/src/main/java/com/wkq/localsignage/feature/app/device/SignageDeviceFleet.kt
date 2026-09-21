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
    private val controlDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val transferDispatcher = Dispatchers.IO.limitedParallelism(2)
    suspend fun statuses(targets: List<PairedDevice>): List<FleetStatus> = coroutineScope {
        targets.distinctBy { it.deviceId }.map { target ->
            async(controlDispatcher) {
                val checkedAt = System.currentTimeMillis()
                val remote = LocalDeviceClient(target).status()
                val state = when {
                    remote.status == 401 -> FleetStatusState.UNAUTHORIZED
                    remote.status == -1 -> FleetStatusState.TIMEOUT
                    remote.status !in 200..299 -> FleetStatusState.OFFLINE
                    remote.deviceId != target.deviceId -> FleetStatusState.INVALID_RESPONSE
                    else -> FleetStatusState.ONLINE
                }
                FleetStatus(
                    deviceId = target.deviceId,
                    deviceName = remote.deviceName ?: target.deviceName,
                    host = target.host,
                    port = target.port,
                    state = state,
                    checkedAt = checkedAt,
                    currentResourceId = remote.currentResourceId,
                    currentSceneId = remote.currentSceneId,
                    currentPlaylistId = remote.currentPlaylistId,
                    playing = remote.playing,
                    volume = remote.volume,
                    muted = remote.muted,
                    error = remote.error,
                    commandRevision = remote.commandRevision,
                    currentResourceName = remote.currentResourceName,
                    currentResourceKind = remote.currentResourceKind,
                    currentResourceContent = remote.currentResourceContent,
                    currentResourceSourceUri = remote.currentResourceSourceUri,
                    currentResourceMimeType = remote.currentResourceMimeType,
                    currentResourceTextSizeSp = remote.currentResourceTextSizeSp,
                    currentResourceTextColor = remote.currentResourceTextColor,
                    currentResourceTextBackgroundColor = remote.currentResourceTextBackgroundColor,
                    currentResourceFontFamily = remote.currentResourceFontFamily,
                    currentResourceUrl = remote.currentResourceUrl
                )
            }
        }.awaitAll()
    }

    suspend fun sync(resource: SignageResource, file: File?, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        targets.distinctBy { it.deviceId }.map { target ->
            async(transferDispatcher) {
                val client = LocalDeviceClient(target)
                val exists = client.resourceExists(resource.hash)
                if (exists.exists) {
                    FleetResult(target.deviceId, target.deviceName, true, true, "ALREADY_EXISTS")
                } else {
                    val uploaded = if (resource.isLocalFile && file != null) client.upload(resource, file) else client.saveVirtualResource(resource)
                    FleetResult(target.deviceId, target.deviceName, uploaded.exists, false, if (uploaded.exists) "UPLOADED" else "UPLOAD_FAILED")
                }
            }
        }.awaitAll()
    }

    suspend fun command(action: String, resource: SignageResource?, value: Int?, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        command(action, resource, null, value, targets)
    }

    suspend fun command(action: String, resource: SignageResource?, playlist: SignagePlaylist?, value: Int?, targets: List<PairedDevice>): List<FleetResult> = coroutineScope {
        targets.distinctBy { it.deviceId }.map { target ->
            async(controlDispatcher) {
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
        targets.distinctBy { it.deviceId }.map { target ->
            async(transferDispatcher) {
                val client = LocalDeviceClient(target)
                val remoteResourceIds = mutableMapOf<String, String>()
                var failure: String? = null
                scenes.forEach { scene ->
                    if (failure != null) return@forEach
                    val resource = resources[scene.resourceId]
                    val file = resource?.let { files[it.id] }
                    if (resource == null || resource.isLocalFile && file == null) {
                        failure = "RESOURCE_NOT_FOUND"
                        return@forEach
                    }
                    val exists = client.resourceExists(resource.hash)
                    if (exists.status !in 200..299) {
                        failure = "RESOURCE_LOOKUP_FAILED_${exists.status}"
                        return@forEach
                    }
                    val synced = if (exists.resourceId != null) {
                        null
                    } else if (resource.isLocalFile) {
                        client.upload(resource, checkNotNull(file))
                    } else {
                        client.saveVirtualResource(resource)
                    }
                    val remoteId = exists.resourceId ?: synced?.resourceId
                    if (remoteId == null) {
                        failure = "RESOURCE_SYNC_FAILED_${synced?.status ?: -1}"
                    } else {
                        remoteResourceIds[resource.id] = remoteId
                    }
                }
                if (failure != null) {
                    FleetResult(target.deviceId, target.deviceName, false, false, failure.orEmpty())
                } else {
                    var sceneFailure: String? = null
                    scenes.forEach { scene ->
                        if (sceneFailure != null) return@forEach
                        val remoteId = remoteResourceIds[scene.resourceId]
                        if (remoteId == null) {
                            sceneFailure = "RESOURCE_MAPPING_MISSING"
                        } else {
                            val result = client.saveSceneResult(scene, remoteId)
                            if (result.status !in 200..299) {
                                sceneFailure = "SCENE_SYNC_FAILED_${result.status}${result.errorCode?.let { "_$it" }.orEmpty()}"
                            }
                        }
                    }
                    if (sceneFailure != null) {
                        FleetResult(target.deviceId, target.deviceName, false, false, sceneFailure.orEmpty())
                    } else {
                        val result = client.savePlaylistResult(playlist)
                        val saved = result.status in 200..299
                        FleetResult(
                            target.deviceId,
                            target.deviceName,
                            saved,
                            false,
                            if (saved) "PLAYLIST_SYNCED" else "PLAYLIST_SYNC_FAILED_${result.status}${result.errorCode?.let { "_$it" }.orEmpty()}"
                        )
                    }
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

    data class FleetStatus(
        val deviceId: String,
        val deviceName: String,
        val host: String,
        val port: Int,
        val state: FleetStatusState,
        val checkedAt: Long,
        val currentResourceId: String?,
        val currentSceneId: String?,
        val currentPlaylistId: String?,
        val playing: Boolean,
        val volume: Int,
        val muted: Boolean,
        val error: String?,
        val commandRevision: Long,
        val currentResourceName: String? = null,
        val currentResourceKind: String? = null,
        val currentResourceContent: String? = null,
        val currentResourceSourceUri: String? = null,
        val currentResourceMimeType: String? = null,
        val currentResourceTextSizeSp: Int? = null,
        val currentResourceTextColor: String? = null,
        val currentResourceTextBackgroundColor: String? = null,
        val currentResourceFontFamily: String? = null,
        val currentResourceUrl: String? = null
    )

    enum class FleetStatusState { ONLINE, OFFLINE, UNAUTHORIZED, TIMEOUT, INVALID_RESPONSE }
}
