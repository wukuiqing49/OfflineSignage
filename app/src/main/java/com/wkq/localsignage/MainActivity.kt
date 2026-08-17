package com.wkq.localsignage

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
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
    private var hotspotInfo: LocalHotspotController.HotspotInfo? = null
    private val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startLocalHotspot() else openHotspotSettings()
    }
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
        binding.openHotspotSettingsButton.setOnClickListener { toggleLocalHotspot() }
        binding.pairingPanel.doOnLayout { configurePairingLayout(it.width) }
        hotspotInfo = LocalHotspotController.currentInfo()
        updateHotspotUi()
        updateContentMode()
    }

    override fun initData() = Unit

    private fun configurePairingLayout(availableWidth: Int) {
        val compact = availableWidth < resources.getDimensionPixelSize(R.dimen.pairing_compact_breakpoint)
        val padding = resources.getDimensionPixelSize(
            if (compact) R.dimen.pairing_compact_screen_padding else R.dimen.pairing_screen_padding
        )
        val gap = resources.getDimensionPixelSize(
            if (compact) R.dimen.pairing_compact_content_gap else R.dimen.pairing_content_gap
        )
        val qrSize = resources.getDimensionPixelSize(
            if (compact) R.dimen.pairing_compact_qr_size else R.dimen.pairing_qr_size
        )
        binding.pairingContentCard.apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
            (layoutParams as ViewGroup.MarginLayoutParams).setMargins(padding, padding, padding, padding)
        }
        binding.pairingQrCode.layoutParams =
            (binding.pairingQrCode.layoutParams as LinearLayout.LayoutParams).apply {
                width = qrSize
                height = qrSize
                marginEnd = 0
            }
        binding.pairingDetails.layoutParams =
            (binding.pairingDetails.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = if (compact) 0f else 1f
                marginStart = if (compact) 0 else gap
                topMargin = if (compact) gap else 0
            }
    }

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

    private fun openHotspotSettings() {
        // 部分 SDK 没有暴露 ACTION_TETHER_SETTINGS 常量，使用系统公开 action 字符串兼容调用。
        val intent = Intent("android.settings.TETHER_SETTINGS")
        runCatching { startActivity(intent) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    .onFailure {
                        runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                    }
            }
    }

    private fun toggleLocalHotspot() {
        if (LocalHotspotController.isActive()) {
            LocalHotspotController.stop()
            hotspotInfo = null
            updateHotspotUi()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openHotspotSettings()
            return
        }
        val missingPermissions = hotspotPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            hotspotPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startLocalHotspot()
        }
    }

    private fun startLocalHotspot() {
        LocalHotspotController.start(
            this,
            onStarted = { value ->
                hotspotInfo = value
                updateHotspotUi()
                refreshPairingPanel()
            },
            onFailed = {
                openHotspotSettings()
            }
        )
    }

    private fun hotspotPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            listOf("android.permission.NEARBY_WIFI_DEVICES")
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
            listOf("android.permission.ACCESS_FINE_LOCATION")
        else -> emptyList()
    }

    private fun updateHotspotUi() {
        binding.openHotspotSettingsButton.setText(
            if (LocalHotspotController.isActive()) R.string.stop_local_hotspot
            else R.string.start_local_hotspot
        )
        val current = hotspotInfo
        binding.pairingHotspotInfo.visibility = if (current == null) View.GONE else View.VISIBLE
        if (current != null) {
            binding.pairingHotspotInfo.text = getString(
                R.string.hotspot_credentials,
                current.ssid,
                current.passphrase
            )
        }
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
        updateHotspotUi()
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
