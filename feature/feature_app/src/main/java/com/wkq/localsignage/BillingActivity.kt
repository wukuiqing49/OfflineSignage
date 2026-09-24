package com.wkq.localsignage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
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
import com.wkq.localsignage.monetization.playDisplayPrice
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

class BillingActivity : BaseActivity<ActivityBillingBinding>() {
    private val viewModel by viewModels<BillingViewModel>()
    private var subscriptionProduct: GoogleProduct? = null
    private var hasPositionedContent = false
    private var purchaseInFlight = false

    override fun initView() {
        binding.toolbar.wrapTitle()
        enableEdgeToEdgeSystemBars(binding.toolbarContainer)
        binding.toolbarContainer.applySystemBarPadding(horizontal = true)
        binding.contentScroll.applySystemBarPadding(bottom = true, horizontal = true)
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.restoreButton.post { binding.restoreButton.requestFocus() }
        } else {
            binding.billingRoot.requestFocus()
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.planContainer.doOnLayout { configurePlanLayout(it.width) }
        binding.subscriptionButton.setOnClickListener { subscriptionProduct?.let(::launchPurchase) }
        // Launch offer: annual only. Legacy monthly and lifetime purchases are still restored by the repository.
        binding.monthlyPanel.visibility = View.GONE
        binding.lifetimePanel.visibility = View.GONE
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
        val compact = availableWidth / resources.configuration.fontScale.coerceAtLeast(1f) <
            resources.getDimensionPixelSize(R.dimen.billing_compact_breakpoint)
        binding.planContainer.orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        configurePlanPanel(binding.subscriptionPanel, compact, first = true)
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
        subscriptionProduct = state.catalog.subscriptions.firstOrNull {
            it.baseProductId == MonetizationRepository.PRO_SUBSCRIPTION_ID &&
                it.basePlanId == MonetizationRepository.YEARLY_BASE_PLAN_ID &&
                it.formattedPrice.isNotBlank()
        }
        val catalogReady = subscriptionProduct != null && state.errorMessage.isBlank()
        val showLoading = state.isCatalogPending

        binding.entitlementStatus.text = entitlementText(state.entitlement)
        binding.subscriptionPrice.text = subscriptionProduct?.displayPrice().orEmpty()
        binding.subscriptionButton.isEnabled = subscriptionProduct != null && !state.loading && !purchaseInFlight
        binding.restoreButton.isEnabled = !state.loading
        binding.contentScroll.visibility = if (showLoading) View.INVISIBLE else View.VISIBLE
        binding.progressIndicator.visibility = if (showLoading) View.VISIBLE else View.GONE
        binding.planSectionTitle.visibility = if (catalogReady) View.VISIBLE else View.GONE
        binding.planContainer.visibility = if (catalogReady) View.VISIBLE else View.GONE
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
        if (purchaseInFlight) return
        purchaseInFlight = true
        binding.subscriptionButton.isEnabled = false
        lifecycleScope.launch {
            val response = MonetizationRepository.launchPurchase(this@BillingActivity, product)
            purchaseInFlight = false
            binding.subscriptionButton.isEnabled = subscriptionProduct != null && !viewModel.uiState.value.loading
            if (!response.isSuccess) {
                Toast.makeText(
                    this@BillingActivity,
                    R.string.billing_purchase_unavailable,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun GoogleProduct.displayPrice(): String = playDisplayPrice(this) { price, period ->
        when (period) {
            "P1M" -> getString(R.string.billing_price_monthly_format, price)
            "P1Y" -> getString(R.string.billing_price_yearly_format, price)
            else -> price
        }
    }.orEmpty()

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
