package com.wkq.localsignage.feature.app.player

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.wkq.localsignage.feature.app.R
import com.wkq.localsignage.feature.app.model.TextStylePolicy

internal object DisplayTypefaceResolver {
    fun resolve(context: Context, fontFamily: String?, text: String?): Typeface = when (TextStylePolicy.fontFamilyForText(fontFamily, text)) {
        "SYSTEM_SERIF" -> Typeface.SERIF
        "MONOSPACE" -> Typeface.MONOSPACE
        "LATO" -> ResourcesCompat.getFont(context, R.font.lato_regular)
        "CRIMSON_TEXT" -> ResourcesCompat.getFont(context, R.font.crimson_text_regular)
        "BEBAS_NEUE" -> ResourcesCompat.getFont(context, R.font.bebas_neue_regular)
        "MA_SHAN_ZHENG" -> ResourcesCompat.getFont(context, R.font.ma_shan_zheng)
        "ZCOOL_XIAOWEI" -> ResourcesCompat.getFont(context, R.font.zcool_xiaowei)
        "ZCOOL_KUAILE" -> ResourcesCompat.getFont(context, R.font.zcool_kuaile)
        else -> Typeface.SANS_SERIF
    } ?: Typeface.SANS_SERIF
}
