package com.baori.game.data.billing

import android.app.Activity
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * RevenueCat-backed implementation of [BillingService] (PRD §8).
 *
 * Development and the submission video run against RevenueCat's **Test
 * Store** with a Test API key (PRD §3, §8.1): no App Store Connect or Play
 * Console setup is needed, and success/cancel/error paths are all
 * deterministic. For a production build, supply a real Android API key via
 * local.properties — no code changes (see README.md).
 *
 * The SDK is configured once in [com.baori.game.BaoriApplication]; this class
 * only wraps [Purchases] calls behind the app-owned interface, so no UI or
 * ViewModel code ever touches the SDK directly (PRD §7.3).
 *
 * The SDK exposes callback-based APIs; each is bridged to suspending calls
 * with [suspendCancellableCoroutine] so ViewModels stay coroutine-shaped.
 */
class RevenueCatBillingService(
    private val purchases: Purchases,
) : BillingService {

    /** Supplies the foreground Activity for the purchase dialog. */
    var activityProvider: (() -> Activity?)? = null

    private val _isFullJourneyUnlocked = MutableStateFlow(false)

    /**
     * Store products from the last successful offering load, by product id.
     * `PurchaseParams` needs the SDK's [StoreProduct] object, while the
     * app-owned [BillingService.ProductPackage] stays SDK-free — this map is
     * the bridge, refreshed on every [loadUnlockPackage].
     */
    private var loadedProductsById: Map<String, StoreProduct> = emptyMap()

    override val isFullJourneyUnlocked: StateFlow<Boolean> =
        _isFullJourneyUnlocked

    override val isLive: Boolean = true

    init {
        // Prime unlock state from the cached CustomerInfo without blocking
        // construction, then keep it reactive (PRD §8.2 step 6): a listener
        // pushes updates so a restored purchase shows up without a restart.
        purchases.getCustomerInfo(
            CacheFetchPolicy.CACHED_OR_FETCHED,
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(info: CustomerInfo) {
                    _isFullJourneyUnlocked.value = hasEntitlement(info)
                }

                override fun onError(error: PurchasesError) = Unit
            },
        )
        purchases.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info ->
                _isFullJourneyUnlocked.value = hasEntitlement(info)
            }
    }

    override suspend fun loadUnlockPackage(): BillingService.ProductPackage? {
        val offerings = awaitOfferings() ?: return null
        // One offering, one package (PRD §8.1). Fall back to the current
        // offering when the id was renamed in the dashboard.
        val offering = offerings.getOffering(BillingService.OFFERING_ID_DEFAULT)
            ?: offerings.current
            ?: return null
        val pkg = offering.availablePackages.firstOrNull() ?: return null
        val product = pkg.product
        loadedProductsById = offering.availablePackages
            .associate { it.product.id to it.product }
        return BillingService.ProductPackage(
            priceText = product.price.formatted,
            productId = product.id,
            productTitle = product.title,
        )
    }

    override suspend fun purchase(pkg: BillingService.ProductPackage): BillingService.PurchaseResult {
        val activity = activityProvider?.invoke()
            ?: return BillingService.PurchaseResult.Error(
                "The purchase dialog needs the app in the foreground. Try again.",
            )
        // PurchaseParams requires the SDK's StoreProduct; resolve it from the
        // last offering load (the paywall always loads before purchasing).
        val product = loadedProductsById[pkg.productId]
            ?: return BillingService.PurchaseResult.Error(
                "The unlock package hasn't finished loading. Try again.",
            )
        val params = PurchaseParams.Builder(activity, product).build()
        return suspendCancellableCoroutine { continuation ->
            purchases.purchase(
                params,
                object : PurchaseCallback {
                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        val result = if (userCancelled) {
                            BillingService.PurchaseResult.Cancelled
                        } else {
                            BillingService.PurchaseResult.Error(error.message)
                        }
                        continuation.resume(result)
                    }

                    override fun onCompleted(storeTransaction: com.revenuecat.purchases.models.StoreTransaction, customerInfo: CustomerInfo) {
                        _isFullJourneyUnlocked.value = hasEntitlement(customerInfo)
                        continuation.resume(BillingService.PurchaseResult.Success)
                    }
                },
            )
        }
    }

    override suspend fun restorePurchases(): BillingService.PurchaseResult =
        suspendCancellableCoroutine { continuation ->
            purchases.restorePurchases(
                object : ReceiveCustomerInfoCallback {
                    override fun onReceived(info: CustomerInfo) {
                        _isFullJourneyUnlocked.value = hasEntitlement(info)
                        continuation.resume(BillingService.PurchaseResult.Success)
                    }

                    override fun onError(error: PurchasesError) {
                        continuation.resume(
                            BillingService.PurchaseResult.Error(error.message),
                        )
                    }
                },
            )
        }

    /** Offers or null on failure — never throws into the UI (PRD §8.2). */
    private suspend fun awaitOfferings(): Offerings? =
        suspendCancellableCoroutine { continuation ->
            purchases.getOfferings(
                object : ReceiveOfferingsCallback {
                    override fun onReceived(offerings: Offerings) {
                        continuation.resume(offerings)
                    }

                    override fun onError(error: PurchasesError) {
                        continuation.resume(null)
                    }
                },
            )
        }

    private fun hasEntitlement(info: CustomerInfo?): Boolean =
        info?.entitlements
            ?.get(BillingService.ENTITLEMENT_FULL_JOURNEY)
            ?.isActive == true
}
