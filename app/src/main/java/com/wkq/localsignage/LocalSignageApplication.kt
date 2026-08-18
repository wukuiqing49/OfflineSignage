package com.wkq.localsignage

import android.app.Application
import com.wkq.util.CoreUtils
import com.wkq.util.CoreUtilsConfig
import com.wkq.localsignage.feature.app.FeatureAppEntry

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
        FeatureAppEntry.initialize(
            context = this,
            debug = BuildConfig.DEBUG,
            playLicensePublicKey = BuildConfig.PLAY_LICENSE_PUBLIC_KEY,
            googleServerClientId = getString(R.string.default_web_client_id)
        )
    }
}
