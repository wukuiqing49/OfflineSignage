package com.wkq.localsignage.feature.app.player

import android.annotation.SuppressLint
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.wkq.localsignage.feature.app.model.PlaybackListener
import com.wkq.localsignage.feature.app.model.SignageOverlay
import com.wkq.localsignage.feature.app.model.SignagePlaylistItem
import com.wkq.localsignage.feature.app.model.PlaybackStartupPolicy
import com.wkq.localsignage.feature.app.model.PlaybackTimingPolicy
import com.wkq.localsignage.feature.app.model.SignageResource
import com.wkq.localsignage.feature.app.model.SignageScene
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

object SignagePlaybackController {
    private enum class ContentMode { NONE, IMAGE, VIDEO, STREAM, WEB, TEXT }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var applicationContext: android.content.Context? = null
    private var listener: PlaybackListener? = null
    private var views: SignagePlaybackViews? = null
    private var slideshowRenderer: ImageSlideshowRenderer? = null
    private var activeScenes: List<SignageScene> = emptyList()
    private var activeItems: List<SignagePlaylistItem> = emptyList()
    private var activeIndex = 0
    private var activePlaylistId: String? = null
    private var activeLoop = true
    private var contentMode = ContentMode.NONE
    private var desiredPlaying = false
    private var scenePositionMs = 0L
    private var sceneStartedAt = 0L
    private var sceneDurationMs: Long? = null
    private var fallbackActive = false
    private var pendingRetry: Runnable? = null
    private val retryAttempts = mutableMapOf<String, Int>()
    private val failedSceneIds = mutableSetOf<String>()
    private val tickerAnimators = mutableListOf<ObjectAnimator>()
    private val htmlOverlayViews = mutableListOf<WebView>()
    private var textAnimator: ObjectAnimator? = null
    private var blurBitmap: Bitmap? = null

