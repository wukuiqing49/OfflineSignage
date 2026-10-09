package com.wkq.localsignage

import android.app.Application
import com.wkq.util.CoreUtils
import com.wkq.util.CoreUtilsConfig
import com.wkq.localsignage.feature.app.FeatureAppEntry
import com.wkq.google.commercial.GoogleCommercialConfig

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
            googleServerClientId = getString(R.string.default_web_client_id),
            commercialConfig = GoogleCommercialConfig(
                enabled = BuildConfig.DEVHUB_ENABLED &&
                    (!BuildConfig.DEBUG || BuildConfig.DEVHUB_DEBUG_ENABLED),
                apiUrl = BuildConfig.DEVHUB_API_URL,
                appKey = BuildConfig.DEVHUB_APP_KEY,
                playIntegrityCloudProjectNumber =
                    BuildConfig.DEVHUB_PLAY_INTEGRITY_PROJECT_NUMBER.toLongOrNull()
            )
        )
    }
}
