package com.wkq.localsignage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wkq.base.activity.BaseActivity
import com.wkq.google.billing.GoogleProduct
import com.wkq.localsignage.feature.app.R
import com.wkq.localsignage.feature.app.databinding.ActivityBillingBinding
import com.wkq.localsignage.monetization.BillingViewModel
import com.wkq.localsignage.monetization.EntitlementState
import com.wkq.localsignage.monetization.EntitlementType
import com.wkq.localsignage.monetization.MonetizationRepository
import com.wkq.localsignage.monetization.MonetizationUiState
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

class BillingActivity : BaseActivity<ActivityBillingBinding>() {
    private val viewModel by viewModels<BillingViewModel>()
    private var monthlyProduct: GoogleProduct? = null
    private var subscriptionProduct: GoogleProduct? = null
    private var lifetimeProduct: GoogleProduct? = null
    private var hasPositionedContent = false

    override fun initView() {
        enableEdgeToEdgeSystemBars()
        binding.toolbarContainer.applySystemBarPadding(top = true, horizontal = true)
        binding.contentScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.billingRoot.requestFocus()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.planContainer.doOnLayout { configurePlanLayout(binding.billingRoot.width) }
        binding.monthlyButton.setOnClickListener { monthlyProduct?.let(::launchPurchase) }
        binding.subscriptionButton.setOnClickListener { subscriptionProduct?.let(::launchPurchase) }
        binding.lifetimeButton.setOnClickListener { lifetimeProduct?.let(::launchPurchase) }
        binding.restoreButton.setOnClickListener { viewModel.refresh() }
        binding.manageSubscriptionButton.setOnClickListener { openSubscriptionManagement() }
        binding.legalCenterButton.setOnClickListener {
            startActivity(Intent(this, LegalCenterActivity::class.java))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun initData() = Unit

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun configurePlanLayout(availableWidth: Int) {
        val compact = availableWidth < resources.getDimensionPixelSize(R.dimen.billing_compact_breakpoint)
        binding.planContainer.orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        configurePlanPanel(binding.monthlyPanel, compact, first = true)
        configurePlanPanel(binding.subscriptionPanel, compact, first = false)
        configurePlanPanel(binding.lifetimePanel, compact, first = false)
    }

    private fun configurePlanPanel(view: View, compact: Boolean, first: Boolean) {
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
            width = if (compact) ViewGroup.LayoutParams.MATCH_PARENT else 0
            weight = if (compact) 0f else 1f
            marginStart = if (!compact && !first) resources.getDimensionPixelSize(R.dimen.app_spacing_medium) else 0
            topMargin = if (compact && !first) resources.getDimensionPixelSize(R.dimen.app_spacing_medium) else 0
        }
    }

    private fun render(state: MonetizationUiState) {
        monthlyProduct = state.catalog.subscriptions.firstOrNull {
            it.baseProductId == MonetizationRepository.PRO_SUBSCRIPTION_ID &&
                it.basePlanId == MonetizationRepository.MONTHLY_BASE_PLAN_ID
        }
        subscriptionProduct = state.catalog.subscriptions.firstOrNull {
            it.baseProductId == MonetizationRepository.PRO_SUBSCRIPTION_ID &&
                it.basePlanId == MonetizationRepository.YEARLY_BASE_PLAN_ID
        } ?: state.catalog.subscriptions.firstOrNull {
            it.baseProductId == MonetizationRepository.PRO_SUBSCRIPTION_ID
        }
        lifetimeProduct = state.catalog.oneTimeProducts.firstOrNull {
            it.baseProductId == MonetizationRepository.LIFETIME_PRODUCT_ID
        }

        binding.entitlementStatus.text = entitlementText(state.entitlement)
        binding.monthlyPrice.text = monthlyProduct?.displayPrice()
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.billing_price_unavailable)
        binding.subscriptionPrice.text = subscriptionProduct?.displayPrice()
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.billing_price_unavailable)
        binding.lifetimePrice.text = lifetimeProduct?.displayPrice()
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.billing_price_unavailable)
        binding.monthlyButton.isEnabled = monthlyProduct != null && !state.loading
        binding.subscriptionButton.isEnabled = subscriptionProduct != null && !state.loading
        binding.lifetimeButton.isEnabled = lifetimeProduct != null && !state.loading
        binding.restoreButton.isEnabled = !state.loading
        binding.progressIndicator.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.billingError.visibility = if (state.errorMessage.isBlank()) View.GONE else View.VISIBLE
        binding.billingError.setText(R.string.billing_unavailable)
        binding.pendingNotice.visibility = if (state.entitlement.pendingProductIds.isEmpty()) View.GONE else View.VISIBLE
        binding.manageSubscriptionButton.visibility = when (state.entitlement.type) {
            EntitlementType.SUBSCRIPTION, EntitlementType.SUBSCRIPTION_GRACE -> View.VISIBLE
            else -> View.GONE
        }
        if (!hasPositionedContent) {
            hasPositionedContent = true
            binding.contentScroll.post { binding.contentScroll.scrollTo(0, 0) }
        }
    }

    private fun entitlementText(state: EntitlementState): String = when (state.type) {
        EntitlementType.TRIAL_ACTIVE -> {
            val remainingMillis = max(0L, state.trialEndsAtEpochMillis - System.currentTimeMillis())
            val remainingDays = max(1L, TimeUnit.MILLISECONDS.toDays(remainingMillis) + 1L)
            resources.getQuantityString(
                R.plurals.billing_trial_days,
                remainingDays.toInt(),
                remainingDays
            )
        }
        EntitlementType.TRIAL_EXPIRED -> getString(R.string.billing_status_trial_ended)
        EntitlementType.SUBSCRIPTION -> getString(R.string.billing_status_subscription)
        EntitlementType.SUBSCRIPTION_GRACE -> getString(R.string.billing_status_subscription_grace)
        EntitlementType.LIFETIME -> getString(R.string.billing_status_lifetime)
    }

    private fun launchPurchase(product: GoogleProduct) {
        val response = MonetizationRepository.launchPurchase(this, product)
        if (!response.isSuccess) {
            Toast.makeText(
                this,
                R.string.billing_purchase_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun GoogleProduct.displayPrice(): String {
        return formattedPrice.trim()
    }

    private fun openSubscriptionManagement() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${MonetizationRepository.PRO_SUBSCRIPTION_ID}&package=$packageName"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.billing_manage_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
