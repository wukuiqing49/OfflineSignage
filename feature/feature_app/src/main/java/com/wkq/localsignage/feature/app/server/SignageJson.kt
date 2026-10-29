package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.model.*
import com.wkq.localsignage.feature.app.device.SignageDeviceFleet
import com.wkq.localsignage.monetization.CommercialAccessState
import org.json.JSONArray
import org.json.JSONObject

/** HTTP 与备份共享的无状态 JSON 编码，不持有 Server、Activity 或数据库。 */
internal object SignageJson {
    private fun quote(value: String): String = JSONObject.quote(value)
    fun deviceAssignmentJson(assignment: com.wkq.localsignage.feature.app.model.DeviceAssignment): String =
        "{\"deviceId\":${quote(assignment.deviceId)},\"playlistId\":${quote(assignment.playlistId)}," +
            "\"desiredRevision\":${assignment.desiredRevision},\"desiredPlaying\":${assignment.desiredPlaying}," +
            "\"state\":${quote(assignment.state)},\"appliedRevision\":${assignment.appliedRevision ?: "null"}," +
            "\"lastSyncAt\":${assignment.lastSyncAt ?: "null"},\"lastError\":${assignment.lastError?.let(::quote) ?: "null"}}"
    fun pairedDeviceJson(device: PairedDevice): String = "{\"deviceId\":${quote(device.deviceId)},\"deviceName\":${quote(device.deviceName)},\"host\":${quote(device.host)},\"port\":${device.port},\"pairedAt\":${device.pairedAt},\"groupName\":${device.groupName?.let(::quote) ?: "null"}}"
    fun fleetResultsJson(results: List<SignageDeviceFleet.FleetResult>): String = jsonArray(results) { result ->
        "{\"deviceId\":${quote(result.deviceId)},\"deviceName\":${quote(result.deviceName)},\"success\":${result.success},\"skipped\":${result.skipped},\"code\":${quote(result.code)}}"
    }
    fun fleetStatusesJson(statuses: List<SignageDeviceFleet.FleetStatus>): String = jsonArray(statuses) { status ->
        "{\"deviceId\":${quote(status.deviceId)},\"deviceName\":${quote(status.deviceName)},\"host\":${quote(status.host)},\"port\":${status.port}," +
            "\"state\":${quote(status.state.name)},\"checkedAt\":${status.checkedAt}," +
            "\"currentResourceId\":${status.currentResourceId?.let(::quote) ?: "null"},\"currentSceneId\":${status.currentSceneId?.let(::quote) ?: "null"}," +
            "\"currentPlaylistId\":${status.currentPlaylistId?.let(::quote) ?: "null"},\"playing\":${status.playing},\"volume\":${status.volume},\"muted\":${status.muted}," +
            "\"currentResourceName\":${status.currentResourceName?.let(::quote) ?: "null"}," +
            "\"currentResourceKind\":${status.currentResourceKind?.let(::quote) ?: "null"}," +
            "\"currentResourceContent\":${status.currentResourceContent?.let(::quote) ?: "null"}," +
            "\"currentResourceSourceUri\":${status.currentResourceSourceUri?.let(::quote) ?: "null"}," +
            "\"currentResourceMimeType\":${status.currentResourceMimeType?.let(::quote) ?: "null"}," +
            "\"currentResourceTextSizeSp\":${status.currentResourceTextSizeSp ?: "null"}," +
            "\"currentResourceTextColor\":${status.currentResourceTextColor?.let(::quote) ?: "null"}," +
            "\"currentResourceTextBackgroundColor\":${status.currentResourceTextBackgroundColor?.let(::quote) ?: "null"}," +
            "\"currentResourceFontFamily\":${status.currentResourceFontFamily?.let(::quote) ?: "null"}," +
            "\"currentResourceUrl\":${status.currentResourceUrl?.let(::quote) ?: "null"}," +
            "\"error\":${status.error?.let(::quote) ?: "null"},\"commandRevision\":${status.commandRevision}}"
    }

    fun commercialAccessJson(access: CommercialAccessState): String = "{" +
        "\"mode\":${quote(access.mode.name)}," +
        "\"advancedEnabled\":${access.canUseAdvancedContent}," +
        "\"multiDeviceEnabled\":${access.canUseMultiDevice}," +
        "\"resourceCount\":${access.resourceCount}," +
        "\"resourceLimit\":${access.resourceLimit ?: "null"}," +
        "\"playlistCount\":${access.playlistCount}," +
        "\"playlistLimit\":${access.playlistLimit ?: "null"}," +
        "\"canAddBasicMedia\":${access.canAddBasicMedia}" +
        "}"

    fun sceneJson(scene: SignageScene): String = "{\"id\":${quote(scene.id)},\"name\":${quote(scene.name)},\"resourceId\":${quote(scene.resourceId)},\"layoutTemplate\":${quote(scene.layoutTemplate)},\"sidebarResourceId\":${scene.sidebarResourceId?.let(::quote) ?: "null"},\"fitMode\":${quote(scene.fitMode)},\"cropGravity\":${quote(scene.cropGravity)},\"backgroundType\":${quote(scene.backgroundType)},\"backgroundColor\":${scene.backgroundColor?.let(::quote) ?: "null"},\"volume\":${scene.volume ?: "null"},\"muted\":${scene.muted},\"playbackSpeed\":${PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(scene.playbackSpeed)},\"transitionEffect\":${quote(ImageTransitionPolicy.normalize(scene.transitionEffect))},\"overlays\":${overlaysJson(scene.overlays)}}"
    fun playlistJson(playlist: SignagePlaylist): String = "{\"id\":${quote(playlist.id)},\"name\":${quote(playlist.name)},\"loop\":${playlist.loop},\"items\":[${playlist.items.joinToString { "{\"sceneId\":${quote(it.sceneId)},\"durationMs\":${it.durationMs ?: "null"},\"enabled\":${it.enabled}}" }}]}"
    fun playlistScheduleJson(schedule: PlaylistSchedule): String = "{\"id\":${quote(schedule.id)},\"playlistId\":${quote(schedule.playlistId)},\"weekdays\":[${schedule.weekdays.sorted().joinToString()}],\"startMinute\":${schedule.startMinute},\"endMinute\":${schedule.endMinute},\"priority\":${schedule.priority},\"enabled\":${schedule.enabled},\"updatedAt\":${schedule.updatedAt}}"
    fun sessionJson(session: com.wkq.localsignage.feature.app.model.ControlSession?): String = session?.let { "{\"sessionId\":${quote(it.sessionId)},\"clientName\":${quote(it.clientName)},\"expiresAt\":${it.expiresAt}}" } ?: "null"
    fun overlaysJson(overlays: List<SignageOverlay>): String = JSONArray().apply {
        overlays.forEach { overlay -> put(JSONObject().apply {
            put("id", overlay.id); put("type", overlay.type); put("content", overlay.content)
            put("horizontalPosition", overlay.horizontalPosition); put("verticalPosition", overlay.verticalPosition)
            put("textSizeSp", overlay.textSizeSp); put("textColor", overlay.textColor); put("backgroundColor", overlay.backgroundColor)
            put("paddingDp", overlay.paddingDp); put("cornerRadiusDp", overlay.cornerRadiusDp); put("fontFamily", overlay.fontFamily)
            put("speedDpPerSecond", overlay.speedDpPerSecond)
            put("enabled", overlay.enabled); put("zIndex", overlay.zIndex)
            put("widthPercent", overlay.widthPercent); put("heightPercent", overlay.heightPercent)
        }) }
    }.toString()

}
