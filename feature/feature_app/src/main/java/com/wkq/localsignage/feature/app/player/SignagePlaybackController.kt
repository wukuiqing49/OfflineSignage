package com.wkq.localsignage.feature.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object SignagePlaybackController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val positionSaver = object : Runnable {
        override fun run() {
            player?.let { SignageRuntime.setPosition(it.currentPosition) }
            mainHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }
    private var player: ExoPlayer? = null
    private var listener: PlaybackListener? = null
    private var loadedPlaylistId: String? = null
    private var loadedSceneIds: List<String> = emptyList()
    private val retryAttempts = mutableMapOf<String, Int>()
    private val failedMediaIds = mutableSetOf<String>()

    @Synchronized
    fun initialize(context: Context) {
        if (player != null) return
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    SignageRuntime.setPlaying(isPlaying)
                    publish()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.let { retryAttempts.remove(it) }
                    updateCurrentScene()
                    publish()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) updateCurrentScene()
                    publish()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    recoverFromPlaybackError(error)
                    publish()
                }
            })
        }
        mainHandler.post(positionSaver)
        runOnMainAndWait { applyAudioState(); loadPlaylist(restorePosition = true) }
    }

    fun attach(view: PlayerView, listener: PlaybackListener) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "PlayerView must be attached on the main thread" }
        this.listener = listener
        view.player = requirePlayer()
        loadPlaylist(restorePosition = true)
        publish()
    }

    fun detach(view: PlayerView) {
        if (view.player === player) view.player = null
        listener = null
    }

    fun release() {
        mainHandler.removeCallbacks(positionSaver)
        player?.release()
        player = null
        loadedPlaylistId = null
        loadedSceneIds = emptyList()
        retryAttempts.clear()
        failedMediaIds.clear()
        listener = null
    }

    fun togglePause() = applyCommand("TOGGLE")

    fun applyCommand(action: String, resourceId: String? = null, sceneId: String? = null, playlistId: String? = null, value: Int? = null): Boolean {
        val normalized = action.uppercase()
        if (normalized !in SUPPORTED_ACTIONS) return false
        runOnMainAndWait {
            when (normalized) {
                "PLAY", "RESUME" -> {
                    resourceId?.let(SignageRuntime::selectResource)
                    sceneId?.let(SignageRuntime::selectScene)
                    playlistId?.let(SignageRuntime::selectPlaylist)
                    loadPlaylist(restorePosition = false, forceReload = true)
                    sceneId?.let(::seekToScene)
                    resourceId?.let { targetResourceId ->
                        SignageRuntime.scenes().firstOrNull { it.resourceId == targetResourceId }?.id?.let(::seekToScene)
                    }
                    requirePlayer().play()
                }
                "PAUSE" -> requirePlayer().pause()
                "STOP" -> {
                    requirePlayer().stop()
                    SignageRuntime.setPlaying(false)
                    SignageRuntime.setPosition(0L)
                }
                "TOGGLE" -> if (requirePlayer().isPlaying) requirePlayer().pause() else {
                    loadPlaylist(restorePosition = true)
                    requirePlayer().play()
                }
                "NEXT" -> requirePlayer().seekToNextMediaItem()
                "PREVIOUS" -> requirePlayer().seekToPreviousMediaItem()
                "VOLUME" -> value?.let { SignageRuntime.command("VOLUME", value = it); applyAudioState() }
                "MUTE" -> { SignageRuntime.command("MUTE"); applyAudioState() }
                "UNMUTE" -> { SignageRuntime.command("UNMUTE"); applyAudioState() }
                "PLAY_SCENE" -> sceneId?.let {
                    SignageRuntime.selectScene(it)
                    loadPlaylist(restorePosition = false, forceReload = true)
                    seekToScene(it)
                    requirePlayer().play()
                }
                "PLAY_PLAYLIST" -> playlistId?.let {
                    SignageRuntime.selectPlaylist(it)
                    loadPlaylist(restorePosition = false, forceReload = true)
                    requirePlayer().play()
                }
            }
            SignageRuntime.setError(null)
            publish()
        }
        return true
    }

    private fun loadPlaylist(restorePosition: Boolean, forceReload: Boolean = false) {
        val playlist = SignageRuntime.playlist(SignageRuntime.state().currentPlaylistId)
            ?: SignageRuntime.playlists().firstOrNull()
            ?: return
        val enabledItems = playlist.items.filter { it.enabled && SignageRuntime.scene(it.sceneId) != null }
        val scenes = enabledItems.mapNotNull { SignageRuntime.scene(it.sceneId) }
        if (scenes.isEmpty()) return
        val ids = scenes.map { it.id }
        if (!forceReload && loadedPlaylistId == playlist.id && loadedSceneIds == ids && requirePlayer().currentMediaItem != null) {
            applyAudioState()
            return
        }
        retryAttempts.clear()
        failedMediaIds.clear()
        val currentSceneId = SignageRuntime.state().currentSceneId
        loadedPlaylistId = playlist.id
        loadedSceneIds = ids
        val index = ids.indexOf(currentSceneId).coerceAtLeast(0)
        requirePlayer().setMediaItems(scenes.map { scene ->
            val resource = SignageRuntime.resource(scene.resourceId) ?: return@map null
            val playlistItem = enabledItems.firstOrNull { it.sceneId == scene.id }
            MediaItem.Builder()
                .setMediaId(scene.id)
                .setUri(SignageRuntime.fileFor(resource).toURI().toString())
                .apply {
                    if (!resource.isVideo) {
                        setImageDurationMs(playlistItem?.durationMs ?: DEFAULT_IMAGE_DURATION_MS)
                    }
                }
                .build()
        }.filterNotNull(), index, if (restorePosition) SignageRuntime.state().positionMs else 0L)
        requirePlayer().repeatMode = if (playlist.loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        requirePlayer().prepare()
        scenes.getOrNull(index)?.let { SignageRuntime.selectScene(it.id) }
        applyAudioState()
        requirePlayer().playWhenReady = SignageRuntime.state().playing
    }

    private fun recoverFromPlaybackError(error: androidx.media3.common.PlaybackException) {
        val current = requirePlayer()
        val mediaId = current.currentMediaItem?.mediaId
        if (mediaId == null) {
            SignageRuntime.setPlaying(false)
            SignageRuntime.setError(error.errorCodeName)
            return
        }
        val attempts = retryAttempts[mediaId] ?: 0
        if (attempts == 0) {
            retryAttempts[mediaId] = 1
            SignageRuntime.setError("RETRYING_${error.errorCodeName}")
            current.prepare()
            current.playWhenReady = true
            return
        }
        retryAttempts.remove(mediaId)
        failedMediaIds += mediaId
        if (skipToNextAvailable(current)) {
            SignageRuntime.setError("SKIPPED_${error.errorCodeName}")
            return
        }
        current.stop()
        SignageRuntime.setPlaying(false)
        SignageRuntime.setError(error.errorCodeName)
    }

    private fun skipToNextAvailable(current: ExoPlayer): Boolean {
        val count = current.mediaItemCount
        if (count < 2) return false
        val start = current.currentMediaItemIndex
        val canWrap = current.repeatMode == Player.REPEAT_MODE_ALL
        val maxOffset = if (canWrap) count - 1 else count - start - 1
        for (offset in 1..maxOffset) {
            val index = (start + offset) % count
            val candidateId = current.getMediaItemAt(index).mediaId
            if (candidateId !in failedMediaIds) {
                current.seekTo(index, 0L)
                current.prepare()
                current.playWhenReady = true
                return true
            }
        }
        return false
    }

    private fun seekToScene(sceneId: String) {
        val index = loadedSceneIds.indexOf(sceneId)
        if (index >= 0) requirePlayer().seekToDefaultPosition(index)
    }

    private fun updateCurrentScene() {
        val scene = SignageRuntime.scene(requirePlayer().currentMediaItem?.mediaId) ?: return
        SignageRuntime.selectScene(scene.id)
        SignageRuntime.setPosition(requirePlayer().currentPosition)
        applyAudioState()
    }

    private fun applyAudioState() {
        val current = requirePlayer()
        val scene = SignageRuntime.scene(current.currentMediaItem?.mediaId)
        val volume = scene?.volume ?: SignageRuntime.state().volume
        current.volume = if (scene?.muted == true || SignageRuntime.state().muted) 0f else volume.coerceIn(0, 100) / 100f
    }

    private fun publish() {
        listener?.onStateChanged(SignageRuntime.state().copy(
            playing = player?.isPlaying == true,
            positionMs = player?.currentPosition ?: SignageRuntime.state().positionMs
        ))
    }

    private fun runOnMainAndWait(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else {
            val latch = CountDownLatch(1)
            mainHandler.post { try { block() } finally { latch.countDown() } }
            latch.await(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun requirePlayer(): ExoPlayer = checkNotNull(player) { "SignagePlaybackController is not initialized" }

    private const val POSITION_SAVE_INTERVAL_MS = 1_000L
    private const val COMMAND_TIMEOUT_MS = 3_000L
    private const val DEFAULT_IMAGE_DURATION_MS = 10_000L
    private val SUPPORTED_ACTIONS = setOf("PLAY", "RESUME", "PAUSE", "STOP", "TOGGLE", "NEXT", "PREVIOUS", "VOLUME", "MUTE", "UNMUTE", "PLAY_SCENE", "PLAY_PLAYLIST")
}
