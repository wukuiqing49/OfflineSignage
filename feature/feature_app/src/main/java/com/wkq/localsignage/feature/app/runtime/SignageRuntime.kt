package com.wkq.localsignage.feature.app.runtime

import android.content.Context
import android.net.Uri
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.ControlSession
import com.wkq.localsignage.feature.app.model.SignagePlaylist
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.model.PlaybackErrorRecord
import com.wkq.localsignage.feature.app.model.SignageSettings
import com.wkq.localsignage.feature.app.model.PairedDevice
import com.wkq.localsignage.feature.app.storage.SignageStore
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArraySet

object SignageRuntime {
    const val SERVER_PORT = 8080

    private var store: SignageStore? = null
    private var listener: PlaybackListener? = null
    private var contentListener: (() -> Unit)? = null
    private val stateListeners = CopyOnWriteArraySet<() -> Unit>()

    @Synchronized
    fun initialize(context: Context) {
        if (store == null) store = SignageStore(context.applicationContext)
        requireStore().ensureDefaultContent()
    }

    fun startServer(context: Context) = initialize(context)
    fun stopServer() = Unit

    fun register(listener: PlaybackListener) { this.listener = listener }
    fun unregister(listener: PlaybackListener) { if (this.listener === listener) this.listener = null }
    fun registerContentListener(listener: () -> Unit) { contentListener = listener }
    fun unregisterContentListener() { contentListener = null }
    fun registerStateListener(listener: () -> Unit) { stateListeners += listener }
    fun unregisterStateListener(listener: () -> Unit) { stateListeners -= listener }

    fun state(): SignageState = requireStore().state(SERVER_PORT)
    fun resources(): List<SignageResource> = requireStore().resources()
    fun resource(id: String?): SignageResource? = requireStore().resource(id)
    fun resourceByHash(hash: String?): SignageResource? = requireStore().resourceByHash(hash)
    fun fileFor(resource: SignageResource) = requireStore().fileFor(resource)
    fun scenes(): List<SignageScene> = requireStore().scenes()
    fun scene(id: String?): SignageScene? = requireStore().scene(id)
    fun playlists(): List<SignagePlaylist> = requireStore().playlists()
    fun playlist(id: String?): SignagePlaylist? = requireStore().playlist(id)
    fun controlToken(): String = requireStore().controlToken()
    fun pairedDevices(): List<PairedDevice> = requireStore().pairedDevices()
    fun pairedDevice(deviceId: String): PairedDevice? = requireStore().pairedDevice(deviceId)
    fun savePairedDevice(device: PairedDevice): PairedDevice = requireStore().savePairedDevice(device)
    fun deletePairedDevice(deviceId: String): Boolean = requireStore().deletePairedDevice(deviceId)
    fun controlSession(): ControlSession? = requireStore().controlSession()
    fun acquireControlSession(clientName: String, takeover: Boolean = false): ControlSession? = requireStore().acquireControlSession(clientName, takeover)
    fun heartbeatControlSession(sessionId: String): ControlSession? = requireStore().heartbeatControlSession(sessionId)
    fun releaseControlSession(sessionId: String): Boolean = requireStore().releaseControlSession(sessionId)
    fun hasControlSession(sessionId: String): Boolean = requireStore().hasControlSession(sessionId)
    fun acceptCommandRevision(revision: Long?): Boolean = requireStore().acceptCommandRevision(revision)
    fun canAcceptCommandRevision(revision: Long?): Boolean = requireStore().canAcceptCommandRevision(revision)
    fun commitCommandRevision(revision: Long?): Boolean = requireStore().commitCommandRevision(revision)
    fun settings(): SignageSettings = requireStore().settings()
    fun setSettings(value: SignageSettings) = requireStore().setSettings(value).also { notifyContentChanged() }
    fun playbackErrors(limit: Int = 100): List<PlaybackErrorRecord> = requireStore().playbackErrors(limit)
    fun clearPlaybackErrors() = requireStore().clearPlaybackErrors()
    fun recordPlaybackError(mediaId: String?, sceneId: String?, errorCode: String, action: String, attempt: Int) {
        requireStore().recordPlaybackError(mediaId, sceneId, errorCode, action, attempt)
    }

