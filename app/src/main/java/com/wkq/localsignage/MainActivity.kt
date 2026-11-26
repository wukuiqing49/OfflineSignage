package com.wkq.localsignage

import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

class MainActivity : SignageActivity<ActivityMainBinding>(), PlaybackListener {
    private var appliedKeepScreenAwake: Boolean? = null
    private var appliedFullscreen: Boolean? = null
    private var pairingManuallyOpened = false
    private var currentControlAddress: String? = null
    private var hotspotPermissionPending = false
    private var hotspotPermissionDenied = false
    private val hotspotStateListener: () -> Unit = {
        if (!isFinishing && !isDestroyed) refreshPairingPanel()
    }
    private var entitlementState: EntitlementState = MonetizationRepository.uiState.value.entitlement
    private val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hotspotPermissionPending = false
        if (permissions.isNotEmpty() && hotspotPermissions().all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            hotspotPermissionDenied = false
            startLocalHotspot()
        } else {
            hotspotPermissionDenied = true
            updateHotspotUi()
        }
    }
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val touchSlopSquared by lazy {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        slop * slop
    }
    private var playbackAttached = false
    private val playbackViews: SignagePlaybackViews by lazy(LazyThreadSafetyMode.NONE) {
        SignagePlaybackViews(
            playerView = binding.playerView,
            imageSlideshow = binding.imageSlideshow,
            webView = binding.webView,
            textView = binding.textContentView,
            overlayContainer = binding.overlayContainer,
            blurBackgroundView = binding.blurBackgroundView,
            sidebarContainer = binding.sidebarContainer
        )
    }
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
    private val trialExpiryRefresh = Runnable {
        MonetizationRepository.refresh(loadCatalog = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pairingManuallyOpened = savedInstanceState?.getBoolean("pairing_open") ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pairing_open", pairingManuallyOpened)
        super.onSaveInstanceState(outState)
    }

    override fun initView() {
        ViewCompat.setAccessibilityHeading(binding.pairingConnectTitle, true)
        if (!SignageRuntime.tryInitialize(this)) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }
        SignagePlaybackController.initialize(this)
        installPlaybackInsets()
        applyDisplaySettings()
        SignagePlaybackController.attach(playbackViews, this)
        playbackAttached = true
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
        binding.openHotspotSettingsButton.setOnClickListener { showNetworkOptions() }
        binding.openBillingRow.setOnClickListener {
            startActivity(Intent(this, BillingActivity::class.java))
        }
        binding.enterpriseContactButton.setOnClickListener {
            startActivity(
                HelpArticleActivity.intent(
                    this,
                    HelpArticleActivity.ARTICLE_ENTERPRISE_DEPLOYMENT
                )
            )
        }
        binding.trialExpiredBadge.setOnClickListener {
            startActivity(Intent(this, BillingActivity::class.java))
        }
        binding.openHelpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.pairingAddress.setOnClickListener {
            val address = currentControlAddress
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
                configurePairingLayout(
                    width - binding.pairingPanel.paddingLeft - binding.pairingPanel.paddingRight,
                    height - binding.pairingPanel.paddingTop - binding.pairingPanel.paddingBottom
                )
            }
        }
        configureTvFocusTargets()
        LocalHotspotController.addListener(hotspotStateListener)
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

    /** 与用户提供的连接卡片保持一致；放大字体时允许自然换行和滚动。 */
    private fun configurePairingLayout(availableWidth: Int, availableHeight: Int) {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val fontScale = resources.configuration.fontScale.coerceAtLeast(1f)
        val stacked = availableWidth / fontScale < dp(560)
        val gap = dp(32)
        val qrSize = if (availableHeight / fontScale < dp(360)) dp(128) else dp(144)
        val qrColumnWidth = qrSize + if (!stacked && availableWidth / fontScale >= dp(720)) dp(64) else 0
        val contentWidth = min(dp(1040), availableWidth).coerceAtLeast(1)
        binding.pairingContentCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = contentWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setMargins(0, 0, 0, 0)
        }
        binding.pairingContentCard.minimumHeight = availableHeight
        binding.openBillingRow.updateLayoutParams<LinearLayout.LayoutParams> {
            width = min(dp(420), (contentWidth - dp(32)).coerceAtLeast(1))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        binding.pairingContent.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        binding.pairingQrSection.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else qrColumnWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        binding.pairingQrFrame.updateLayoutParams<LinearLayout.LayoutParams> {
            width = qrSize
            height = qrSize
        }
        binding.pairingDetails.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            weight = if (stacked) 0f else 1f
            marginStart = if (stacked) 0 else gap
            topMargin = if (stacked) dp(16) else 0
        }
        val compactCredentials = stacked || (contentWidth - qrColumnWidth - gap - dp(48)) / fontScale < dp(360)
        binding.pairingCredentials.orientation = if (compactCredentials) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        binding.pairingAddressGroup.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (compactCredentials) ViewGroup.LayoutParams.MATCH_PARENT else 0
            weight = if (compactCredentials) 0f else 1f
            marginEnd = if (compactCredentials) 0 else dp(16)
        }
        binding.pairingCodeGroup.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (compactCredentials) ViewGroup.LayoutParams.MATCH_PARENT else (dp(128) * fontScale).toInt()
            topMargin = if (compactCredentials) dp(8) else 0
        }
        val stackNetwork = stacked || fontScale >= 1.4f
        binding.pairingNetworkActions.orientation = if (stackNetwork) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        binding.pairingNetworkStatus.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (stackNetwork) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            weight = 0f
        }
        binding.openHotspotSettingsButton.updateLayoutParams<LinearLayout.LayoutParams> {
            marginStart = if (stackNetwork) 0 else dp(8)
        }
        binding.pairingPanel.post {
            if (!isDestroyed) ViewCompat.getRootWindowInsets(binding.root)?.let(::applyPairingCutoutPadding)
        }
    }

    override fun onStateChanged(state: SignageState) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!playbackAttached) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java))
        } catch (error: Exception) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                error !is android.app.ForegroundServiceStartNotAllowedException
            ) throw error
            android.util.Log.w("MainActivity", "FOREGROUND_START_REJECTED_WAIT_FOR_RESUME", error)
            SignageRuntime.setError("FOREGROUND_SERVICE_START_NOT_ALLOWED")
        }
        applyDisplaySettings()
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.post(pairingRefresh)
        MonetizationRepository.refresh(loadCatalog = false)
    }

    override fun onPause() {
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.removeCallbacks(hidePlaybackControls)
        pairingHandler.removeCallbacks(trialExpiryRefresh)
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
            binding.closePairingButton.icon = null
        } else {
            binding.closePairingButton.setText(FeatureAppR.string.exit_app)
            binding.closePairingButton.icon = null
        }
        // 设备中心本身就是广告屏上的全屏连接页，不应受播放页全屏开关限制。
        applyDisplaySettings()
        if (showPairing) {
            hidePlaybackControls.run()
            refreshPairingPanel()
        }
        requestTvFocus(showPairing)
        renderMonetization()
    }

    private fun configureTvFocusTargets() {
        val television = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        listOf(
            binding.openHelpButton,
            binding.closePairingButton,
            binding.openBillingRow,
            binding.openHotspotSettingsButton,
            binding.enterpriseContactButton,
            binding.showPairingButton,
            binding.pauseResumeButton,
            binding.trialExpiredBadge
        ).forEach { view ->
            view.isFocusable = true
            view.isFocusableInTouchMode = television
        }
        binding.pairingPanel.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }

    private fun requestTvFocus(showPairing: Boolean) {
        val isTelevision = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        if (!isTelevision) return
        val target = if (showPairing) binding.closePairingButton else binding.pauseResumeButton
        target.post {
            if (target.isShown && !target.hasFocus()) target.requestFocus()
        }
    }

    private fun showPlaybackControls() {
        if (binding.pairingPanel.isVisible || SignageRuntime.resources().isEmpty()) return
        binding.playbackStatusBar.isVisible = true
        requestTvFocus(showPairing = false)
        pairingHandler.removeCallbacks(hidePlaybackControls)
        pairingHandler.postDelayed(hidePlaybackControls, PLAYBACK_CONTROLS_TIMEOUT_MS)
    }

    private fun openPairingPanel() {
        pairingManuallyOpened = true
        // A manually opened control panel must never present a stale pairing credential.
        SignageRuntime.issuePairingToken()
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

    /** 网络入口保留在连接信息旁，打开系统页面必须由用户明确选择。 */
    private fun showNetworkOptions() {
        val action = if (LocalHotspotController.isActive()) FeatureAppR.string.stop_local_hotspot
            else FeatureAppR.string.start_local_hotspot
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            getString(action) to { toggleLocalHotspot() },
            getString(FeatureAppR.string.open_hotspot_settings) to { openHotspotSettings() }
        )
        if (LocalHotspotController.failure == LocalHotspotController.Failure.LOCATION) {
            actions += getString(FeatureAppR.string.hotspot_open_location) to {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        if (hotspotPermissionDenied || LocalHotspotController.failure == LocalHotspotController.Failure.PERMISSION) {
            actions += getString(FeatureAppR.string.hotspot_open_permissions) to {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)))
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(FeatureAppR.string.pairing_network_action)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleLocalHotspot() {
        if (LocalHotspotController.phase == LocalHotspotController.Phase.STARTING || hotspotPermissionPending) return
        hotspotPermissionDenied = false
        if (LocalHotspotController.isActive()) {
            LocalHotspotController.stop()
            return
        }
        val missingPermissions = hotspotPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            hotspotPermissionPending = true
            updateHotspotUi()
            hotspotPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else startLocalHotspot()
    }

    private fun startLocalHotspot() = LocalHotspotController.start(this)

    private fun hotspotPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf("android.permission.NEARBY_WIFI_DEVICES")
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> listOf("android.permission.ACCESS_FINE_LOCATION")
        else -> emptyList()
    }

    /** 启动、成功、失败与系统停止都在本页反映，不自动跳转设置。 */
    private fun updateHotspotUi() {
        val starting = hotspotPermissionPending || LocalHotspotController.phase == LocalHotspotController.Phase.STARTING
        binding.openHotspotSettingsButton.isEnabled = !starting
        binding.openHotspotSettingsButton.setText(
            if (starting) FeatureAppR.string.hotspot_starting else FeatureAppR.string.pairing_network_action
        )
        val current = LocalHotspotController.currentInfo()
        val failure = LocalHotspotController.failure
        val message = when {
            current != null -> getString(FeatureAppR.string.hotspot_credentials, current.ssid, current.passphrase)
            hotspotPermissionDenied || failure == LocalHotspotController.Failure.PERMISSION -> getString(FeatureAppR.string.hotspot_permission_denied)
            starting -> getString(FeatureAppR.string.hotspot_starting)
            failure != null -> getString(when (failure) {
                LocalHotspotController.Failure.UNSUPPORTED -> FeatureAppR.string.hotspot_unsupported
                LocalHotspotController.Failure.LOCATION -> FeatureAppR.string.hotspot_location_required
                LocalHotspotController.Failure.NO_CHANNEL -> FeatureAppR.string.hotspot_no_channel
                LocalHotspotController.Failure.INCOMPATIBLE -> FeatureAppR.string.hotspot_incompatible
                LocalHotspotController.Failure.DISALLOWED -> FeatureAppR.string.hotspot_disallowed
                else -> FeatureAppR.string.hotspot_start_failed
            })
            else -> null
        }
        binding.pairingHotspotInfo.isVisible = message != null
        binding.pairingHotspotInfo.text = message.orEmpty()
        binding.pairingHotspotInfo.setTextColor(ContextCompat.getColor(this,
            if (failure != null || hotspotPermissionDenied) FeatureAppR.color.entitlement_verification
            else FeatureAppR.color.pairing_text_secondary))
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
        applySignageOrientation()
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
        if (appliedFullscreen != shouldFullscreen) {
            appliedFullscreen = shouldFullscreen
            ViewCompat.requestApplyInsets(binding.root)
        }
    }

    private fun installPlaybackInsets() {
        val controls = binding.playbackStatusBar.layoutParams as android.widget.FrameLayout.LayoutParams
        val controlTop = controls.topMargin
        val controlEnd = controls.marginEnd
        val badge = binding.trialExpiredBadge.layoutParams as android.widget.FrameLayout.LayoutParams
        val badgeTop = badge.topMargin
        val badgeStart = badge.marginStart
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val fullscreen = appliedFullscreen == true
            val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
            // 播放画面可铺满屏幕；可操作控件必须避开系统栏和刘海。
            root.updatePadding(
                left = if (fullscreen) 0 else safe.left,
                top = if (fullscreen) 0 else safe.top,
                right = if (fullscreen) 0 else safe.right,
                bottom = if (fullscreen) 0 else safe.bottom
            )
            binding.pairingPanel.updatePadding(
                left = 0,
                top = if (fullscreen) safe.top else 0,
                right = 0,
                bottom = if (fullscreen) safe.bottom else 0
            )
            binding.playbackStatusBar.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                topMargin = controlTop + if (fullscreen) safe.top else 0
                marginEnd = controlEnd + if (fullscreen) (if (rtl) safe.left else safe.right) else 0
            }
            binding.trialExpiredBadge.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                topMargin = badgeTop + if (fullscreen) safe.top else 0
                marginStart = badgeStart + if (fullscreen) (if (rtl) safe.right else safe.left) else 0
            }
            if (binding.pairingPanel.width > 0 && binding.pairingPanel.height > 0) {
                configurePairingLayout(
                    binding.pairingPanel.width - binding.pairingPanel.paddingLeft - binding.pairingPanel.paddingRight,
                    binding.pairingPanel.height - binding.pairingPanel.paddingTop - binding.pairingPanel.paddingBottom
                )
            }
            insets
        }
        binding.root.doOnAttach(ViewCompat::requestApplyInsets)
    }

    /** 按行避让实际重叠的刘海，不把中部摄像头留白叠加到页头和底部操作栏。 */
    private fun applyPairingCutoutPadding(insets: WindowInsetsCompat) {
        val cutout = insets.displayCutout
        fun apply(row: View, base: Int) {
            val location = IntArray(2)
            row.getLocationOnScreen(location)
            val overlaps = appliedFullscreen == true && cutout?.boundingRects.orEmpty().any {
                it.bottom > location[1] && it.top < location[1] + row.height
            }
            row.updatePadding(
                left = base + if (overlaps) cutout?.safeInsetLeft ?: 0 else 0,
                right = base + if (overlaps) cutout?.safeInsetRight ?: 0 else 0
            )
        }
        apply(binding.pairingHeader, 0)
        apply(binding.pairingContent, (12 * resources.displayMetrics.density).toInt())
        apply(binding.pairingSecondaryActions, 0)
    }

    private fun refreshPairingPanel() {
        if (binding.pairingPanel.visibility != View.VISIBLE) return
        val code = SignageRuntime.pairingCode(resources.getInteger(FeatureAppR.integer.pairing_qr_pixels))
        binding.pairingDeviceName.text = SignageRuntime.state().deviceName
        currentControlAddress = code.controlAddress
        binding.pairingAddress.text = code.controlAddress?.removePrefix("http://")?.removePrefix("https://")
            ?: getString(FeatureAppR.string.pairing_waiting_for_network)
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
        binding.pairingHint.isVisible = true
        binding.pairingHint.setText(if (pairingAvailable) FeatureAppR.string.pairing_connection_summary
            else FeatureAppR.string.pairing_network_hint)
    }

    private fun String.formatPairingCode(): String =
        if (length == 6 && all(Char::isDigit)) chunked(3).joinToString(" ") else this

    private fun renderMonetization() {
        val pairingVisible = binding.pairingPanel.isVisible
        val trialExpired = entitlementState.type == EntitlementType.TRIAL_EXPIRED
        binding.trialExpiredBadge.isVisible =
            trialExpired && !pairingVisible
        val (compactStatus, accessibleStatus, _) = when (entitlementState.type) {
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
        val trialActive = entitlementState.type == EntitlementType.TRIAL_ACTIVE
        binding.entitlementUpgradeHint.isVisible = true
        binding.entitlementUpgradeHint.setText(
            if (trialExpired || trialActive) FeatureAppR.string.pairing_pro_action else FeatureAppR.string.device_license_label
        )
        binding.entitlementUpgradeHint.setTextColor(ContextCompat.getColor(this, FeatureAppR.color.pairing_brand_blue))
        binding.openBillingRow.contentDescription = getString(
            FeatureAppR.string.device_license_status_action,
            accessibleStatus
        )
        pairingHandler.removeCallbacks(trialExpiryRefresh)
        if (entitlementState.type == EntitlementType.TRIAL_ACTIVE) {
            val refreshDelay = max(
                1_000L,
                entitlementState.trialEndsAtEpochMillis - System.currentTimeMillis() + 1_000L
            )
            pairingHandler.postDelayed(trialExpiryRefresh, refreshDelay)
        }
    }

    override fun onDestroy() {
        LocalHotspotController.removeListener(hotspotStateListener)
        pairingHandler.removeCallbacks(pairingRefresh)
        pairingHandler.removeCallbacks(hidePlaybackControls)
        pairingHandler.removeCallbacks(trialExpiryRefresh)
        if (playbackAttached) SignagePlaybackController.detach(playbackViews)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private companion object {
        const val PAIRING_REFRESH_INTERVAL_MS = 5_000L
        const val PLAYBACK_CONTROLS_TIMEOUT_MS = 4_000L
    }
}
