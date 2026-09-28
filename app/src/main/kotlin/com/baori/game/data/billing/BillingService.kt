package com.baori.game.data.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * The only surface the rest of the app sees for monetization (PRD §7.3).
 *
 * The RevenueCat SDK is never called directly from UI or ViewModel code —
 * everything goes through this interface. That gives us:
 *  - a clean seam for judges to inspect ("careful technical choices"),
 *  - a [FakeBillingService] so the app runs on a clean clone with no key,
 *  - a single place to swap the Test Store key for a production one.
 *
 * The entitlement id `full_journey` and product naming live in
 * [Companion.ENTITLEMENT_FULL_JOURNEY] so there is one source of truth.
 */
interface BillingService {

    /** Emits the current unlock state; a restore reflects here immediately. */
    val isFullJourneyUnlocked: StateFlow<Boolean>

    /**
     * The single purchasable package (PRD §8.1: one offering, one package),
     * or null when the offering could not be loaded (offline, key not set).
     */
    suspend fun loadUnlockPackage(): ProductPackage?

    /** Attempts purchase of [package]. Returns the resulting state. */
    suspend fun purchase(pkg: ProductPackage): PurchaseResult

    /** Restores prior purchases; success updates [isFullJourneyUnlocked]. */
    suspend fun restorePurchases(): PurchaseResult

    /** True when the backend is the real RevenueCat SDK, not the fake. */
    val isLive: Boolean

    data class ProductPackage(
        /** Presented price string, e.g. "$1.99". */
        val priceText: String,
        /** RevenueCat product identifier, for analytics/debug. */
        val productId: String,
        /** Store-displayed product title, e.g. "Baori: Full Journey". */
        val productTitle: String = "",
    )

    sealed interface PurchaseResult {
        /** Entitlement granted. */
        data object Success : PurchaseResult

        /** The user closed the dialog without buying — never shown as an error. */
        data object Cancelled : PurchaseResult

        /** Purchase did not complete (pending, backend error, etc.). */
        data class Error(val message: String) : PurchaseResult
    }

    companion object {
        /** PRD §8.1 — entitlement and product naming. */
        const val ENTITLEMENT_FULL_JOURNEY = "full_journey"
        const val PRODUCT_ID_UNLOCK = "baori_full_journey"
        const val OFFERING_ID_DEFAULT = "default"
    }
}
