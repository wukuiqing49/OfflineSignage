package com.wkq.localsignage

import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.runtime.SignageRuntime

/** 页面各自管理 Insets，避免基类根布局与 Toolbar/滚动区域重复消费。 */
abstract class SignageActivity<VB : ViewBinding> : BaseActivity<VB>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applySignageOrientation()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        applySignageOrientation()
    }

    protected fun applySignageOrientation() {
        val target = SignageOrientationPolicy.resolve(
            SignageRuntime.orientationPreference(),
            resources.configuration.smallestScreenWidthDp,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode,
            Build.VERSION.SDK_INT
        )
        if (requestedOrientation != target) requestedOrientation = target
    }

    override fun configureEdgeToEdge() {
        WindowCompat.enableEdgeToEdge(window)
    }

    override fun applyDefaultSystemBarsInsets() = Unit
}
