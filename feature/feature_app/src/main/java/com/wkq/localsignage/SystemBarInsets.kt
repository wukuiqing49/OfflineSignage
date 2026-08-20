package com.wkq.localsignage

import android.app.Activity
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import com.wkq.localsignage.feature.app.R
import kotlin.math.max

@Suppress("DEPRECATION")
internal fun Activity.enableEdgeToEdgeSystemBars(topBar: View) {
    // BaseActivity 已让普通内容避开系统栏；标题页不再额外消费 top inset。
    // 状态栏直接使用与 Toolbar 相同的 surface 颜色，避免夜间模式出现黑色断层。
    WindowCompat.setDecorFitsSystemWindows(window, true)
    window.statusBarColor = ContextCompat.getColor(this, R.color.billing_surface)
    window.navigationBarColor = ContextCompat.getColor(this, R.color.billing_background)
    installTopChromeScrims(topBar)
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
    doOnAttach(ViewCompat::requestApplyInsets)
}

/**
 * Android 15+ 对高 targetSdk 应用强制透明状态栏，statusBarColor 不再决定最终背景色。
 * 在 DecorView 顶部绘制同色层，并把标题背景延伸到横屏刘海安全区。
 * 导航栏区域保持页面背景色，避免右侧三键导航栏被标题色污染。
 */
private fun Activity.installTopChromeScrims(topBar: View) {
    val decorView = window.decorView as? FrameLayout ?: return
    val statusScrim = decorView.obtainScrim(this, STATUS_BAR_SCRIM_TAG)
    val leftScrim = decorView.obtainScrim(this, LEFT_TOP_BAR_SCRIM_TAG)
    val rightScrim = decorView.obtainScrim(this, RIGHT_TOP_BAR_SCRIM_TAG)
    val surfaceColor = ContextCompat.getColor(this, R.color.billing_surface)
    listOf(statusScrim, leftScrim, rightScrim).forEach { it.setBackgroundColor(surfaceColor) }

    ViewCompat.setOnApplyWindowInsetsListener(statusScrim) { view, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = statusBars.top
            leftMargin = navigationBars.left
            rightMargin = navigationBars.right
            topMargin = 0
            gravity = Gravity.TOP
        }
        insets
    }

    fun configureSideScrim(view: View, alignStart: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { sideView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val sideWidth = if (alignStart) {
                max(systemBars.left, cutout.left) - navigationBars.left
            } else {
                max(systemBars.right, cutout.right) - navigationBars.right
            }.coerceAtLeast(0)
            sideView.layoutParams = (sideView.layoutParams as FrameLayout.LayoutParams).apply {
                width = sideWidth
                height = topBar.height
                leftMargin = if (alignStart) navigationBars.left else 0
                rightMargin = if (alignStart) 0 else navigationBars.right
                topMargin = statusBars.top
                gravity = Gravity.TOP or if (alignStart) Gravity.START else Gravity.END
            }
            insets
        }
    }

    configureSideScrim(leftScrim, alignStart = true)
    configureSideScrim(rightScrim, alignStart = false)
    val scrims = listOf(statusScrim, leftScrim, rightScrim)
    scrims.forEach { it.doOnAttach(ViewCompat::requestApplyInsets) }
    topBar.doOnLayout { scrims.forEach(ViewCompat::requestApplyInsets) }
}

private const val STATUS_BAR_SCRIM_TAG = "local_signage_status_bar_scrim"
private const val LEFT_TOP_BAR_SCRIM_TAG = "local_signage_left_top_bar_scrim"
private const val RIGHT_TOP_BAR_SCRIM_TAG = "local_signage_right_top_bar_scrim"

private fun FrameLayout.obtainScrim(activity: Activity, tagValue: String): View =
    findViewWithTag<View>(tagValue) ?: View(activity).apply {
        tag = tagValue
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(
            this,
            FrameLayout.LayoutParams(0, 0, Gravity.TOP)
        )
    }
