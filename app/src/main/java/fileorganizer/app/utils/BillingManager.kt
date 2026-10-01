package fileorganizer.app.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small, app-wide Google Play Billing controller for the Premium subscription.
 *
 * Product: premium
 * Base plans: 6months, 12months
 */
object BillingManager {
    private const val TAG = "BillingManager"
    private const val PRODUCT_ID = "premium"
    const val PLAN_6_MONTHS = "6months"
    const val PLAN_12_MONTHS = "12months"

    private const val PREFS_NAME = "BillingState"
    private const val KEY_PREMIUM = "premium_active"

    data class PremiumPlan(
        val basePlanId: String,
        val formattedPrice: String,
        val billingPeriod: String
    )

    enum class BillingEvent {
        PURCHASE_COMPLETED,
        PURCHASE_PENDING,
        PURCHASE_CANCELED,
        RESTORED,
        NOTHING_TO_RESTORE,
        PRODUCT_UNAVAILABLE,
        BILLING_UNAVAILABLE,
        ERROR
    }

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var isConnecting = false
    private var isPurchaseQueryInFlight = false
    private var latestProductDetails: ProductDetails? = null
    private val offerTokens = mutableMapOf<String, String>()
    private val pendingConnectedActions = mutableListOf<() -> Unit>()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // False until Google Play has successfully answered an entitlement query in this process.
    // Ads use this so a Premium user is never shown an ad while entitlement is still unknown.
    private val _entitlementReady = MutableStateFlow(false)
    val entitlementReady: StateFlow<Boolean> = _entitlementReady.asStateFlow()

    private val _plans = MutableStateFlow<List<PremiumPlan>>(emptyList())
    val plans: StateFlow<List<PremiumPlan>> = _plans.asStateFlow()

