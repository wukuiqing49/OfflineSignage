package com.wkq.localsignage

import android.content.res.Configuration
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.databinding.ActivityLegalCenterBinding

class LegalCenterActivity : BaseActivity<ActivityLegalCenterBinding>() {
    override fun initView() {
        binding.toolbar.wrapTitle()
        enableEdgeToEdgeSystemBars(binding.toolbarContainer)
        binding.toolbarContainer.applySystemBarPadding(horizontal = true)
        binding.legalScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.privacyButton.setOnClickListener {
            openDocument(LegalDocumentActivity.DOCUMENT_PRIVACY)
        }
        binding.termsButton.setOnClickListener {
            openDocument(LegalDocumentActivity.DOCUMENT_TERMS)
        }
        binding.subscriptionButton.setOnClickListener {
            openDocument(LegalDocumentActivity.DOCUMENT_SUBSCRIPTION)
        }
        binding.dataDeletionButton.setOnClickListener {
            openDocument(LegalDocumentActivity.DOCUMENT_DATA_DELETION)
        }
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.privacyButton.post { binding.privacyButton.requestFocus() }
        }
    }

    override fun initData() = Unit

    private fun openDocument(document: String) {
        startActivity(LegalDocumentActivity.intent(this, document))
    }
}
