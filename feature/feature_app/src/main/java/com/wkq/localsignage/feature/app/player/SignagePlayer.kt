package com.wkq.localsignage.feature.app.player

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.graphics.BitmapFactory
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

class SignagePlayer(
    context: Context,
    private val surfaceView: SurfaceView,
    private val imageView: ImageView,
    private val listener: PlaybackListener
) : SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var loadedResourceId: String? = null

    init {
        surfaceView.holder.addCallback(this)
        SignageRuntime.register(listener)
    }

    fun release() {
        SignageRuntime.unregister(listener)
        surfaceView.holder.removeCallback(this)
        mediaPlayer?.release()
        mediaPlayer = null
        loadedResourceId = null
        imageView.setImageDrawable(null)
    }

    fun sync() {
        val state = SignageRuntime.state()
        val resource = SignageRuntime.resource(state.currentResourceId)
        if (resource == null) {
            mediaPlayer?.stopSafely()
            imageView.setImageDrawable(null)
            surfaceView.visibility = View.GONE
            return
        }
        if (resource.isVideo) {
            imageView.setImageDrawable(null)
            surfaceView.visibility = View.VISIBLE
            if (loadedResourceId != resource.id) prepare(resource.id)
        } else {
            mediaPlayer?.stopSafely()
            prepared = false
            loadedResourceId = resource.id
            surfaceView.visibility = View.GONE
            imageView.setImageBitmap(BitmapFactory.decodeFile(SignageRuntime.fileFor(resource).absolutePath))
        }
        if (prepared) {
            mediaPlayer?.setVolume(if (state.muted) 0f else state.volume / 100f, if (state.muted) 0f else state.volume / 100f)
            if (state.playing && mediaPlayer?.isPlaying != true) mediaPlayer?.start()
            if (!state.playing && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = sync()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mediaPlayer?.setDisplay(holder)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mediaPlayer?.setDisplay(null)
    }

    override fun onPrepared(player: MediaPlayer) {
        prepared = true
        player.setDisplay(surfaceView.holder)
        sync()
    }

    override fun onCompletion(player: MediaPlayer) {
        SignageRuntime.command("NEXT")
        sync()
    }

    override fun onError(player: MediaPlayer, what: Int, extra: Int): Boolean {
        prepared = false
        player.reset()
        SignageRuntime.command("NEXT")
        return true
    }

    private fun prepare(resourceId: String) {
        val resource = SignageRuntime.resource(resourceId) ?: return
        val file = SignageRuntime.fileFor(resource)
        if (!file.isFile) return
        prepared = false
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener(this@SignagePlayer)
            setOnCompletionListener(this@SignagePlayer)
            setOnErrorListener(this@SignagePlayer)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepareAsync()
        }
        loadedResourceId = resource.id
    }

    private fun MediaPlayer.stopSafely() {
        try {
            if (isPlaying) stop()
            reset()
        } catch (_: IllegalStateException) {
            release()
        }
        prepared = false
    }
}