    private val _isLoadingProducts = MutableStateFlow(false)
    val isLoadingProducts: StateFlow<Boolean> = _isLoadingProducts.asStateFlow()

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private val _event = MutableStateFlow<BillingEvent?>(null)
    val event: StateFlow<BillingEvent?> = _event.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        _operationInProgress.value = false

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(
                    purchases = purchases.orEmpty(),
                    emitPurchaseEvent = true,
                    emitRestoreEvent = false
                )
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _event.value = BillingEvent.PURCHASE_CANCELED
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
            }

            else -> {
                Log.w(TAG, "Purchase update failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                _event.value = BillingEvent.ERROR
            }
        }
    }

    fun initialize(context: Context) {
        if (billingClient != null) return

        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPremium.value = prefs.getBoolean(KEY_PREMIUM, false)
        _entitlementReady.value = false

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        ensureConnected {
            refreshPurchases()
            refreshProductDetails()
        }
    }

    fun clearEvent() {
        _event.value = null
    }

    fun refreshProductDetails() {
        val client = billingClient ?: return
        if (_isLoadingProducts.value) return

        ensureConnected {
            _isLoadingProducts.value = true

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()

            client.queryProductDetailsAsync(params) { billingResult, result ->
                _isLoadingProducts.value = false

                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Product query failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    latestProductDetails = null
                    offerTokens.clear()
                    _plans.value = emptyList()
                    return@queryProductDetailsAsync
                }

                val productDetails = result.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                latestProductDetails = productDetails
                offerTokens.clear()

                if (productDetails == null) {
                    _plans.value = emptyList()
                    return@queryProductDetailsAsync
                }

                val availablePlans = mutableListOf<PremiumPlan>()
                val offers = productDetails.subscriptionOfferDetails.orEmpty()

                listOf(PLAN_6_MONTHS, PLAN_12_MONTHS).forEach { basePlanId ->
                    // offerId == null identifies the normal base-plan purchase option,
                    // rather than a trial or promotional offer.
                    val offer = offers.firstOrNull {
                        it.basePlanId == basePlanId && it.offerId == null
                    } ?: offers.firstOrNull { it.basePlanId == basePlanId }

                    if (offer != null) {
                        val pricingPhase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                        if (pricingPhase != null) {
                            offerTokens[basePlanId] = offer.offerToken
                            availablePlans.add(
                                PremiumPlan(
                                    basePlanId = basePlanId,
                                    formattedPrice = pricingPhase.formattedPrice,
                                    billingPeriod = pricingPhase.billingPeriod
                                )
                            )
                        }
                    }
                }

                _plans.value = availablePlans
            }
        }
    }

    fun refreshPurchases(userInitiatedRestore: Boolean = false) {
        val client = billingClient ?: return
        if (isPurchaseQueryInFlight) return

        ensureConnected {
            if (isPurchaseQueryInFlight) return@ensureConnected
            isPurchaseQueryInFlight = true
            if (userInitiatedRestore) _operationInProgress.value = true

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            client.queryPurchasesAsync(params) { billingResult, purchases ->
                isPurchaseQueryInFlight = false
                if (userInitiatedRestore) _operationInProgress.value = false

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(
                        purchases = purchases,
                        emitPurchaseEvent = false,
                        emitRestoreEvent = userInitiatedRestore
                    )
                } else {
                    Log.w(TAG, "Purchase query failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    // Keep entitlement unknown on a failed query. In particular, do not
                    // show ads just because Google Play was temporarily unreachable.
                    if (userInitiatedRestore) {
                        _event.value = BillingEvent.BILLING_UNAVAILABLE
                    }
                }
            }
        }
    }

    fun restorePurchases() {
        clearEvent()
        refreshPurchases(userInitiatedRestore = true)
    }

    fun launchPurchase(activity: Activity, basePlanId: String) {
        clearEvent()

        if (_isPremium.value) {
            _event.value = BillingEvent.RESTORED
            return
        }

        val client = billingClient
        val productDetails = latestProductDetails
        val offerToken = offerTokens[basePlanId]

        if (client == null || !client.isReady) {
            _event.value = BillingEvent.BILLING_UNAVAILABLE
            ensureConnected { refreshProductDetails() }
            return
        }

        if (productDetails == null || offerToken.isNullOrBlank()) {
            _event.value = BillingEvent.PRODUCT_UNAVAILABLE
            refreshProductDetails()
            return
        }

        _operationInProgress.value = true

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _operationInProgress.value = false
            Log.w(TAG, "Unable to launch billing flow: ${result.responseCode} ${result.debugMessage}")
            _event.value = BillingEvent.ERROR
        }
    }

    private fun ensureConnected(onConnected: () -> Unit) {
        val client = billingClient ?: return
        if (client.isReady) {
            onConnected()
            return
        }

        pendingConnectedActions.add(onConnected)
        if (isConnecting) return
        isConnecting = true

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val actions = pendingConnectedActions.toList()
                    pendingConnectedActions.clear()
                    actions.forEach { action -> action() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    pendingConnectedActions.clear()
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                // Automatic service reconnection is enabled. Keep pending entitlement
                // unknown until a later successful Play query.
            }
        })
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        emitPurchaseEvent: Boolean,
        emitRestoreEvent: Boolean
    ) {
        val premiumPurchases = purchases.filter { purchase ->
            purchase.products.contains(PRODUCT_ID)
        }

        val completedPurchases = premiumPurchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val hasPendingPurchase = premiumPurchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        val premiumActive = completedPurchases.isNotEmpty()
        updatePremiumState(premiumActive)
        _entitlementReady.value = true
        appContext?.let { AdHelper.syncForEntitlement(it) }

        completedPurchases.forEach { purchase ->
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }

        if (emitPurchaseEvent) {
            _event.value = when {
                premiumActive -> BillingEvent.PURCHASE_COMPLETED
                hasPendingPurchase -> BillingEvent.PURCHASE_PENDING
                else -> BillingEvent.ERROR
            }
        } else if (emitRestoreEvent) {
            _event.value = if (premiumActive) {
                BillingEvent.RESTORED
            } else {
                BillingEvent.NOTHING_TO_RESTORE
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Purchase acknowledgement failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
        }
    }

    private fun updatePremiumState(active: Boolean) {
        _isPremium.value = active
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_PREMIUM, active)
            ?.apply()
    }
}
