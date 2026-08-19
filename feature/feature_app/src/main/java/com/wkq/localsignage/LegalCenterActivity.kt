package com.wkq.localsignage

import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.databinding.ActivityLegalCenterBinding

class LegalCenterActivity : BaseActivity<ActivityLegalCenterBinding>() {
    override fun initView() {
        enableEdgeToEdgeSystemBars()
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
    }

    override fun initData() = Unit

    private fun openDocument(document: String) {
        startActivity(LegalDocumentActivity.intent(this, document))
    }
}
