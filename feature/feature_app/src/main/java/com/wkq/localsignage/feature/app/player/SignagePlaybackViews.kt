package com.wkq.localsignage.feature.app.player

import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.viewpager2.widget.ViewPager2

data class SignagePlaybackViews(
    val playerView: PlayerView,
    val imageSlideshow: ViewPager2,
    val webView: WebView,
    val textView: TextView,
    val overlayContainer: FrameLayout,
    val blurBackgroundView: ImageView
)
