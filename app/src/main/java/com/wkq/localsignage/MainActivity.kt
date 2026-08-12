package com.wkq.localsignage

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivityMainBinding
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.player.SignagePlayer
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

class MainActivity : BaseActivity<ActivityMainBinding>(), PlaybackListener {
    private lateinit var player: SignagePlayer

    override fun initView() {
        enableEdgeToEdge()
        ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java))
        player = SignagePlayer(this, binding.playerSurface, binding.playerImage, this)
        binding.playButton.setOnClickListener { SignageRuntime.command("PLAY") }
        binding.pauseButton.setOnClickListener { SignageRuntime.command("PAUSE") }
        binding.previousButton.setOnClickListener { SignageRuntime.command("PREVIOUS") }
        binding.nextButton.setOnClickListener { SignageRuntime.command("NEXT") }
        binding.muteButton.setOnClickListener { SignageRuntime.command(if (SignageRuntime.state().muted) "UNMUTE" else "MUTE") }
        binding.volumeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) SignageRuntime.command("VOLUME", value = progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) = Unit
        })
        binding.openControlButton.setOnClickListener { binding.controlHint.visibility = View.VISIBLE }
    }

    override fun initData() = player.sync()

    override fun onStateChanged(state: SignageState) {
        runOnUiThread {
            binding.statusText.text = getString(
                if (state.playing) R.string.playing_status else R.string.paused_status,
                state.deviceName,
                state.volume
            )
            binding.volumeSeekBar.progress = state.volume
            binding.muteButton.text = getString(if (state.muted) R.string.unmute else R.string.mute)
            player.sync()
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
