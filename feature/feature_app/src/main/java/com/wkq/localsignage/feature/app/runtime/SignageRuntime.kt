package com.wkq.localsignage.feature.app.runtime

import android.content.Context
import android.net.Uri
import java.io.InputStream
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.server.LocalSignageServer
import com.wkq.localsignage.feature.app.storage.SignageStore

object SignageRuntime {
    const val SERVER_PORT = 8080

    private var store: SignageStore? = null
    private var server: LocalSignageServer? = null
    private var listener: PlaybackListener? = null

    @Synchronized
    fun initialize(context: Context) {
        if (store == null) store = SignageStore(context.applicationContext)
        if (server == null) {
            server = LocalSignageServer(SERVER_PORT, this)
        }
    }

    fun startServer(context: Context) {
        initialize(context)
        server?.start()
        notifyState()
    }

    fun stopServer() {
        server?.stop()
    }

    fun register(listener: PlaybackListener) {
        this.listener = listener
    }

    fun unregister(listener: PlaybackListener) {
        if (this.listener === listener) this.listener = null
    }

    fun state(): SignageState = requireStore().state(SERVER_PORT)
    fun resources(): List<SignageResource> = requireStore().resources()
    fun resource(id: String?): SignageResource? = requireStore().resource(id)
    fun fileFor(resource: SignageResource) = requireStore().fileFor(resource)

    fun importResource(context: Context, uri: Uri, name: String, mimeType: String): SignageResource {
        val resource = requireStore().importUri(uri, name, mimeType)
        notifyState()
        return resource
    }

    fun saveUpload(name: String, mimeType: String, input: InputStream): SignageResource {
        val resource = requireStore().saveUpload(name, mimeType, input)
        notifyState()
        return resource
    }

    fun deleteResource(id: String): Boolean {
        val deleted = requireStore().deleteResource(id)
        notifyState()
        return deleted
    }

    fun command(action: String, resourceId: String? = null, value: Int? = null): SignageState {
        val currentStore = requireStore()
        when (action.uppercase()) {
            "PLAY" -> {
                val target = currentStore.resource(resourceId) ?: currentStore.resource(currentStore.currentResourceId())
                    ?: currentStore.resources().firstOrNull()
                currentStore.setCurrentResource(target?.id)
                currentStore.setPlaying(target != null)
            }
            "PAUSE", "STOP" -> currentStore.setPlaying(false)
            "NEXT" -> selectRelative(1)
            "PREVIOUS" -> selectRelative(-1)
            "VOLUME" -> value?.let(currentStore::setVolume)
            "MUTE" -> currentStore.setMuted(true)
            "UNMUTE" -> currentStore.setMuted(false)
            "DELETE" -> resourceId?.let(currentStore::deleteResource)
        }
        notifyState()
        return currentStore.state(SERVER_PORT)
    }

    fun notifyState() {
        listener?.onStateChanged(state())
    }

    private fun selectRelative(offset: Int) {
        val resources = requireStore().resources()
        if (resources.isEmpty()) return
        val currentIndex = resources.indexOfFirst { it.id == requireStore().currentResourceId() }
        val nextIndex = (if (currentIndex < 0) 0 else currentIndex + offset + resources.size) % resources.size
        requireStore().setCurrentResource(resources[nextIndex].id)
        requireStore().setPlaying(true)
    }

    private fun requireStore(): SignageStore = checkNotNull(store) { "SignageRuntime is not initialized" }
}
