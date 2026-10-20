package com.wkq.localsignage.feature.app.player

import android.view.ViewGroup
import android.webkit.WebView

/** 渲染进程退出后旧实例不能再加载，保留布局位置并交回一个全新实例。 */
internal fun replaceFailedWebView(views: SignagePlaybackViews, failed: WebView): Boolean {
    if (views.released || views.webView !== failed) return false
    val parent = failed.parent as? ViewGroup ?: return false
    val index = parent.indexOfChild(failed)
    val replacement = WebView(failed.context).apply {
        id = failed.id
        layoutParams = failed.layoutParams
        visibility = failed.visibility
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
    parent.removeView(failed)
    views.webView = replacement
    parent.addView(replacement, index)
    failed.destroy()
    return true
}
