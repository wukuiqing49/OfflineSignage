package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivitySplashBinding
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    override fun initView() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            if (!isFinishing && !isDestroyed) {
                navigateNext()
            }
        }
    }

    override fun initData() = Unit

    private fun navigateNext() {
        val nextIntent = if (!welcomeCompleted() && SignageRuntime.resources().isEmpty()) {
            Intent(this, WelcomeActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(nextIntent)
        finish()
    }

    private fun welcomeCompleted(): Boolean =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WELCOME_COMPLETED, false)

    private companion object {
        const val SPLASH_DURATION_MS = 1000L
        const val PREFERENCES_NAME = "local_signage_onboarding"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
    }
}
