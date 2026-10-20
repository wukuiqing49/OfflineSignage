package com.wkq.localsignage

import android.content.pm.ActivityInfo

internal object PlaybackOrientationPolicy {
    fun resolve(preference: String, smallestWidthDp: Int, multiWindow: Boolean): Int {
        // 大屏与多窗口始终遵从系统窗口方向，不申请兼容模式或固定宽高比。
        if (smallestWidthDp >= 600 || multiWindow) return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        return when (preference) {
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
