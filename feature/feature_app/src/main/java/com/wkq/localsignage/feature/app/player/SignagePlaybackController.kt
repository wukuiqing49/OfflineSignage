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

object SignagePlaybackController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var listener: PlaybackListener? = null
    private var currentResourceId: String? = null

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
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                override fun onPlaybackStateChanged(playbackState: Int) = publish()
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    SignageRuntime.setPlaying(false)
                    publish()
                }
            })
        }
        loadCurrentResource()
    }

    fun attach(view: PlayerView, listener: PlaybackListener) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "PlayerView must be attached on the main thread" }
        this.listener = listener
        view.player = requirePlayer()
        loadCurrentResource()
        publish()
    }

    fun detach(view: PlayerView) {
        if (view.player === player) view.player = null
        if (listener != null) listener = null
    }

    fun release() {
        player?.release()
        player = null
        currentResourceId = null
        listener = null
    }

    fun togglePause() {
        runOnMain {
            val current = requirePlayer()
            if (current.isPlaying) {
                current.pause()
                SignageRuntime.setPlaying(false)
            } else {
                if (current.currentMediaItem == null) loadCurrentResource()
                current.play()
                SignageRuntime.setPlaying(current.currentMediaItem != null)
            }
            publish()
        }
    }

    fun applyCommand(action: String, resourceId: String? = null): Boolean {
        val normalizedAction = action.uppercase()
        if (normalizedAction !in setOf("PAUSE", "PLAY", "RESUME", "TOGGLE")) return false
        runOnMain {
            when (normalizedAction) {
                "PAUSE" -> {
                    requirePlayer().pause()
                    SignageRuntime.setPlaying(false)
                }
                "PLAY", "RESUME" -> {
                    resourceId?.let(SignageRuntime::selectResource)
                    loadCurrentResource()
                    requirePlayer().play()
                    SignageRuntime.setPlaying(requirePlayer().currentMediaItem != null)
                }
                "TOGGLE" -> togglePause()
            }
            publish()
        }
        return true
    }

    private fun loadCurrentResource() {
        val state = SignageRuntime.state()
        val resource = SignageRuntime.resource(state.currentResourceId) ?: return
        if (currentResourceId == resource.id && requirePlayer().currentMediaItem != null) return
        currentResourceId = resource.id
        requirePlayer().setMediaItem(MediaItem.fromUri(SignageRuntime.fileFor(resource).toURI().toString()))
        requirePlayer().prepare()
        requirePlayer().playWhenReady = state.playing
    }

    private fun publish() {
        listener?.onStateChanged(SignageRuntime.state().copy(playing = player?.isPlaying == true))
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun requirePlayer(): ExoPlayer = checkNotNull(player) { "SignagePlaybackController is not initialized" }
}
