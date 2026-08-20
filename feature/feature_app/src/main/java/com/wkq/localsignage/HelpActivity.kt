package com.wkq.localsignage

import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.databinding.ActivityHelpBinding

class HelpActivity : BaseActivity<ActivityHelpBinding>() {
    override fun initView() {
        enableEdgeToEdgeSystemBars()
        binding.toolbarContainer.applySystemBarPadding(horizontal = true)
        binding.helpScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.quickStartButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_QUICK_START) }
        binding.contentButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_CONTENT) }
        binding.htmlButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_HTML) }
        binding.playbackButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_PLAYBACK) }
        binding.multiDeviceButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_MULTI_DEVICE) }
        binding.autostartButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_AUTOSTART) }
        binding.troubleshootingButton.setOnClickListener {
            openArticle(HelpArticleActivity.ARTICLE_TROUBLESHOOTING)
        }
        binding.purchaseButton.setOnClickListener { openArticle(HelpArticleActivity.ARTICLE_PURCHASE) }
        binding.legalButton.setOnClickListener {
            startActivity(android.content.Intent(this, LegalCenterActivity::class.java))
        }
    }

    override fun initData() = Unit

    private fun openArticle(article: String) {
        startActivity(HelpArticleActivity.intent(this, article))
    }
}
