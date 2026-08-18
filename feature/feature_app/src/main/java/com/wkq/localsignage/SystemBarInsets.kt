package com.wkq.localsignage

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.wkq.localsignage.feature.app.R

@Suppress("DEPRECATION")
internal fun Activity.enableEdgeToEdgeSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = ContextCompat.getColor(this, R.color.billing_surface)
    window.navigationBarColor = ContextCompat.getColor(this, R.color.billing_background)
    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isNightMode
        isAppearanceLightNavigationBars = !isNightMode
    }
}

internal fun View.applySystemBarPadding(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialLeft + if (horizontal) bars.left else 0,
            top = initialTop + if (top) bars.top else 0,
            right = initialRight + if (horizontal) bars.right else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0
        )
        insets
    }
}
