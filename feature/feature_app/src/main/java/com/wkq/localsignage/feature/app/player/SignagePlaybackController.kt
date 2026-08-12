package com.wkq.localsignage.feature.app.player

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object SignagePlaybackController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val supervisor = object : Runnable {
        override fun run() {
            val current = player
            if (current != null && SignageRuntime.state().playing && current.mediaItemCount > 0 &&
                !current.isPlaying && current.playbackState == Player.STATE_IDLE
            ) {
                runCatching {
                    current.prepare()
                    current.playWhenReady = true
                    SignageRuntime.setError("SUPERVISOR_RECOVERY")
                }.onFailure {
                    SignageRuntime.setError("SUPERVISOR_FAILED")
                }
            }
            mainHandler.postDelayed(this, SUPERVISOR_INTERVAL_MS)
        }
    }
    private val positionSaver = object : Runnable {
        override fun run() {
            player?.let { SignageRuntime.setPosition(it.currentPosition) }
            mainHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }
    private var player: ExoPlayer? = null
    private var listener: PlaybackListener? = null
    private var attachedView: PlayerView? = null
    private var loadedPlaylistId: String? = null
    private var loadedSceneIds: List<String> = emptyList()
    private var loadedLoop = true
    private val retryAttempts = mutableMapOf<String, Int>()
    private val failedMediaIds = mutableSetOf<String>()
    private var fallbackActive = false

    @Synchronized
    fun initialize(context: Context) {
        if (player != null) return
        SignageRuntime.registerContentListener { refreshContent() }
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
                    if (mediaItem?.mediaId != SignageRuntime.settings().fallbackSceneId) fallbackActive = false
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
        mainHandler.postDelayed(supervisor, SUPERVISOR_INTERVAL_MS)
        runOnMainAndWait { applyAudioState(); loadPlaylist(restorePosition = true); true }
    }

    fun attach(view: PlayerView, listener: PlaybackListener) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "PlayerView must be attached on the main thread" }
        this.listener = listener
        attachedView = view
        view.player = requirePlayer()
        loadPlaylist(restorePosition = true)
        applyDisplayState()
        publish()
    }

    fun detach(view: PlayerView) {
        if (view.player === player) view.player = null
        if (attachedView === view) attachedView = null
        listener = null
    }

    fun release() {
        mainHandler.removeCallbacks(positionSaver)
        mainHandler.removeCallbacks(supervisor)
        player?.release()
        SignageRuntime.unregisterContentListener()
        player = null
        loadedPlaylistId = null
        loadedSceneIds = emptyList()
        attachedView = null
        retryAttempts.clear()
        failedMediaIds.clear()
        fallbackActive = false
        listener = null
    }

    fun togglePause() = applyCommand("TOGGLE")

    fun refreshContent() {
        if (player == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            loadPlaylist(restorePosition = true, forceReload = true)
            applyDisplayState()
            publish()
        } else {
            mainHandler.post { refreshContent() }
        }
    }

    fun applyCommand(action: String, resourceId: String? = null, sceneId: String? = null, playlistId: String? = null, value: Int? = null, revision: Long? = null): Boolean {
        val normalized = action.uppercase()
        if (normalized !in SUPPORTED_ACTIONS) return false
        return runOnMainAndWait {
            val validRequest = SignageRuntime.canAcceptCommandRevision(revision) &&
                (resourceId == null || SignageRuntime.resource(resourceId) != null) &&
                (sceneId == null || SignageRuntime.scene(sceneId) != null) &&
                (playlistId == null || SignageRuntime.playlist(playlistId) != null)
            if (!validRequest) {
                false
            } else when (normalized) {
                "PLAY", "RESUME" -> {
                    val targetScene = sceneId?.let(SignageRuntime::scene)
                        ?: resourceId?.let { targetResourceId ->
                            SignageRuntime.scenes().firstOrNull { it.resourceId == targetResourceId }
                        }
                    targetScene?.let { scene ->
                        SignageRuntime.selectScene(scene.id)
                        loadTargetScene(scene)
                    } ?: playlistId?.let { targetPlaylistId ->
                        SignageRuntime.selectPlaylist(targetPlaylistId)
                        loadPlaylist(restorePosition = false, forceReload = true, followCurrentScene = false)
                    } ?: loadPlaylist(restorePosition = false, forceReload = true)
                    requirePlayer().play()
                    true
                }
                "PAUSE" -> { requirePlayer().pause(); true }
                "STOP" -> {
                    requirePlayer().stop()
                    SignageRuntime.setPlaying(false)
                    SignageRuntime.setPosition(0L)
                    true
                }
                "TOGGLE" -> {
                    if (requirePlayer().isPlaying) {
                        requirePlayer().pause()
                    } else {
                        loadPlaylist(restorePosition = true)
                        requirePlayer().play()
                    }
                    true
                }
                "NEXT" -> { requirePlayer().seekToNextMediaItem(); true }
                "PREVIOUS" -> { requirePlayer().seekToPreviousMediaItem(); true }
                "VOLUME" -> if (value == null) false else {
                    SignageRuntime.command("VOLUME", value = value)
                    applyAudioState()
                    true
                }
                "MUTE" -> { SignageRuntime.command("MUTE"); applyAudioState(); true }
                "UNMUTE" -> { SignageRuntime.command("UNMUTE"); applyAudioState(); true }
                "PLAY_SCENE" -> {
                    val targetScene = sceneId?.let(SignageRuntime::scene)
                    if (targetScene == null) false else {
                        SignageRuntime.selectScene(targetScene.id)
                        loadTargetScene(targetScene)
                        requirePlayer().play()
                        true
                    }
                }
                "PLAY_PLAYLIST" -> {
                    val targetPlaylistId = playlistId
                    if (targetPlaylistId == null) false else {
                        SignageRuntime.selectPlaylist(targetPlaylistId)
                        loadPlaylist(restorePosition = false, forceReload = true, followCurrentScene = false)
                        requirePlayer().play()
                        true
                    }
                }
                else -> false
            }
            .let { executed ->
                if (!executed || !SignageRuntime.commitCommandRevision(revision)) {
                    false
                } else {
                    SignageRuntime.setError(null)
                    publish()
                    true
                }
            }
        }
    }

    private fun loadPlaylist(
        restorePosition: Boolean,
        forceReload: Boolean = false,
        followCurrentScene: Boolean = true
    ) {
        val playlist = SignageRuntime.playlist(SignageRuntime.state().currentPlaylistId)
            ?: SignageRuntime.playlists().firstOrNull()
            ?: run {
                clearQueue("NO_PLAYLIST")
                return
            }
        val currentScene = SignageRuntime.scene(SignageRuntime.state().currentSceneId)
        if (followCurrentScene && currentScene != null && playlist.items.none { it.enabled && it.sceneId == currentScene.id }) {
            loadTargetScene(currentScene)
            return
        }
        val enabledItems = playlist.items.filter { it.enabled && SignageRuntime.scene(it.sceneId) != null }
        val scenes = enabledItems.mapNotNull { SignageRuntime.scene(it.sceneId) }
        if (scenes.isEmpty()) {
            clearQueue("NO_PLAYABLE_SCENE")
            return
        }
        loadScenes(
            scenes = scenes,
            playlistId = playlist.id,
            loop = playlist.loop,
            restorePosition = restorePosition,
            forceReload = forceReload
        )
    }

    private fun loadTargetScene(scene: SignageScene) {
        val currentPlaylist = SignageRuntime.playlist(SignageRuntime.state().currentPlaylistId)
        val targetIsInCurrentPlaylist = currentPlaylist?.items?.any { it.enabled && it.sceneId == scene.id } == true
        if (targetIsInCurrentPlaylist) {
            loadPlaylist(restorePosition = false, forceReload = true)
            seekToScene(scene.id)
            return
        }
        val containingPlaylist = SignageRuntime.playlists().firstOrNull { playlist ->
            playlist.items.any { it.enabled && it.sceneId == scene.id }
        }
        if (containingPlaylist != null) {
            SignageRuntime.selectPlaylist(containingPlaylist.id)
            loadPlaylist(restorePosition = false, forceReload = true)
            seekToScene(scene.id)
        } else {
            loadScenes(listOf(scene), playlistId = null, loop = false, restorePosition = false, forceReload = true)
        }
    }

    private fun loadScenes(
        scenes: List<SignageScene>,
        playlistId: String?,
        loop: Boolean,
        restorePosition: Boolean,
        forceReload: Boolean
    ) {
        if (scenes.isEmpty()) return
        val ids = scenes.map { it.id }
        if (!forceReload && loadedPlaylistId == playlistId && loadedLoop == loop && loadedSceneIds == ids && requirePlayer().currentMediaItem != null) {
            applyAudioState()
            applyDisplayState()
            return
        }
        retryAttempts.clear()
        failedMediaIds.clear()
        fallbackActive = false
        val playableScenes = scenes.filter { scene ->
            val resource = SignageRuntime.resource(scene.resourceId)
            resource != null && SignageRuntime.fileFor(resource).isFile
        }
        if (playableScenes.isEmpty()) {
            clearQueue("NO_PLAYABLE_RESOURCE")
            return
        }
        val currentSceneId = SignageRuntime.state().currentSceneId
        val playableIds = playableScenes.map { it.id }
        loadedPlaylistId = playlistId
        loadedSceneIds = playableIds
        loadedLoop = loop
        val index = playableIds.indexOf(currentSceneId).coerceAtLeast(0)
        requirePlayer().setMediaItems(playableScenes.map { scene ->
            val resource = SignageRuntime.resource(scene.resourceId) ?: error("Resource disappeared")
            MediaItem.Builder()
                .setMediaId(scene.id)
                .setUri(SignageRuntime.fileFor(resource).toURI().toString())
                .apply {
                    if (!resource.isVideo) {
                        val duration = SignageRuntime.playlist(playlistId)?.items?.firstOrNull { it.sceneId == scene.id }?.durationMs
                        setImageDurationMs(duration ?: DEFAULT_IMAGE_DURATION_MS)
                    }
                }
                .build()
        }.filterNotNull(), index, if (restorePosition) SignageRuntime.state().positionMs else 0L)
        requirePlayer().repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        requirePlayer().prepare()
        playableScenes.getOrNull(index)?.let { SignageRuntime.selectScene(it.id) }
        applyAudioState()
        applyDisplayState()
        requirePlayer().playWhenReady = SignageRuntime.settings().autoResume && SignageRuntime.state().playing
    }

    private fun recoverFromPlaybackError(error: androidx.media3.common.PlaybackException) {
        val current = requirePlayer()
        val mediaId = current.currentMediaItem?.mediaId
        val sceneId = mediaId
        if (mediaId == null) {
            SignageRuntime.setPlaying(false)
            SignageRuntime.setError(error.errorCodeName)
            SignageRuntime.recordPlaybackError(null, null, error.errorCodeName, "STOP", 1)
            return
        }
        val attempts = retryAttempts[mediaId] ?: 0
        if (attempts == 0) {
            retryAttempts[mediaId] = 1
            SignageRuntime.recordPlaybackError(mediaId, sceneId, error.errorCodeName, "RETRY", 1)
            SignageRuntime.setError("RETRYING_${error.errorCodeName}")
            mainHandler.postDelayed({
                if (player === current) {
                    current.prepare()
                    current.playWhenReady = true
                }
            }, RETRY_BACKOFF_MS)
            return
        }
        retryAttempts.remove(mediaId)
        failedMediaIds += mediaId
        if (skipToNextAvailable(current)) {
            SignageRuntime.recordPlaybackError(mediaId, sceneId, error.errorCodeName, "SKIP", attempts + 1)
            SignageRuntime.setError("SKIPPED_${error.errorCodeName}")
            return
        }
        if (activateFallback(error.errorCodeName, mediaId, sceneId)) return
        SignageRuntime.recordPlaybackError(mediaId, sceneId, error.errorCodeName, "STOP", attempts + 1)
        current.stop()
        SignageRuntime.setPlaying(false)
        SignageRuntime.setError(error.errorCodeName)
    }

    private fun activateFallback(errorCode: String, failedMediaId: String, failedSceneId: String?) : Boolean {
        if (fallbackActive) return false
        val fallbackId = SignageRuntime.settings().fallbackSceneId ?: return false
        val fallback = SignageRuntime.scene(fallbackId) ?: return false
        if (fallback.id == failedSceneId || fallback.resourceId == failedMediaId) return false
        val resource = SignageRuntime.resource(fallback.resourceId) ?: return false
        if (!SignageRuntime.fileFor(resource).isFile) return false
        fallbackActive = true
        SignageRuntime.selectScene(fallback.id)
        loadScenes(listOf(fallback), playlistId = null, loop = true, restorePosition = false, forceReload = true)
        fallbackActive = true
        requirePlayer().play()
        SignageRuntime.recordPlaybackError(failedMediaId, failedSceneId, errorCode, "FALLBACK", 2)
        SignageRuntime.setError("FALLBACK_${errorCode}")
        return true
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
        applyDisplayState()
    }

    private fun applyAudioState() {
        val current = requirePlayer()
        val scene = SignageRuntime.scene(current.currentMediaItem?.mediaId)
        val volume = scene?.volume ?: SignageRuntime.state().volume
        current.volume = if (scene?.muted == true || SignageRuntime.state().muted) 0f else volume.coerceIn(0, 100) / 100f
    }

    private fun applyDisplayState() {
        val view = attachedView ?: return
        val scene = SignageRuntime.scene(requirePlayer().currentMediaItem?.mediaId)
        view.resizeMode = when (scene?.fitMode?.uppercase()) {
            "FILL", "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val background = scene?.backgroundColor?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { Color.parseColor(value) }.getOrNull()
        } ?: Color.BLACK
        view.setBackgroundColor(background)
    }

    private fun clearQueue(error: String) {
        val current = requirePlayer()
        current.stop()
        current.clearMediaItems()
        loadedPlaylistId = null
        loadedSceneIds = emptyList()
        SignageRuntime.setPlaying(false)
        SignageRuntime.setPosition(0L)
        SignageRuntime.setError(error)
        attachedView?.setBackgroundColor(Color.BLACK)
    }

    private fun publish() {
        listener?.onStateChanged(SignageRuntime.state().copy(
            playing = player?.isPlaying == true,
            positionMs = player?.currentPosition ?: SignageRuntime.state().positionMs
        ))
    }

    private fun runOnMainAndWait(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching { block() }.getOrElse {
                SignageRuntime.setError("COMMAND_FAILED")
                false
            }
        }
        var result = false
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result = runCatching { block() }.getOrElse {
                    SignageRuntime.setError("COMMAND_FAILED")
                    false
                }
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false
        return result
    }

    private fun requirePlayer(): ExoPlayer = checkNotNull(player) { "SignagePlaybackController is not initialized" }

    private const val POSITION_SAVE_INTERVAL_MS = 1_000L
    private const val COMMAND_TIMEOUT_MS = 3_000L
    private const val DEFAULT_IMAGE_DURATION_MS = 10_000L
    private const val RETRY_BACKOFF_MS = 1_000L
    private const val SUPERVISOR_INTERVAL_MS = 10_000L
    private val SUPPORTED_ACTIONS = setOf("PLAY", "RESUME", "PAUSE", "STOP", "TOGGLE", "NEXT", "PREVIOUS", "VOLUME", "MUTE", "UNMUTE", "PLAY_SCENE", "PLAY_PLAYLIST")
}