    private val sceneTimeout = Runnable { if (desiredPlaying) advance(1, fromFailure = false) }
    private val webLoadTimeout = Runnable { handleSceneFailure("WEB_TIMEOUT") }
    private val webRefresh = Runnable { views?.webView?.reload(); scheduleWebRefresh() }
    private val positionSaver = object : Runnable {
        override fun run() {
            if (desiredPlaying) SignageRuntime.setPosition(currentPositionMs())
            mainHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }
    private val supervisor = object : Runnable {
        override fun run() {
            val current = player
            if (contentMode in setOf(ContentMode.VIDEO, ContentMode.STREAM) && desiredPlaying && current != null &&
                current.mediaItemCount > 0 && current.playbackState == Player.STATE_IDLE &&
                currentScene()?.id !in retryAttempts
            ) {
                runCatching { current.prepare(); current.playWhenReady = true }
                    .onFailure { handleSceneFailure("SUPERVISOR_FAILED") }
            }
            mainHandler.postDelayed(this, SUPERVISOR_INTERVAL_MS)
        }
    }

    @Synchronized
    fun initialize(context: android.content.Context) {
        if (player != null) return
        applicationContext = context.applicationContext
        SignageRuntime.registerContentListener { refreshContent() }
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        currentScene()?.id?.let { retryAttempts.remove(it); failedSceneIds.remove(it) }
                    } else if (playbackState == Player.STATE_ENDED) {
                        when (contentMode) {
                            ContentMode.VIDEO -> advance(1, fromFailure = false)
                            ContentMode.STREAM -> handleSceneFailure("STREAM_ENDED")
                            else -> Unit
                        }
                    }
                    publish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    handleSceneFailure(error.errorCodeName)
                    publish()
                }
            })
        }
        desiredPlaying = PlaybackStartupPolicy.shouldResume(
            persistedPlaying = SignageRuntime.state().playing,
            autoResume = SignageRuntime.settings().autoResume
        )
        mainHandler.post(positionSaver)
        mainHandler.postDelayed(supervisor, SUPERVISOR_INTERVAL_MS)
        runOnMainAndWait { loadPlaylist(restorePosition = true); true }
    }

    fun attach(playbackViews: SignagePlaybackViews, listener: PlaybackListener) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Playback views must be attached on the main thread" }
        this.listener = listener
        views = playbackViews
        slideshowRenderer = ImageSlideshowRenderer(playbackViews.imageSlideshow)
        configureWebView(playbackViews.webView)
        playbackViews.playerView.apply {
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
            player = requirePlayer()
        }
        scenePositionMs = currentPositionMs()
        renderCurrent(restorePosition = true)
        publish()
    }

    fun detach(playbackViews: SignagePlaybackViews) {
        if (playbackViews.playerView.player === player) playbackViews.playerView.player = null
        releaseWebView(playbackViews.webView, destroy = false)
        clearOverlays(playbackViews.overlayContainer)
        textAnimator?.cancel()
        textAnimator = null
        slideshowRenderer?.release()
        playbackViews.blurBackgroundView.setImageDrawable(null)
        releaseBlurBitmap()
        if (views?.playerView === playbackViews.playerView) views = null
        slideshowRenderer = null
        listener = null
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        views?.let {
            it.playerView.player = null
            releaseWebView(it.webView, destroy = true)
            clearOverlays(it.overlayContainer)
        }
        textAnimator?.cancel()
        textAnimator = null
        slideshowRenderer?.release()
        player?.release()
        releaseBlurBitmap()
        SignageRuntime.unregisterContentListener()
        player = null
        applicationContext = null
        views = null
        listener = null
        activeScenes = emptyList()
        activeItems = emptyList()
        retryAttempts.clear()
        failedSceneIds.clear()
        contentMode = ContentMode.NONE
    }

    fun togglePause() = applyCommand("TOGGLE")

    fun refreshContent() {
        if (player == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshContent() }
            return
        }
        loadPlaylist(restorePosition = true, forceReload = true)
        publish()
    }

    fun applyCommand(
        action: String,
        resourceId: String? = null,
        sceneId: String? = null,
        playlistId: String? = null,
        value: Int? = null,
        revision: Long? = null
    ): Boolean {
        val normalized = action.uppercase()
        if (normalized !in SUPPORTED_ACTIONS) return false
        return runOnMainAndWait {
            val valid = SignageRuntime.canAcceptCommandRevision(revision) &&
                (resourceId == null || SignageRuntime.resource(resourceId) != null) &&
                (sceneId == null || SignageRuntime.scene(sceneId) != null) &&
                (playlistId == null || SignageRuntime.playlist(playlistId) != null)
            if (valid) SignageRuntime.setError(null)
            val executed = valid && when (normalized) {
                "PLAY", "RESUME" -> {
                    when {
                        resourceId != null -> SignageRuntime.scenes().firstOrNull { it.resourceId == resourceId }
                            ?.let { loadStandaloneScene(it) }
                        sceneId != null -> SignageRuntime.scene(sceneId)?.let { loadStandaloneScene(it) }
                        playlistId != null -> {
                            SignageRuntime.selectPlaylist(playlistId)
                            loadPlaylist(restorePosition = false, forceReload = true, followCurrentScene = false)
                        }
                        activeScenes.isEmpty() -> loadPlaylist(restorePosition = true)
                    }
                    resumePlayback()
                    true
                }
                "PAUSE" -> { pausePlayback(); true }
                "STOP" -> { stopPlayback(); true }
                "TOGGLE" -> { if (desiredPlaying) pausePlayback() else resumePlayback(); true }
                "NEXT" -> { advance(1, fromFailure = false); true }
                "PREVIOUS" -> { advance(-1, fromFailure = false); true }
                "VOLUME" -> if (value == null) false else {
                    SignageRuntime.command("VOLUME", value = value); applyAudioState(); true
                }
                "MUTE" -> { SignageRuntime.command("MUTE"); applyAudioState(); true }
                "UNMUTE" -> { SignageRuntime.command("UNMUTE"); applyAudioState(); true }
                "PLAY_SCENE" -> sceneId?.let(SignageRuntime::scene)?.let {
                    loadTargetScene(it); resumePlayback(); true
                } ?: false
                "PLAY_PLAYLIST" -> playlistId?.let {
                    SignageRuntime.selectPlaylist(it)
                    loadPlaylist(restorePosition = false, forceReload = true, followCurrentScene = false)
                    resumePlayback()
                    true
                } ?: false
                else -> false
            }
            if (!executed || !SignageRuntime.commitCommandRevision(revision)) false else {
                SignageRuntime.notifyState()
                publish()
                true
            }
        }
    }

    private fun loadPlaylist(
        restorePosition: Boolean,
        forceReload: Boolean = false,
        followCurrentScene: Boolean = true
    ) {
        val persistedState = SignageRuntime.state()
        if (persistedState.currentPlaylistId == null && persistedState.currentSceneId != null) {
            val standalone = SignageRuntime.scene(persistedState.currentSceneId)
            if (standalone != null) {
                loadStandaloneScene(standalone, restorePosition)
                return
            }
        }
        val playlist = SignageRuntime.playlist(persistedState.currentPlaylistId)
            ?: SignageRuntime.playlists().firstOrNull()
            ?: return clearPlayback("NO_PLAYLIST")
        val enabled = playlist.items.mapNotNull { item ->
            if (!item.enabled) null else SignageRuntime.scene(item.sceneId)?.let { it to item }
        }
        if (enabled.isEmpty()) return clearPlayback("NO_PLAYABLE_SCENE")
        val currentId = SignageRuntime.state().currentSceneId
        val currentOutside = followCurrentScene && currentId != null && enabled.none { it.first.id == currentId }
        if (currentOutside) {
            SignageRuntime.scene(currentId)?.let { loadTargetScene(it) }
            return
        }
        val ids = enabled.map { it.first.id }
        if (!forceReload && activePlaylistId == playlist.id && activeScenes.map { it.id } == ids) return
        activeScenes = enabled.map { it.first }
        activeItems = enabled.map { it.second }
        activePlaylistId = playlist.id
        activeLoop = playlist.loop
        activeIndex = ids.indexOf(currentId).coerceAtLeast(0)
        fallbackActive = false
        retryAttempts.clear()
        failedSceneIds.clear()
        scenePositionMs = if (restorePosition) SignageRuntime.state().positionMs else 0L
        renderCurrent(restorePosition)
    }

    private fun loadTargetScene(scene: SignageScene) {
        val containing = SignageRuntime.playlists().firstOrNull { playlist ->
            playlist.items.any { it.enabled && it.sceneId == scene.id }
        }
        if (containing != null) {
            SignageRuntime.selectPlaylist(containing.id)
            val enabled = containing.items.mapNotNull { item ->
                if (!item.enabled) null else SignageRuntime.scene(item.sceneId)?.let { it to item }
            }
            activeScenes = enabled.map { it.first }
            activeItems = enabled.map { it.second }
            activePlaylistId = containing.id
            activeLoop = containing.loop
            activeIndex = activeScenes.indexOfFirst { it.id == scene.id }.coerceAtLeast(0)
        } else {
            activeScenes = listOf(scene)
            activeItems = listOf(SignagePlaylistItem(scene.id))
            activePlaylistId = null
            activeLoop = false
            activeIndex = 0
        }
        scenePositionMs = 0L
        renderCurrent(restorePosition = false)
    }

    private fun loadStandaloneScene(scene: SignageScene, restorePosition: Boolean = false) {
        activeScenes = listOf(scene)
        activeItems = listOf(SignagePlaylistItem(scene.id))
        activePlaylistId = null
        activeLoop = true
        activeIndex = 0
        scenePositionMs = if (restorePosition) SignageRuntime.state().positionMs else 0L
        renderCurrent(restorePosition = restorePosition)
    }

    private fun renderCurrent(restorePosition: Boolean) {
        cancelSceneCallbacks()
        textAnimator?.cancel()
        textAnimator = null
        // Remove the previous scene's overlays before the new primary content is attached.
        views?.overlayContainer?.let(::clearOverlays)
        val scene = currentScene() ?: return clearPlayback("NO_PLAYABLE_SCENE")
        val resource = SignageRuntime.resource(scene.resourceId) ?: return handleSceneFailure("RESOURCE_MISSING")
        SignageRuntime.selectPlaybackScene(scene.id, activePlaylistId)
        scenePositionMs = if (restorePosition) scenePositionMs else 0L
        sceneStartedAt = SystemClock.elapsedRealtime()
        sceneDurationMs = currentItem()?.durationMs
        applyDisplayState(scene)
        applyAudioState()
        when {
            resource.isImage -> renderImages(scene, resource)
            resource.isVideo -> renderVideo(scene, resource)
            resource.isStream -> renderStream(scene, resource)
            resource.isWeb -> renderWeb(resource)
            resource.isText -> renderText(resource)
            else -> handleSceneFailure("UNSUPPORTED_RESOURCE_KIND")
        }
        renderOverlays(scene)
        if (desiredPlaying) scheduleSceneTimeout()
        SignageRuntime.setPlaying(desiredPlaying)
        publish()
    }

    private fun renderImages(scene: SignageScene, resource: SignageResource) {
        contentMode = ContentMode.IMAGE
        requirePlayer().stop()
        requirePlayer().clearMediaItems()
        showMode(ContentMode.IMAGE)
        val allImages = activeScenes.mapNotNull { candidate ->
            SignageRuntime.resource(candidate.resourceId)?.takeIf { it.isImage }?.let { ImageSlide(candidate, it) }
        }
        val isPureImagePlaylist = allImages.size == activeScenes.size
        val slides = if (isPureImagePlaylist) allImages else listOf(ImageSlide(scene, resource))
        val pageIndex = if (isPureImagePlaylist) activeIndex else 0
        slideshowRenderer?.show(slides, pageIndex)
    }

    private fun renderVideo(scene: SignageScene, resource: SignageResource) {
        contentMode = ContentMode.VIDEO
        renderMedia(scene, resource)
    }

    private fun renderStream(scene: SignageScene, resource: SignageResource) {
        contentMode = ContentMode.STREAM
        renderMedia(scene, resource)
    }

    private fun renderMedia(scene: SignageScene, resource: SignageResource) {
        showMode(contentMode)
        val uri = if (resource.isLocalFile) {
            val file = runCatching { SignageRuntime.fileFor(resource) }.getOrNull()
            if (file?.isFile != true) return handleSceneFailure("LOCAL_FILE_MISSING")
            Uri.fromFile(file)
        } else {
            Uri.parse(resource.sourceUri ?: return handleSceneFailure("STREAM_URI_MISSING"))
        }
        val item = MediaItem.Builder().setMediaId(scene.id).setUri(uri).apply {
            val source = resource.sourceUri.orEmpty().lowercase()
            when {
                source.substringBefore('?').endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                source.substringBefore('?').endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
                source.startsWith("rtsp://") -> setMimeType(MimeTypes.APPLICATION_RTSP)
            }
        }.build()
        requirePlayer().apply {
            setMediaItem(item, scenePositionMs)
            setPlaybackSpeed(
                if (contentMode == ContentMode.VIDEO) {
                    PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(scene.playbackSpeed)
                } else {
                    PlaybackTimingPolicy.DEFAULT_VIDEO_PLAYBACK_SPEED
                }
            )
            prepare()
            playWhenReady = desiredPlaying
        }
    }

    private fun renderWeb(resource: SignageResource) {
        contentMode = ContentMode.WEB
        requirePlayer().stop(); requirePlayer().clearMediaItems()
        showMode(ContentMode.WEB)
        val webView = views?.webView ?: return
        mainHandler.postDelayed(webLoadTimeout, WEB_LOAD_TIMEOUT_MS)
        val html = resource.content
        if (!html.isNullOrBlank()) {
            webView.loadDataWithBaseURL(LOCAL_HTML_BASE_URL, html, "text/html", "UTF-8", null)
        } else {
            webView.loadUrl(resource.sourceUri ?: return handleSceneFailure("WEB_URI_MISSING"))
        }
        scheduleWebRefresh()
        if (desiredPlaying) webView.onResume() else webView.onPause()
    }

    private fun renderText(resource: SignageResource) {
        contentMode = ContentMode.TEXT
        if (sceneDurationMs == null && resource.textRepeatCount != PlaybackTimingPolicy.INFINITE_TEXT_REPEAT_COUNT) {
            sceneDurationMs = estimateTextDurationMs(resource)
        }
        requirePlayer().stop(); requirePlayer().clearMediaItems()
        showMode(ContentMode.TEXT)
        val background = parseColor(resource.textBackgroundColor, Color.BLACK)
        (views?.textView?.parent as? View)?.setBackgroundColor(background)
        views?.textView?.apply {
            text = resource.content.orEmpty()
            textSize = resource.textSizeSp.toFloat()
            setTextColor(parseColor(resource.textColor, Color.WHITE))
            setBackgroundColor(Color.TRANSPARENT)
            typeface = DisplayTypefaceResolver.resolve(context, resource.fontFamily, resource.content)
            post { startTextTicker(this, resource) }
        }
    }

    private fun startTextTicker(text: TextView, resource: SignageResource, attempt: Int = 0) {
        textAnimator?.cancel()
        textAnimator = null
        val parentWidth = (text.parent as? View)?.width ?: return
        if (parentWidth <= 0 || text.width <= 0) {
            text.translationX = 0f
            if (attempt < TEXT_LAYOUT_RETRY_COUNT) {
                text.postDelayed({
                    if (views?.textView === text && currentScene()?.resourceId == resource.id) {
                        startTextTicker(text, resource, attempt + 1)
                    }
                }, TEXT_LAYOUT_RETRY_DELAY_MS)
            }
            return
        }
        val startOffset = (parentWidth - text.paddingLeft).coerceAtLeast(0).toFloat()
        val distance = startOffset + text.width.toFloat()
        val speed = PlaybackTimingPolicy.normalizeTextSpeed(resource.textSpeedDpPerSecond) *
            text.resources.displayMetrics.density
        val configuredRepeatCount = PlaybackTimingPolicy.normalizeTextRepeatCount(resource.textRepeatCount)
        val animator = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, startOffset, -text.width.toFloat()).apply {
            duration = (distance / speed * 1_000L).toLong().coerceAtLeast(1_000L)
            this.repeatCount = if (configuredRepeatCount == PlaybackTimingPolicy.INFINITE_TEXT_REPEAT_COUNT) {
                ObjectAnimator.INFINITE
            } else {
                configuredRepeatCount - 1
            }
            repeatMode = ObjectAnimator.RESTART
            start()
            if (!desiredPlaying) pause()
        }
        textAnimator = animator
    }

    private fun estimateTextDurationMs(resource: SignageResource): Long {
        val context = applicationContext
        val metrics = context?.resources?.displayMetrics
        val density = metrics?.density ?: 1f
        val scaledDensity = density * (context?.resources?.configuration?.fontScale ?: 1f)
        val screenWidth = (metrics?.widthPixels ?: DEFAULT_TEXT_VIEWPORT_WIDTH_PX).coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = resource.textSizeSp * scaledDensity
            typeface = context?.let { DisplayTypefaceResolver.resolve(it, resource.fontFamily, resource.content) }
        }
        val textWidth = paint.measureText(resource.content.orEmpty()).coerceAtLeast(screenWidth.toFloat())
        return PlaybackTimingPolicy.textTickerDurationMs(
            viewportWidthPx = screenWidth,
            textWidthPx = textWidth,
            density = density,
            speedDpPerSecond = resource.textSpeedDpPerSecond,
            repeatCount = resource.textRepeatCount
        )
    }

    private fun resumePlayback() {
        if (activeScenes.isEmpty()) loadPlaylist(restorePosition = true)
        desiredPlaying = true
        sceneStartedAt = SystemClock.elapsedRealtime()
        when (contentMode) {
            ContentMode.VIDEO, ContentMode.STREAM -> requirePlayer().play()
            ContentMode.WEB -> views?.webView?.onResume()
            else -> Unit
        }
        textAnimator?.let { if (it.isPaused) it.resume() }
        tickerAnimators.forEach { if (it.isPaused) it.resume() }
        scheduleSceneTimeout()
        SignageRuntime.setPlaying(true)
    }

    private fun pausePlayback() {
        scenePositionMs = currentPositionMs()
        desiredPlaying = false
        cancelSceneTimeout()
        mainHandler.removeCallbacks(webRefresh)
        requirePlayer().pause()
        views?.webView?.onPause()
        tickerAnimators.forEach { if (it.isStarted && !it.isPaused) it.pause() }
        textAnimator?.let { if (it.isStarted && !it.isPaused) it.pause() }
        SignageRuntime.setPosition(scenePositionMs)
        SignageRuntime.setPlaying(false)
    }

    private fun stopPlayback() {
        pausePlayback()
        scenePositionMs = 0L
        requirePlayer().stop()
        SignageRuntime.setPosition(0L)
    }

    private fun advance(offset: Int, fromFailure: Boolean) {
        cancelSceneCallbacks()
        if (activeScenes.isEmpty()) return clearPlayback("NO_PLAYABLE_SCENE")
        val next = activeIndex + offset
        activeIndex = when {
            next in activeScenes.indices -> next
            activeLoop -> (next % activeScenes.size + activeScenes.size) % activeScenes.size
            else -> {
                desiredPlaying = false
                SignageRuntime.setPlaying(false)
                SignageRuntime.setPosition(0L)
                return
            }
        }
        scenePositionMs = 0L
        if (!fromFailure) fallbackActive = false
        renderCurrent(restorePosition = false)
    }

    private fun handleSceneFailure(errorCode: String) {
        val scene = currentScene() ?: return clearPlayback(errorCode)
        val attempts = retryAttempts[scene.id] ?: 0
        val resource = SignageRuntime.resource(scene.resourceId)
        val retryable = resource?.isStream == true || resource?.isWeb == true
        if (retryable && attempts < RETRY_BACKOFF_MS.size) {
            val nextAttempt = attempts + 1
            retryAttempts[scene.id] = nextAttempt
            SignageRuntime.recordPlaybackError(resource.id, scene.id, errorCode, "RETRY", nextAttempt)
            SignageRuntime.setError("RETRYING_$errorCode")
            cancelSceneCallbacks()
            pendingRetry = Runnable {
                if (currentScene()?.id == scene.id) renderCurrent(restorePosition = false)
            }.also { mainHandler.postDelayed(it, RETRY_BACKOFF_MS[attempts]) }
            return
        }
        failedSceneIds += scene.id
        retryAttempts.remove(scene.id)
        if (activateFallback(errorCode, resource?.id, scene.id)) return
        if (hasNextAvailable()) {
            SignageRuntime.recordPlaybackError(resource?.id, scene.id, errorCode, "SKIP", attempts + 1)
            SignageRuntime.setError("SKIPPED_$errorCode")
            advance(1, fromFailure = true)
        } else {
            SignageRuntime.recordPlaybackError(resource?.id, scene.id, errorCode, "STOP", attempts + 1)
            clearPlayback(errorCode)
        }
    }

    private fun activateFallback(errorCode: String, resourceId: String?, sceneId: String): Boolean {
        if (fallbackActive) return false
        val fallback = SignageRuntime.settings().fallbackSceneId?.let(SignageRuntime::scene) ?: return false
        if (fallback.id == sceneId || SignageRuntime.resource(fallback.resourceId) == null) return false
        fallbackActive = true
        SignageRuntime.recordPlaybackError(resourceId, sceneId, errorCode, "FALLBACK", 1)
        activeScenes = listOf(fallback)
        activeItems = listOf(SignagePlaylistItem(fallback.id))
        activeIndex = 0
        activePlaylistId = null
        activeLoop = true
        scenePositionMs = 0L
        renderCurrent(restorePosition = false)
        SignageRuntime.setError("FALLBACK_$errorCode")
        return true
    }

    private fun hasNextAvailable(): Boolean {
        if (activeScenes.size < 2) return false
        val max = if (activeLoop) activeScenes.size - 1 else activeScenes.lastIndex - activeIndex
        return (1..max).any { offset -> activeScenes[(activeIndex + offset) % activeScenes.size].id !in failedSceneIds }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            databaseEnabled = false
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webChromeClient = null
        webView.webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !isAllowedWebUri(Uri.parse(url))

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !isAllowedWebUri(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                mainHandler.removeCallbacks(webLoadTimeout)
                retryAttempts.remove(currentScene()?.id)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) handleSceneFailure("WEB_${error.errorCode}")
            }
        }
    }

    private fun releaseWebView(webView: WebView, destroy: Boolean) {
        mainHandler.removeCallbacks(webLoadTimeout)
        mainHandler.removeCallbacks(webRefresh)
        webView.onPause()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        if (destroy) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun scheduleWebRefresh() {
        mainHandler.removeCallbacks(webRefresh)
        val interval = currentScene()?.let { SignageRuntime.resource(it.resourceId) }?.refreshIntervalMs ?: return
        if (desiredPlaying) mainHandler.postDelayed(webRefresh, interval)
    }

    private fun renderOverlays(scene: SignageScene) {
        val container = views?.overlayContainer ?: return
        if (container.width <= 0 || container.height <= 0) {
            container.post {
                if (views?.overlayContainer === container && currentScene()?.id == scene.id) renderOverlays(scene)
            }
            return
        }
        clearOverlays(container)
        scene.overlays.filter { it.enabled }.sortedBy { it.zIndex }.forEach { overlay ->
            if (overlay.type.equals("HTML", true)) {
                val webView = createHtmlOverlay(container, overlay)
                htmlOverlayViews += webView
                container.addView(webView, overlayLayoutParams(container, overlay))
                return@forEach
            }
            val text = TextView(container.context).apply {
                this.text = overlay.content
                textSize = overlay.textSizeSp.coerceIn(8, 160).toFloat()
                setTextColor(parseColor(overlay.textColor, Color.WHITE))
                typeface = DisplayTypefaceResolver.resolve(context, overlay.fontFamily, overlay.content)
                background = GradientDrawable().apply {
                    setColor(parseColor(overlay.backgroundColor, Color.TRANSPARENT))
                    cornerRadius = dp(container, overlay.cornerRadiusDp.coerceIn(0, 64)).toFloat()
                }
                setPadding(dp(container, overlay.paddingDp.coerceIn(0, 64)))
                maxLines = if (overlay.type.equals("TICKER", true)) 1 else Int.MAX_VALUE
            }
            container.addView(text, overlayLayoutParams(container, overlay))
            if (overlay.type.equals("TICKER", true)) startTicker(container, text, overlay)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHtmlOverlay(container: FrameLayout, overlay: SignageOverlay): WebView = WebView(container.context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            databaseEnabled = false
            setSupportZoom(false)
        }
        webChromeClient = null
        webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        loadDataWithBaseURL(LOCAL_HTML_BASE_URL, htmlOverlayDocument(overlay), "text/html", "UTF-8", null)
    }

    private fun htmlOverlayDocument(overlay: SignageOverlay): String = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
          html,body{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:transparent}
          body{color:#fff;font-family:system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif;padding:${dpCss(overlay.paddingDp)};border-radius:${dpCss(overlay.cornerRadiusDp)};background:${overlay.backgroundColor}}
          *{box-sizing:border-box}
        </style></head><body>${overlay.content}</body></html>
    """.trimIndent()

    private fun dpCss(value: Int): String = "${value.coerceIn(0, 96)}px"

    private fun startTicker(container: FrameLayout, text: TextView, overlay: SignageOverlay) {
        text.post {
            if (text.parent !== container) return@post
            val distance = container.width + text.width.toFloat()
            val speed = max(1, overlay.speedDpPerSecond) * container.resources.displayMetrics.density
            ObjectAnimator.ofFloat(text, View.TRANSLATION_X, container.width.toFloat(), -text.width.toFloat()).apply {
                duration = (distance / speed * 1_000L).toLong().coerceAtLeast(1_000L)
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                tickerAnimators += this
                start()
                if (!desiredPlaying) pause()
            }
        }
    }

    private fun clearOverlays(container: FrameLayout) {
        tickerAnimators.forEach { it.cancel() }
        tickerAnimators.clear()
        htmlOverlayViews.forEach { webView ->
            webView.onPause()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            webView.removeAllViews()
            webView.destroy()
        }
        htmlOverlayViews.clear()
        container.removeAllViews()
    }

    private fun overlayLayoutParams(container: FrameLayout, overlay: SignageOverlay): FrameLayout.LayoutParams {
        val horizontal = when (overlay.horizontalPosition.uppercase()) {
            "LEFT", "START" -> Gravity.START
            "RIGHT", "END" -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        val vertical = when (overlay.verticalPosition.uppercase()) {
            "TOP" -> Gravity.TOP
            "CENTER" -> Gravity.CENTER_VERTICAL
            else -> Gravity.BOTTOM
        }
        if (overlay.type.equals("HTML", true)) {
            return FrameLayout.LayoutParams(
                (container.width * overlay.widthPercent.coerceIn(10, 95) / 100).coerceAtLeast(dp(container, 120)),
                (container.height * overlay.heightPercent.coerceIn(8, 90) / 100).coerceAtLeast(dp(container, 72)),
                horizontal or vertical
            )
        }
        return FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, horizontal or vertical)
    }

    private fun applyDisplayState(scene: SignageScene) {
        val currentViews = views ?: return
        currentViews.playerView.resizeMode = when (scene.fitMode.uppercase()) {
            "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "FILL", "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val background = parseColor(scene.backgroundColor, Color.BLACK)
        (currentViews.textView.parent as? View)?.setBackgroundColor(background)
        currentViews.playerView.setBackgroundColor(background)
        currentViews.playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        currentViews.webView.setBackgroundColor(background)
        currentViews.textView.setBackgroundColor(Color.TRANSPARENT)
        currentViews.blurBackgroundView.apply {
            visibility = View.GONE
            setImageDrawable(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRenderEffect(null)
        }
        releaseBlurBitmap()
        val resource = SignageRuntime.resource(scene.resourceId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scene.backgroundType.equals("BLUR", true) &&
            resource?.isLocalFile == true && resource.isImage
        ) {
            runCatching { SignageRuntime.fileFor(resource) }.getOrNull()?.takeIf { it.isFile }?.let { file ->
                decodeSampledBitmap(file.absolutePath)?.let { bitmap ->
                    blurBitmap = bitmap
                    currentViews.blurBackgroundView.setImageBitmap(bitmap)
                    currentViews.blurBackgroundView.visibility = View.VISIBLE
                    currentViews.blurBackgroundView.setRenderEffect(
                        RenderEffect.createBlurEffect(BLUR_RADIUS_PX, BLUR_RADIUS_PX, Shader.TileMode.CLAMP)
                    )
                    currentViews.playerView.setBackgroundColor(Color.TRANSPARENT)
                    currentViews.playerView.setShutterBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    private fun applyAudioState() {
        val scene = currentScene()
        val volume = scene?.volume ?: SignageRuntime.state().volume
        requirePlayer().volume = if (scene?.muted == true || SignageRuntime.state().muted) 0f else volume.coerceIn(0, 100) / 100f
    }

    private fun showMode(mode: ContentMode) {
        views?.let {
            it.imageSlideshow.visibility = if (mode == ContentMode.IMAGE) View.VISIBLE else View.GONE
            it.playerView.visibility = if (mode == ContentMode.VIDEO || mode == ContentMode.STREAM) View.VISIBLE else View.GONE
            it.webView.visibility = if (mode == ContentMode.WEB) View.VISIBLE else View.GONE
            it.textView.visibility = if (mode == ContentMode.TEXT) View.VISIBLE else View.GONE
        }
    }

    private fun scheduleSceneTimeout() {
        cancelSceneTimeout()
        val duration = sceneDurationMs ?: when (contentMode) {
            ContentMode.IMAGE -> if (activeScenes.size > 1) DEFAULT_SCENE_DURATION_MS else null
            else -> null
        }
        if (duration != null && desiredPlaying) {
            mainHandler.postDelayed(sceneTimeout, (duration - scenePositionMs).coerceAtLeast(MIN_TIMEOUT_MS))
        }
        scheduleWebRefresh()
    }

    private fun cancelSceneTimeout() = mainHandler.removeCallbacks(sceneTimeout)

    private fun cancelSceneCallbacks() {
        cancelSceneTimeout()
        mainHandler.removeCallbacks(webLoadTimeout)
        mainHandler.removeCallbacks(webRefresh)
        pendingRetry?.let(mainHandler::removeCallbacks)
        pendingRetry = null
    }

    private fun currentPositionMs(): Long = when {
        contentMode == ContentMode.VIDEO || contentMode == ContentMode.STREAM -> requirePlayer().currentPosition.coerceAtLeast(0L)
        desiredPlaying -> scenePositionMs + (SystemClock.elapsedRealtime() - sceneStartedAt)
        else -> scenePositionMs
    }

    private fun currentScene(): SignageScene? = activeScenes.getOrNull(activeIndex)
    private fun currentItem(): SignagePlaylistItem? = activeItems.getOrNull(activeIndex)

    private fun clearPlayback(error: String) {
        cancelSceneCallbacks()
        requirePlayer().stop(); requirePlayer().clearMediaItems()
        activeScenes = emptyList(); activeItems = emptyList()
        activePlaylistId = null; contentMode = ContentMode.NONE; desiredPlaying = false
        showMode(ContentMode.NONE)
        views?.overlayContainer?.let(::clearOverlays)
        textAnimator?.cancel()
        textAnimator = null
        SignageRuntime.setPlaying(false)
        SignageRuntime.setPosition(0L)
        SignageRuntime.setError(error)
    }

    private fun publish() {
        listener?.onStateChanged(
            SignageRuntime.state().copy(playing = desiredPlaying, positionMs = currentPositionMs())
        )
    }

    private fun runOnMainAndWait(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrElse {
            SignageRuntime.setError("COMMAND_FAILED"); false
        }
        var result = false
        val latch = CountDownLatch(1)
        mainHandler.post {
            try { result = runCatching(block).getOrElse { SignageRuntime.setError("COMMAND_FAILED"); false } }
            finally { latch.countDown() }
        }
        return latch.await(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS) && result
    }

    private fun parseColor(value: String?, fallback: Int): Int =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback

    private fun isAllowedWebUri(uri: Uri): Boolean =
        uri.scheme.equals("https", true) || uri.toString().startsWith(LOCAL_HTML_BASE_URL)

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

    private fun decodeSampledBitmap(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_BLUR_BITMAP_EDGE ||
            bounds.outHeight / sampleSize > MAX_BLUR_BITMAP_EDGE
        ) sampleSize *= 2
        return runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        }.getOrNull()
    }

    private fun releaseBlurBitmap() {
        blurBitmap?.takeUnless { it.isRecycled }?.recycle()
        blurBitmap = null
    }

    private fun requirePlayer(): ExoPlayer = checkNotNull(player) { "SignagePlaybackController is not initialized" }

    private const val POSITION_SAVE_INTERVAL_MS = 1_000L
    private const val COMMAND_TIMEOUT_MS = 3_000L
    private const val DEFAULT_SCENE_DURATION_MS = 10_000L
    private const val MIN_TIMEOUT_MS = 100L
    private const val WEB_LOAD_TIMEOUT_MS = 20_000L
    private const val SUPERVISOR_INTERVAL_MS = 10_000L
    private const val LOCAL_HTML_BASE_URL = "https://local.signage.invalid/"
    private const val BLUR_RADIUS_PX = 24f
    private const val MAX_BLUR_BITMAP_EDGE = 1_280
    private const val DEFAULT_TEXT_VIEWPORT_WIDTH_PX = 1_920
    private const val TEXT_LAYOUT_RETRY_COUNT = 10
    private const val TEXT_LAYOUT_RETRY_DELAY_MS = 100L
    private val RETRY_BACKOFF_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 30_000L)
    private val SUPPORTED_ACTIONS = setOf(
        "PLAY", "RESUME", "PAUSE", "STOP", "TOGGLE", "NEXT", "PREVIOUS", "VOLUME", "MUTE", "UNMUTE",
        "PLAY_SCENE", "PLAY_PLAYLIST"
    )
}
