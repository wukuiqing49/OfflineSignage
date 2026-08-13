package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.databinding.ActivityWelcomeBinding
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override fun initView() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (welcomeCompleted() || SignageRuntime.resources().isNotEmpty()) {
            markWelcomeCompleted()
            openPlayer()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.welcomeRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        binding.startButton.setOnClickListener {
            markWelcomeCompleted()
            openPlayer()
        }
    }

    override fun initData() = Unit

    private fun welcomeCompleted(): Boolean =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WELCOME_COMPLETED, false)

    private fun markWelcomeCompleted() {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WELCOME_COMPLETED, true)
            .apply()
    }

    private fun openPlayer() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private companion object {
        const val PREFERENCES_NAME = "local_signage_onboarding"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
    }
}