    fun importResource(context: Context, uri: Uri, name: String, mimeType: String): SignageResource =
        requireStore().importUri(uri, name, mimeType).also { notifyContentChanged() }

    fun saveUpload(name: String, mimeType: String, input: InputStream): SignageResource =
        requireStore().saveUpload(name, mimeType, input).also { notifyContentChanged() }

    fun saveRemote(url: String, name: String? = null): SignageResource =
        requireStore().saveRemote(url, name).also { notifyContentChanged() }

    fun deleteResource(id: String): Boolean = requireStore().deleteResource(id).also { notifyContentChanged() }
    fun deleteScene(id: String): Boolean = requireStore().deleteScene(id).also { notifyContentChanged() }
    fun deletePlaylist(id: String): Boolean = requireStore().deletePlaylist(id).also { notifyContentChanged() }

    fun saveScene(scene: SignageScene): SignageScene = requireStore().saveScene(scene).also { notifyContentChanged() }
    fun savePlaylist(playlist: SignagePlaylist): SignagePlaylist = requireStore().savePlaylist(playlist).also { notifyContentChanged() }

    fun selectResource(id: String) {
        requireStore().setCurrentResource(id)
        requireStore().scenes().firstOrNull { it.resourceId == id }?.let { requireStore().setCurrentSceneId(it.id) }
        notifyState()
    }

    fun selectScene(id: String) {
        val scene = requireStore().scene(id) ?: return
        requireStore().setCurrentSceneId(scene.id)
        requireStore().setCurrentResource(scene.resourceId)
        notifyState()
    }

    fun selectPlaylist(id: String) {
        if (requireStore().playlist(id) != null) requireStore().setCurrentPlaylistId(id)
        notifyState()
    }

    fun setPlaying(value: Boolean) { requireStore().setPlaying(value); notifyState() }
    fun setPosition(value: Long) { requireStore().setPosition(value); notifyState() }
    fun setError(value: String?) { requireStore().setError(value); notifyState() }

    fun notifyState() {
        val currentState = state()
        listener?.onStateChanged(currentState)
        stateListeners.forEach { callback -> runCatching { callback() } }
    }

    private fun notifyContentChanged() {
        notifyState()
        contentListener?.invoke()
    }

    fun command(action: String, resourceId: String? = null, value: Int? = null): SignageState {
        val currentStore = requireStore()
        when (action.uppercase()) {
            "PLAY" -> {
                val target = currentStore.resource(resourceId)
                    ?: currentStore.resource(currentStore.currentResourceId())
                    ?: currentStore.resources().firstOrNull()
                target?.let { selectResource(it.id) }
                currentStore.setPlaying(target != null)
            }
            "PAUSE", "STOP" -> currentStore.setPlaying(false)
            "NEXT" -> selectRelative(1)
            "PREVIOUS" -> selectRelative(-1)
            "VOLUME" -> value?.let(currentStore::setVolume)
            "MUTE" -> currentStore.setMuted(true)
            "UNMUTE" -> currentStore.setMuted(false)
        }
        notifyState()
        return currentStore.state(SERVER_PORT)
    }

    private fun selectRelative(offset: Int) {
        val resources = requireStore().resources()
        if (resources.isEmpty()) return
        val currentIndex = resources.indexOfFirst { it.id == requireStore().currentResourceId() }
        val nextIndex = (if (currentIndex < 0) 0 else currentIndex + offset + resources.size) % resources.size
        selectResource(resources[nextIndex].id)
        requireStore().setPlaying(true)
    }

    private fun requireStore(): SignageStore = checkNotNull(store) { "SignageRuntime is not initialized" }
}
