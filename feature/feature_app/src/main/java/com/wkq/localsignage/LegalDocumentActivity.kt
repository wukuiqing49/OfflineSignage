package com.wkq.localsignage

import android.content.Context
import android.content.Intent
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.R
import com.wkq.localsignage.feature.app.databinding.ActivityLegalDocumentBinding
import java.nio.charset.StandardCharsets

class LegalDocumentActivity : BaseActivity<ActivityLegalDocumentBinding>() {
    override fun initView() {
        enableEdgeToEdgeSystemBars()
        binding.toolbarContainer.applySystemBarPadding(top = true, horizontal = true)
        binding.documentScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val (title, document) = documentResources(intent.getStringExtra(EXTRA_DOCUMENT))
        binding.toolbar.setTitle(title)
        binding.documentBody.text = resources.openRawResource(document)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }

    override fun initData() = Unit

    private fun documentResources(document: String?): Pair<Int, Int> = when (document) {
        DOCUMENT_TERMS -> R.string.legal_terms_title to R.raw.legal_terms
        DOCUMENT_SUBSCRIPTION -> R.string.legal_subscription_title to R.raw.legal_subscription
        DOCUMENT_DATA_DELETION -> R.string.legal_data_deletion_title to R.raw.legal_data_deletion
        else -> R.string.legal_privacy_title to R.raw.legal_privacy
    }

    companion object {
        const val DOCUMENT_PRIVACY = "privacy"
        const val DOCUMENT_TERMS = "terms"
        const val DOCUMENT_SUBSCRIPTION = "subscription"
        const val DOCUMENT_DATA_DELETION = "data_deletion"
        private const val EXTRA_DOCUMENT = "document"

        fun intent(context: Context, document: String): Intent =
            Intent(context, LegalDocumentActivity::class.java).putExtra(EXTRA_DOCUMENT, document)
    }
}
