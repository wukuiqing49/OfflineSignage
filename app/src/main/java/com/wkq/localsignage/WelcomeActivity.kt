package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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
        binding.welcomeRoot.doOnLayout { configureWelcomeLayout(it.width) }
        binding.startButton.setOnClickListener {
            markWelcomeCompleted()
            openPlayer()
        }
    }

    override fun initData() = Unit

    private fun configureWelcomeLayout(availableWidth: Int) {
        val compact = availableWidth < resources.getDimensionPixelSize(R.dimen.pairing_compact_breakpoint)
        val padding = resources.getDimensionPixelSize(
            if (compact) R.dimen.welcome_compact_screen_padding else R.dimen.welcome_screen_padding
        )
        val gap = resources.getDimensionPixelSize(
            if (compact) R.dimen.welcome_compact_content_gap else R.dimen.welcome_content_gap
        )
        binding.welcomeContent.apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }
        binding.welcomeIntro.layoutParams =
            (binding.welcomeIntro.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compact) 0f else 1f
            }
        binding.welcomeSetup.layoutParams =
            (binding.welcomeSetup.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compact) 0f else 1f
                marginStart = if (compact) 0 else gap
                topMargin = if (compact) gap else 0
            }
    }

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
