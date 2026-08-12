package com.wkq.localsignage

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivityMainBinding
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.player.SignagePlaybackController

class MainActivity : BaseActivity<ActivityMainBinding>(), PlaybackListener {

    override fun initView() {
        ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java))
        SignagePlaybackController.initialize(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        SignagePlaybackController.attach(binding.playerView, this)
        binding.pauseResumeButton.setOnClickListener { SignagePlaybackController.togglePause() }
    }

    override fun initData() = Unit

    override fun onStateChanged(state: SignageState) {
        runOnUiThread {
            binding.statusText.text = getString(if (state.playing) R.string.playing_status else R.string.paused_status)
            binding.pauseResumeButton.text = getString(if (state.playing) R.string.pause else R.string.resume)
        }
    }

    override fun onDestroy() {
        SignagePlaybackController.detach(binding.playerView)
        super.onDestroy()
    }
}
