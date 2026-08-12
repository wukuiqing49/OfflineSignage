package com.wkq.localsignage.feature.app

import com.wkq.util.CoreUtils

object FeatureAppEntry {

    fun isAvailable(): Boolean = CoreUtils.isInitialized()
}
