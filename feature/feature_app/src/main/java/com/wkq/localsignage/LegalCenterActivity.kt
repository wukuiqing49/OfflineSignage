package com.wkq.localsignage

import android.content.res.Configuration
import android.widget.Toast
import com.wkq.google.GoogleKit
import com.wkq.localsignage.feature.app.R
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
        bindAnalyticsConsent()
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.privacyButton.post { binding.privacyButton.requestFocus() }
        }
    }

    override fun initData() = Unit

    private fun bindAnalyticsConsent() {
        val analytics = GoogleKit.commercial
        binding.analyticsSwitch.setOnCheckedChangeListener(null)
        binding.analyticsSwitch.isChecked = analytics.isCollectionEnabled()
        binding.analyticsSwitch.isEnabled = analytics.isConfigured()
        if (!analytics.isConfigured()) {
            binding.analyticsSummary.setText(R.string.legal_analytics_unavailable)
        }
        binding.analyticsSwitch.setOnCheckedChangeListener { _, enabled ->
            binding.analyticsSwitch.isEnabled = false
            analytics.setCollectionEnabled(enabled) { successful ->
                if (isFinishing || isDestroyed) return@setCollectionEnabled
                // Updating the checked state must not start another consent request.
                binding.analyticsSwitch.setOnCheckedChangeListener(null)
                binding.analyticsSwitch.isChecked = analytics.isCollectionEnabled()
                binding.analyticsSwitch.isEnabled = analytics.isConfigured()
                if (!successful) {
                    Toast.makeText(this, R.string.legal_analytics_update_failed, Toast.LENGTH_LONG).show()
                }
                bindAnalyticsConsent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bindAnalyticsConsent()
    }

    private fun openDocument(document: String) {
        startActivity(LegalDocumentActivity.intent(this, document))
    }
}
