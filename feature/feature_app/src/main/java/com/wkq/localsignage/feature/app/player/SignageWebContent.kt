package com.wkq.localsignage.feature.app.player

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import java.io.FileInputStream
import java.net.URLConnection

/** Web 内容访问与离线包解析；播放调度和 View 生命周期仍由播放器负责。 */
internal object SignageWebContent {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webChromeClient = null
    }

    fun intercept(uri: Uri): WebResourceResponse? {
        if (uri.scheme != "https" || uri.host != "local.signage") return null
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != "site") return blocked()
        val resource = SignageRuntime.resource(segments[1])?.takeIf { it.isLocalWebPackage } ?: return blocked()
        val relative = segments.drop(2).joinToString("/").ifBlank { "index.html" }
        val file = runCatching { SignageRuntime.webPackageFile(resource, relative) }.getOrNull()
            ?.takeIf { it.isFile } ?: return blocked()
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        return try {
            WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript")) "UTF-8" else null, FileInputStream(file))
        } catch (error: java.io.IOException) {
            android.util.Log.w("SignagePlayback", "WEB_RESOURCE_OPEN_FAILED", error)
            blocked()
        }
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream())
}
