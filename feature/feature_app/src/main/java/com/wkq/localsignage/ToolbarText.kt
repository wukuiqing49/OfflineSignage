package com.wkq.localsignage

import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar

/** 页面标题可随翻译长度和系统字体自然换行，返回按钮仍保持标准触控尺寸。 */
internal fun MaterialToolbar.wrapTitle() {
    for (index in 0 until childCount) {
        val label = getChildAt(index) as? TextView ?: continue
        if (label.text == title) {
            label.isSingleLine = false
            label.maxLines = Int.MAX_VALUE
            label.ellipsize = null
        }
    }
}
