package com.wkq.localsignage.feature.app.server

import com.wkq.localsignage.feature.app.model.SignageSettings
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/** 更新设置时保留未提交字段；错误值不能悄悄重置播放配置。 */
internal fun applySettingsPatch(body: String, current: SignageSettings): SignageSettings {
    val json = try { JSONObject(body) } catch (error: JSONException) {
        throw IllegalArgumentException("INVALID_SETTINGS", error)
    }
    fun boolean(key: String, previous: Boolean): Boolean {
        if (!json.has(key)) return previous
        return json.opt(key) as? Boolean ?: throw IllegalArgumentException("INVALID_SETTINGS_$key")
    }
    val orientation = if (!json.has("orientation")) current.orientation else {
        val value = (json.opt("orientation") as? String)?.uppercase(Locale.ROOT)
        require(value in setOf("AUTO", "LANDSCAPE", "PORTRAIT")) { "INVALID_ORIENTATION" }
        requireNotNull(value)
    }
    val fallback = when {
        !json.has("fallbackSceneId") -> current.fallbackSceneId
        json.isNull("fallbackSceneId") -> null
        else -> json.opt("fallbackSceneId") as? String
            ?: throw IllegalArgumentException("INVALID_FALLBACK_SCENE")
    }
    return current.copy(
        fallbackSceneId = fallback,
        keepScreenAwake = boolean("keepScreenAwake", current.keepScreenAwake),
        autoResume = boolean("autoResume", current.autoResume),
        fullscreen = boolean("fullscreen", current.fullscreen),
        orientation = orientation
    )
}
