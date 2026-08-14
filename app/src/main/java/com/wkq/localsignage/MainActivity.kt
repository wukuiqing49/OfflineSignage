package com.wkq.localsignage

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivityMainBinding
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.player.SignagePlaybackViews
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

class MainActivity : BaseActivity<ActivityMainBinding>(), PlaybackListener {
    private var appliedKeepScreenAwake: Boolean? = null
    private var appliedFullscreen: Boolean? = null
    private var pairingManuallyOpened = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val touchSlopSquared by lazy {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        slop * slop
    }
    private val playbackViews: SignagePlaybackViews
        get() = SignagePlaybackViews(
            playerView = binding.playerView,
            imageSlideshow = binding.imageSlideshow,
            webView = binding.webView,
            textView = binding.textContentView,
            overlayContainer = binding.overlayContainer,
            blurBackgroundView = binding.blurBackgroundView
        )
    private val pairingHandler = Handler(Looper.getMainLooper())
    private val hidePlaybackControls = Runnable {
        binding.playbackStatusBar.visibility = View.GONE
    }
    private val pairingRefresh = object : Runnable {
        override fun run() {
            refreshPairingPanel()
            pairingHandler.postDelayed(this, PAIRING_REFRESH_INTERVAL_MS)
        }
    }

    override fun initView() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java))
        SignagePlaybackController.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyDisplaySettings()
        SignagePlaybackController.attach(playbackViews, this)
        binding.pauseResumeButton.setOnClickListener {
            SignagePlaybackController.togglePause()
            showPlaybackControls()
        }
        binding.showPairingButton.setOnClickListener { openPairingPanel() }
        binding.closePairingButton.setOnClickListener { closePairingPanel() }
        updateContentMode()
    }

    override fun initData() = Unit

    override fun onStateChanged(state: SignageState) {
        runOnUiThread {
            binding.pauseResumeButton.setIconResource(
                if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            binding.pauseResumeButton.contentDescription = getString(if (state.playing) R.string.pause else R.string.resume)
            applyDisplaySettings()
            updateContentMode()
        }
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        applyDisplaySettings()
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.post(pairingRefresh)
    }

    override fun onPause() {
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.removeCallbacks(hidePlaybackControls)
        super.onPause()
    }

    private fun updateContentMode() {
        val hasContent = SignageRuntime.resources().isNotEmpty()
        if (!hasContent) pairingManuallyOpened = false
        val showPairing = !hasContent || pairingManuallyOpened
        binding.pairingPanel.visibility = if (showPairing) View.VISIBLE else View.GONE
        binding.closePairingButton.visibility = if (hasContent) View.VISIBLE else View.GONE
        if (showPairing) {
            hidePlaybackControls.run()
            refreshPairingPanel()
        }
    }

    private fun showPlaybackControls() {
        if (binding.pairingPanel.visibility == View.VISIBLE || SignageRuntime.resources().isEmpty()) return
        binding.playbackStatusBar.visibility = View.VISIBLE
        pairingHandler.removeCallbacks(hidePlaybackControls)
        pairingHandler.postDelayed(hidePlaybackControls, PLAYBACK_CONTROLS_TIMEOUT_MS)
    }

    private fun openPairingPanel() {
        pairingManuallyOpened = true
        updateContentMode()
    }

    private fun closePairingPanel() {
        if (SignageRuntime.resources().isEmpty()) return
        pairingManuallyOpened = false
        updateContentMode()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                val isTap = dx * dx + dy * dy <= touchSlopSquared
                if (isTap && binding.pairingPanel.visibility != View.VISIBLE &&
                    binding.playbackStatusBar.visibility != View.VISIBLE
                ) {
                    showPlaybackControls()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun applyDisplaySettings() {
        val settings = SignageRuntime.settings()
        if (appliedKeepScreenAwake != settings.keepScreenAwake) {
            if (settings.keepScreenAwake) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            appliedKeepScreenAwake = settings.keepScreenAwake
        }
        if (appliedFullscreen != settings.fullscreen) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (settings.fullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
            appliedFullscreen = settings.fullscreen
        }
    }

    private fun refreshPairingPanel() {
        if (binding.pairingPanel.visibility != View.VISIBLE) return
        val code = SignageRuntime.pairingCode(resources.getDimensionPixelSize(R.dimen.pairing_qr_pixels))
        binding.pairingDeviceName.text = SignageRuntime.state().deviceName
        binding.pairingAddress.text = code.controlAddress ?: getString(R.string.pairing_waiting_for_network)
        binding.pairingQrCode.setImageBitmap(code.qrBitmap)
        val pairingAvailable = code.qrBitmap != null
        binding.pairingQrCode.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingAccessCodeLabel.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingAccessCode.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingAccessCode.text = code.accessCode
        binding.pairingHint.text = getString(
            if (pairingAvailable) R.string.pairing_scan_hint else R.string.pairing_network_hint
        )
    }

    override fun onDestroy() {
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.removeCallbacks(hidePlaybackControls)
        SignagePlaybackController.detach(playbackViews)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private companion object {
        const val PAIRING_REFRESH_INTERVAL_MS = 5_000L
        const val PLAYBACK_CONTROLS_TIMEOUT_MS = 4_000L
    }
}
