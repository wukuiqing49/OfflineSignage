package com.wkq.localsignage.monetization

import android.app.Activity
import android.content.Context
import com.wkq.google.GoogleKit
import com.wkq.google.billing.GoogleBillingCatalog
import com.wkq.google.billing.GoogleBillingResponse
import com.wkq.google.billing.GoogleBillingResponseCode
import com.wkq.google.billing.GoogleProduct
import com.wkq.google.billing.GoogleProductType
import com.wkq.google.billing.GooglePurchase
import com.wkq.google.billing.GooglePurchaseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MonetizationRepository {
    const val PRO_SUBSCRIPTION_ID = "pro_subscription"
    const val LIFETIME_PRODUCT_ID = "pro_lifetime"
    const val MONTHLY_BASE_PLAN_ID = "pro-mouth"
    const val YEARLY_BASE_PLAN_ID = "pro-yearly"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private lateinit var appContext: Context
    private lateinit var store: EntitlementStore
    private var playLicensePublicKey: String = ""
    private var allowMissingPublicKey: Boolean = false
    private val policy = EntitlementPolicy()

    private val _uiState = MutableStateFlow(
        MonetizationUiState(
            entitlement = EntitlementState(
                type = EntitlementType.TRIAL_ACTIVE,
                trialStartedAtEpochMillis = 0L,
                trialEndsAtEpochMillis = 0L
            )
        )
    )
    val uiState: StateFlow<MonetizationUiState> = _uiState.asStateFlow()

    fun initialize(
        context: Context,
        playLicensePublicKey: String,
        allowMissingPublicKey: Boolean
    ) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        this.playLicensePublicKey = playLicensePublicKey
        this.allowMissingPublicKey = allowMissingPublicKey
        store = EntitlementStore(appContext)
        _uiState.value = MonetizationUiState(entitlement = policy.evaluateLocal(store.snapshot()))
        GoogleKit.billing.setPurchaseUpdatedListener { response, purchases ->
            scope.launch {
                if (response.isSuccess) {
                    processPurchaseUpdate(purchases)
                    refreshNow(loadCatalog = true)
                } else if (response.responseCode == GoogleBillingResponseCode.USER_CANCELED) {
                    _uiState.value = _uiState.value.copy(loading = false, errorMessage = "")
                } else {
                    _uiState.value = _uiState.value.copy(loading = false, errorMessage = response.message)
                }
            }
        }
        refresh(loadCatalog = false)
    }

    fun refresh(loadCatalog: Boolean = true) {
        if (!::appContext.isInitialized) return
        scope.launch { refreshNow(loadCatalog) }
    }

    fun launchPurchase(activity: Activity, product: GoogleProduct): GoogleBillingResponse {
        return GoogleKit.billing.launchPurchase(
            activity = activity,
            productId = product.productId,
            offerToken = product.offerToken
        )
    }

    private suspend fun refreshNow(loadCatalog: Boolean) = refreshMutex.withLock {
        _uiState.value = _uiState.value.copy(loading = true, errorMessage = "")
        val catalog = if (loadCatalog) {
            GoogleKit.billing.queryConfiguredCatalog().getOrElse {
                _uiState.value.catalog
            }
        } else {
            _uiState.value.catalog
        }
        val subscriptionResult = GoogleKit.billing.queryActivePurchases(GoogleProductType.SUBS)
        val lifetimeResult = GoogleKit.billing.queryActivePurchases(GoogleProductType.IN_APP)
        if (subscriptionResult.isFailure || lifetimeResult.isFailure) {
            val error = subscriptionResult.exceptionOrNull() ?: lifetimeResult.exceptionOrNull()
            _uiState.value = MonetizationUiState(
                entitlement = policy.evaluateLocal(store.snapshot()),
                catalog = catalog,
                loading = false,
                errorMessage = error?.message.orEmpty()
            )
            return@withLock
        }

        val allPurchases = subscriptionResult.getOrThrow() + lifetimeResult.getOrThrow()
        val pendingIds = allPurchases
            .filter { it.purchaseState == GooglePurchaseState.PENDING }
            .flatMap { it.products }
            .toSet()
        val verified = allPurchases.filter(::isVerifiedPurchased)
        val hasLifetime = verified.any { LIFETIME_PRODUCT_ID in it.products }
        val hasSubscription = verified.any { PRO_SUBSCRIPTION_ID in it.products }
        acknowledgeVerifiedPurchases(verified)
        store.saveVerifiedPurchases(hasLifetime, hasSubscription)
        val snapshot = store.snapshot()
        _uiState.value = MonetizationUiState(
            entitlement = policy.evaluateVerified(
                snapshot = snapshot,
                hasLifetime = hasLifetime,
                hasSubscription = hasSubscription,
                billingAvailable = true,
                pendingProductIds = pendingIds
            ),
            catalog = catalog,
            loading = false
        )
    }

    private suspend fun processPurchaseUpdate(purchases: List<GooglePurchase>) {
        val verified = purchases.filter(::isVerifiedPurchased)
        acknowledgeVerifiedPurchases(verified)
    }

    private suspend fun acknowledgeVerifiedPurchases(purchases: List<GooglePurchase>) {
        purchases.filterNot { it.isAcknowledged }.forEach { purchase ->
            GoogleKit.billing.acknowledgePurchase(purchase.purchaseToken)
        }
    }

    private fun isVerifiedPurchased(purchase: GooglePurchase): Boolean {
        if (purchase.purchaseState != GooglePurchaseState.PURCHASED) return false
        return PurchaseVerifier.verify(
            purchase = purchase,
            expectedPackageName = appContext.packageName,
            allowedProductIds = setOf(PRO_SUBSCRIPTION_ID, LIFETIME_PRODUCT_ID),
            licensePublicKey = playLicensePublicKey,
            allowMissingPublicKey = allowMissingPublicKey
        )
    }
}
