package com.wkq.localsignage

import android.view.ViewGroup
import android.widget.LinearLayout
import com.wkq.localsignage.feature.app.R

/** 按可用宽度和字体缩放选择双栏；窄屏保留自然高度和完整滚动。 */
internal fun LinearLayout.adaptTwoColumns() {
    fun arrange(width: Int) {
        val available = width - paddingLeft - paddingRight
        if (available <= 0) return
        val stacked = available / resources.configuration.fontScale.coerceAtLeast(1f) <
            resources.getDimensionPixelSize(R.dimen.ui_columns_breakpoint)
        orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val gap = resources.getDimensionPixelSize(R.dimen.app_spacing_medium)
        for (index in 0 until childCount) {
            getChildAt(index).layoutParams = (getChildAt(index).layoutParams as LinearLayout.LayoutParams).apply {
                this.width = if (stacked) ViewGroup.LayoutParams.MATCH_PARENT else 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = if (stacked) 0f else 1f
                marginStart = if (!stacked && index > 0) gap else 0
                topMargin = if (stacked && index > 0) gap else 0
            }
        }
    }
    addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) arrange(right - left)
    }
    post { arrange(width) }
}
