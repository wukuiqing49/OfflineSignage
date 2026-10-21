package com.wkq.localsignage

import android.content.pm.ActivityInfo

/** 尊重显式方向；仅多窗口及 Android 16+ 大屏交由系统管理。 */
internal object SignageOrientationPolicy {
    fun resolve(preference: String, smallestWidthDp: Int, multiWindow: Boolean, sdkInt: Int): Int {
        if (multiWindow || (sdkInt >= 36 && smallestWidthDp >= 600)) {
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        return when (preference) {
            "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
