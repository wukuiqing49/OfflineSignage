package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.wkq.localsignage.databinding.ActivitySplashBinding
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : SignageActivity<ActivitySplashBinding>() {

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            insets
        }
        binding.root.doOnAttach(ViewCompat::requestApplyInsets)
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
        if (!SignageRuntime.tryInitialize(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(com.wkq.localsignage.feature.app.R.string.storage_unavailable_title)
                .setMessage(com.wkq.localsignage.feature.app.R.string.storage_unavailable_message)
                .setPositiveButton(com.wkq.localsignage.feature.app.R.string.storage_retry) { _, _ -> navigateNext() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }
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
