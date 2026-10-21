package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.wkq.localsignage.databinding.ActivityWelcomeBinding
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.R as FeatureAppR

class WelcomeActivity : SignageActivity<ActivityWelcomeBinding>() {

    override fun initView() {
        if (welcomeCompleted() || SignageRuntime.resources().isNotEmpty()) {
            markWelcomeCompleted()
            openPlayer()
            return
        }

        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.welcomeRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        binding.welcomeRoot.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            configureWelcomeLayout(
                view.width - view.paddingLeft - view.paddingRight,
                view.height - view.paddingTop - view.paddingBottom
            )
        }
        binding.startButton.setOnClickListener {
            markWelcomeCompleted()
            openPlayer()
        }
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.startButton.post { binding.startButton.requestFocus() }
        }
    }

    override fun initData() = Unit

    private fun configureWelcomeLayout(availableWidth: Int, availableHeight: Int) {
        val compactWidth = availableWidth / resources.configuration.fontScale.coerceAtLeast(1f) < resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_compact_breakpoint)
        val compactHeight = availableHeight < resources.getDimensionPixelSize(FeatureAppR.dimen.pairing_compact_height_breakpoint)
        val compact = compactWidth || compactHeight
        val padding = resources.getDimensionPixelSize(
            if (compact) FeatureAppR.dimen.welcome_compact_screen_padding else FeatureAppR.dimen.welcome_screen_padding
        )
        val gap = resources.getDimensionPixelSize(
            if (compact) FeatureAppR.dimen.welcome_compact_content_gap else FeatureAppR.dimen.welcome_content_gap
        )
        binding.welcomeContent.apply {
            orientation = if (compactWidth) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }
        binding.welcomeIntro.layoutParams =
            (binding.welcomeIntro.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compactWidth) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compactWidth) 0f else 1f
            }
        binding.welcomeSetup.layoutParams =
            (binding.welcomeSetup.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compactWidth) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compactWidth) 0f else 1f
                marginStart = if (compactWidth) 0 else gap
                topMargin = if (compactWidth) gap else 0
            }
        // XML id 沿用历史命名：title 是品牌标签，summary 才是主标题。
        binding.welcomeTitle.textSize = 12f
        binding.welcomeSummary.textSize = if (compactHeight) 28f else 36f
        binding.welcomeSummary.maxLines = Int.MAX_VALUE
        binding.welcomeOfflineNote.visibility = View.VISIBLE
        binding.welcomeOfflineNote.textSize = if (compactHeight) 14f else 17f
        binding.welcomeOfflineNote.maxLines = Int.MAX_VALUE
        binding.welcomeSetupTitle.textSize = 14f
        binding.welcomeIntro.visibility = View.VISIBLE
    }

    private fun welcomeCompleted(): Boolean =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WELCOME_COMPLETED, false)

    private fun markWelcomeCompleted() {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_WELCOME_COMPLETED, true)
        }
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
