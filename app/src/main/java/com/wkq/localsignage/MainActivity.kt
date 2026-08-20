package com.wkq.localsignage

import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivityMainBinding
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.R as FeatureAppR
import com.wkq.localsignage.feature.app.model.SignageState
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.player.SignagePlaybackViews
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.monetization.EntitlementState
import com.wkq.localsignage.monetization.EntitlementType
import com.wkq.localsignage.monetization.MonetizationRepository
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MainActivity : BaseActivity<ActivityMainBinding>(), PlaybackListener {
    private var appliedKeepScreenAwake: Boolean? = null
    private var appliedFullscreen: Boolean? = null
    private var pairingManuallyOpened = false
    private var hotspotInfo: LocalHotspotController.HotspotInfo? = null
    private var entitlementState: EntitlementState = MonetizationRepository.uiState.value.entitlement
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
        binding.playbackStatusBar.isVisible = false
    }
    private val pairingRefresh = object : Runnable {
        override fun run() {
            refreshPairingPanel()
            pairingHandler.postDelayed(this, PAIRING_REFRESH_INTERVAL_MS)
        }
    }

    override fun initView() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java))
        SignagePlaybackController.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyDisplaySettings()
        SignagePlaybackController.attach(playbackViews, this)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.pairingPanel.isVisible &&
                    SignageRuntime.resources().isNotEmpty() &&
                    pairingManuallyOpened
                ) {
                    closePairingPanel()
                } else {
                    finish()
                }
            }
        })
        binding.pauseResumeButton.setOnClickListener {
            SignagePlaybackController.togglePause()
            showPlaybackControls()
        }
        binding.showPairingButton.setOnClickListener { openPairingPanel() }
        binding.closePairingButton.setOnClickListener {
            if (SignageRuntime.resources().isNotEmpty() && pairingManuallyOpened) {
                closePairingPanel()
            } else {
                finish()
            }
        }
        binding.openHotspotSettingsButton.setOnClickListener { toggleLocalHotspot() }
        binding.openBillingRow.setOnClickListener {
            startActivity(Intent(this, BillingActivity::class.java))
        }
        binding.trialExpiredBadge.setOnClickListener {
            startActivity(Intent(this, BillingActivity::class.java))
        }
        binding.openHelpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.pairingAddress.setOnClickListener {
            val address = binding.pairingAddress.text?.toString()
            if (!address.isNullOrBlank() && address.startsWith("http")) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LocalSignage Address", address))
                android.widget.Toast.makeText(this, address, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.pairingPanel.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                         oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if (width != oldRight - oldLeft || height != oldBottom - oldTop) {
                configurePairingLayout(width, height)
            }
        }
        hotspotInfo = LocalHotspotController.currentInfo()
        updateHotspotUi()
        updateContentMode()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonetizationRepository.uiState.collect { state ->
                    entitlementState = state.entitlement
                    renderMonetization()
                }
            }
        }
    }

    override fun initData() = Unit

    private fun configurePairingLayout(availableWidth: Int, availableHeight: Int) {
        val compact = availableWidth < resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_compact_breakpoint) ||
            availableHeight < resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_compact_height_breakpoint)
        val padding = resources.getDimensionPixelSize(
            if (compact) FeatureAppR.dimen.pairing_compact_screen_padding else FeatureAppR.dimen.pairing_screen_padding
        )
        val gap = resources.getDimensionPixelSize(
            if (compact) FeatureAppR.dimen.pairing_compact_content_gap else FeatureAppR.dimen.pairing_content_gap
        )
        val qrSize = resources.getDimensionPixelSize(
            if (compact) FeatureAppR.dimen.pairing_compact_qr_size else FeatureAppR.dimen.pairing_qr_size
        )
        val maxContentWidth = resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_content_max_width)
        val contentWidth = min(maxContentWidth, availableWidth - padding * 2).coerceAtLeast(qrSize)
        val contentHeight = (availableHeight - padding * 2).coerceAtLeast(1)
        binding.pairingContentCard.layoutParams =
            (binding.pairingContentCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = contentWidth
                height = contentHeight
                setMargins(0, padding, 0, padding)
            }
        binding.pairingContent.setPadding(padding, padding, padding, padding)
        binding.pairingPanel.scrollTo(0, 0)
        binding.pairingQrSection.layoutParams =
            (binding.pairingQrSection.layoutParams as LinearLayout.LayoutParams).apply {
                width = qrSize
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                marginEnd = 0
            }
        binding.pairingQrFrame.layoutParams =
            (binding.pairingQrFrame.layoutParams as LinearLayout.LayoutParams).apply {
                width = qrSize
                height = qrSize
            }
        binding.pairingDetails.layoutParams =
            (binding.pairingDetails.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 1f
                marginStart = gap
                topMargin = 0
            }
    }

    override fun onStateChanged(state: SignageState) {
        runOnUiThread {
            binding.pauseResumeButton.setIconResource(
                if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            binding.pauseResumeButton.contentDescription = getString(if (state.playing) FeatureAppR.string.pause else FeatureAppR.string.resume)
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
        MonetizationRepository.refresh(loadCatalog = false)
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
        binding.playbackRoot.setBackgroundColor(
            if (showPairing) ContextCompat.getColor(this, FeatureAppR.color.pairing_background)
            else android.graphics.Color.BLACK
        )
        binding.closePairingButton.visibility = View.VISIBLE
        if (hasContent && pairingManuallyOpened) {
            binding.closePairingButton.setText(FeatureAppR.string.return_to_playback)
            binding.closePairingButton.setIconResource(FeatureAppR.drawable.ic_arrow_back_24)
        } else {
            binding.closePairingButton.setText(FeatureAppR.string.exit_app)
            binding.closePairingButton.setIconResource(FeatureAppR.drawable.ic_close_24)
        }
        // 设备中心本身就是广告屏上的全屏连接页，不应受播放页全屏开关限制。
        applyDisplaySettings()
        if (showPairing) {
            hidePlaybackControls.run()
            refreshPairingPanel()
        }
        renderMonetization()
    }

    private fun showPlaybackControls() {
        if (binding.pairingPanel.isVisible || SignageRuntime.resources().isEmpty()) return
        binding.playbackStatusBar.isVisible = true
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
            if (LocalHotspotController.isActive()) FeatureAppR.string.stop_local_hotspot
            else FeatureAppR.string.start_local_hotspot
        )
        val current = hotspotInfo
        binding.pairingHotspotInfo.visibility = if (current == null) View.GONE else View.VISIBLE
        if (current != null) {
            binding.pairingHotspotInfo.text = getString(
                FeatureAppR.string.hotspot_credentials,
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
        val shouldFullscreen = settings.fullscreen || binding.pairingPanel.isVisible
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (shouldFullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        appliedFullscreen = shouldFullscreen
    }

    private fun refreshPairingPanel() {
        if (binding.pairingPanel.visibility != View.VISIBLE) return
        val code = SignageRuntime.pairingCode(resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_qr_pixels))
        binding.pairingDeviceName.text = SignageRuntime.state().deviceName
        binding.pairingAddress.text = code.controlAddress ?: getString(FeatureAppR.string.pairing_waiting_for_network)
        binding.pairingQrCode.setImageBitmap(code.qrBitmap)
        val pairingAvailable = code.qrBitmap != null
        binding.pairingQrCode.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingQrUnavailable.visibility = if (pairingAvailable) View.GONE else View.VISIBLE
        binding.pairingQrCaption.visibility = if (pairingAvailable) View.VISIBLE else View.INVISIBLE
        binding.pairingAccessCodeLabel.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingAccessCode.visibility = if (pairingAvailable) View.VISIBLE else View.GONE
        binding.pairingAccessCode.text = code.accessCode.formatPairingCode()
        binding.pairingNetworkStatus.setText(
            if (pairingAvailable) FeatureAppR.string.pairing_network_connected
            else FeatureAppR.string.pairing_waiting_for_network
        )
        binding.pairingNetworkStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (pairingAvailable) FeatureAppR.color.pairing_accent
                else FeatureAppR.color.entitlement_verification
            )
        )
        updateHotspotUi()
        binding.pairingHint.text = getString(
            if (pairingAvailable) FeatureAppR.string.pairing_scan_hint else FeatureAppR.string.pairing_network_hint
        )
    }

    private fun String.formatPairingCode(): String =
        if (length == 6 && all(Char::isDigit)) chunked(3).joinToString(" ") else this

    private fun renderMonetization() {
        val pairingVisible = binding.pairingPanel.isVisible
        binding.trialExpiredBadge.isVisible =
            entitlementState.type == EntitlementType.TRIAL_EXPIRED && !pairingVisible
        val (compactStatus, accessibleStatus, statusColor) = when (entitlementState.type) {
            EntitlementType.TRIAL_ACTIVE -> {
                val remainingMillis = max(
                    0L,
                    entitlementState.trialEndsAtEpochMillis - System.currentTimeMillis()
                )
                val remainingDays = max(1L, TimeUnit.MILLISECONDS.toDays(remainingMillis) + 1L)
                Triple(
                    resources.getQuantityString(
                        FeatureAppR.plurals.entitlement_trial_compact,
                        remainingDays.toInt(),
                        remainingDays
                    ),
                    resources.getQuantityString(
                        FeatureAppR.plurals.entitlement_trial_status,
                        remainingDays.toInt(),
                        remainingDays
                    ),
                    FeatureAppR.color.entitlement_trial
                )
            }
            EntitlementType.TRIAL_EXPIRED -> Triple(
                getString(FeatureAppR.string.entitlement_trial_ended_compact),
                getString(FeatureAppR.string.entitlement_trial_ended),
                FeatureAppR.color.entitlement_expired
            )
            EntitlementType.SUBSCRIPTION -> Triple(
                getString(FeatureAppR.string.entitlement_subscription_compact),
                getString(FeatureAppR.string.entitlement_subscription),
                FeatureAppR.color.entitlement_active
            )
            EntitlementType.SUBSCRIPTION_GRACE -> Triple(
                getString(FeatureAppR.string.entitlement_subscription_grace_compact),
                getString(FeatureAppR.string.entitlement_subscription_grace),
                FeatureAppR.color.entitlement_verification
            )
            EntitlementType.LIFETIME -> Triple(
                getString(FeatureAppR.string.entitlement_lifetime_compact),
                getString(FeatureAppR.string.entitlement_lifetime),
                FeatureAppR.color.entitlement_active
            )
        }
        binding.entitlementStatus.text = compactStatus
        binding.entitlementIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, statusColor)
        )
        binding.openBillingRow.contentDescription = getString(
            FeatureAppR.string.device_license_status_action,
            accessibleStatus
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
