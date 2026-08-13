package com.wkq.localsignage.feature.app.model

import java.net.URI

object SecondPhasePolicy {
    private val streamSchemes = setOf("https", "rtsp")
    private val streamExtensions = setOf("m3u8", "mpd")

    fun validateVirtualResource(kind: ResourceKind, sourceUri: String?, content: String?) {
        when (kind) {
            ResourceKind.WEB -> {
                if (sourceUri != null) requireHttpsUri(sourceUri)
                else require(!content.isNullOrBlank()) { "WEB_CONTENT_REQUIRED" }
            }
            ResourceKind.STREAM -> {
                val uri = requireUri(sourceUri, "STREAM_URL_REQUIRED")
                require(uri.scheme?.lowercase() in streamSchemes) { "STREAM_PROTOCOL_NOT_ALLOWED" }
                require(uri.userInfo == null && !uri.host.isNullOrBlank()) { "STREAM_URL_NOT_ALLOWED" }
                if (uri.scheme.equals("https", true)) {
                    val extension = uri.path.substringAfterLast('.', "").lowercase()
                    require(extension in streamExtensions) { "STREAM_FORMAT_NOT_SUPPORTED" }
                }
            }
            ResourceKind.TEXT -> require(!content.isNullOrBlank()) { "TEXT_CONTENT_REQUIRED" }
            ResourceKind.REMOTE_FILE -> requireHttpsUri(sourceUri ?: "")
            ResourceKind.LOCAL_FILE -> throw IllegalArgumentException("LOCAL_FILE_REQUIRES_UPLOAD")
        }
    }

    fun normalizedRefreshInterval(value: Long?): Long? = value?.coerceIn(30_000L, 24 * 60 * 60 * 1000L)

    private fun requireHttpsUri(value: String) {
        val uri = requireUri(value, "WEB_URL_REQUIRED")
        require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.host?.isNotBlank() == true) {
            "WEB_URL_NOT_ALLOWED"
        }
    }

    private fun requireUri(value: String?, missingCode: String): URI {
        require(!value.isNullOrBlank()) { missingCode }
        return runCatching { URI(value.trim()) }.getOrElse { throw IllegalArgumentException("RESOURCE_URL_INVALID") }
    }
}
