package com.wkq.localsignage

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

internal fun Activity.enableEdgeToEdgeSystemBars() {
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
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = initialLeft + if (horizontal) bars.left else 0,
            top = initialTop + if (top) bars.top else 0,
            right = initialRight + if (horizontal) bars.right else 0,
            bottom = initialBottom + if (bottom) maxOf(bars.bottom, ime.bottom) else 0
        )
        insets
    }
    doOnAttach(ViewCompat::requestApplyInsets)
}
