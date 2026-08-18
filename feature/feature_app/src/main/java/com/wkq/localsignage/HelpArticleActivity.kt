package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import android.view.View
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.R
import com.wkq.localsignage.feature.app.databinding.ActivityLegalDocumentBinding

class HelpArticleActivity : BaseActivity<ActivityLegalDocumentBinding>() {
    override fun initView() {
        enableEdgeToEdgeSystemBars()
        binding.toolbar.applySystemBarPadding(top = true, horizontal = true)
        binding.documentScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val article = articleResources(intent.getStringExtra(EXTRA_ARTICLE))
        binding.toolbar.setTitle(article.title)
        binding.documentBody.setText(article.body)
        binding.documentVisualCard.visibility = View.VISIBLE
        binding.documentVisual.setImageResource(article.visual)
        binding.documentVisualCaption.setText(article.visualCaption)
    }

    override fun initData() = Unit

    private fun articleResources(article: String?): HelpArticle = when (article) {
        ARTICLE_CONTENT -> HelpArticle(
            R.string.help_content_title,
            R.string.help_content_body,
            R.drawable.illustration_help_content,
            R.string.help_visual_content_caption
        )
        ARTICLE_HTML -> HelpArticle(
            R.string.help_html_title,
            R.string.help_html_body,
            R.drawable.illustration_help_html,
            R.string.help_visual_html_caption
        )
        ARTICLE_PLAYBACK -> HelpArticle(
            R.string.help_playback_title,
            R.string.help_playback_body,
            R.drawable.illustration_help_content,
            R.string.help_visual_content_caption
        )
        ARTICLE_MULTI_DEVICE -> HelpArticle(
            R.string.help_multi_device_title,
            R.string.help_multi_device_body,
            R.drawable.illustration_help_devices,
            R.string.help_visual_devices_caption
        )
        ARTICLE_TROUBLESHOOTING -> HelpArticle(
            R.string.help_troubleshooting_title,
            R.string.help_troubleshooting_body,
            R.drawable.illustration_help_pairing,
            R.string.help_visual_pairing_caption
        )
        ARTICLE_PURCHASE -> HelpArticle(
            R.string.help_purchase_title,
            R.string.help_purchase_body,
            R.drawable.illustration_help_billing,
            R.string.help_visual_billing_caption
        )
        else -> HelpArticle(
            R.string.help_quick_start_title,
            R.string.help_quick_start_body,
            R.drawable.illustration_help_pairing,
            R.string.help_visual_pairing_caption
        )
    }

    private data class HelpArticle(
        val title: Int,
        val body: Int,
        val visual: Int,
        val visualCaption: Int
    )

    companion object {
        const val ARTICLE_QUICK_START = "quick_start"
        const val ARTICLE_CONTENT = "content"
        const val ARTICLE_HTML = "html"
        const val ARTICLE_PLAYBACK = "playback"
        const val ARTICLE_MULTI_DEVICE = "multi_device"
        const val ARTICLE_TROUBLESHOOTING = "troubleshooting"
        const val ARTICLE_PURCHASE = "purchase"
        private const val EXTRA_ARTICLE = "article"

        fun intent(context: Context, article: String): Intent =
            Intent(context, HelpArticleActivity::class.java).putExtra(EXTRA_ARTICLE, article)
    }
}
