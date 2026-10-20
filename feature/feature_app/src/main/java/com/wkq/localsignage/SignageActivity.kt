package com.wkq.localsignage

import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.wkq.base.activity.BaseActivity

/** 页面各自管理 Insets，避免基类根布局与 Toolbar/滚动区域重复消费。 */
abstract class SignageActivity<VB : ViewBinding> : BaseActivity<VB>() {
    override fun configureEdgeToEdge() {
        WindowCompat.enableEdgeToEdge(window)
    }

    override fun applyDefaultSystemBarsInsets() = Unit
}
