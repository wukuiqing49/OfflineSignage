package com.wkq.localsignage

import android.app.Application
import com.wkq.util.CoreUtils
import com.wkq.util.CoreUtilsConfig
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

class LocalSignageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CoreUtils.init(
            context = this,
            config = CoreUtilsConfig(
                debug = BuildConfig.DEBUG,
                initLog = false,
                logCaptureCrash = false
            )
        )
        SignageRuntime.initialize(this)
    }
}
