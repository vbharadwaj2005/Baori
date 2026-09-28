package com.baori.game.data.billing

import com.baori.game.data.BaoriPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Deterministic stand-in for [BillingService] used when no RevenueCat key is
 * configured (clean clones, CI, previews). Success/cancel/error follow the
 * same shape as the Test Store so the Day 2 paywall UI can be built and
 * demoed before wiring a key (PRD §8.1 — Test Store needs no store setup;
 * this goes one step further and needs no key at all).
 *
 * It also writes through the cached entitlement so the fake exercises the
 * exact same unlock path as the real service.
 */
class FakeBillingService(
    private val preferences: BaoriPreferences,
    private val scope: CoroutineScope,
    /** Simulated dialog outcome, overridable by tests/debug toggles. */
    private var outcome: Outcome = Outcome.SUCCESS,
) : BillingService {

    enum class Outcome { SUCCESS, CANCELLED, ERROR }

    private val _isFullJourneyUnlocked = MutableStateFlow(false)

    override val isFullJourneyUnlocked: StateFlow<Boolean> = _isFullJourneyUnlocked

    override val isLive: Boolean = false

    override suspend fun loadUnlockPackage(): BillingService.ProductPackage? =
        BillingService.ProductPackage(
            priceText = "$1.99", // Placeholder price shown only in fake mode.
            productId = BillingService.PRODUCT_ID_UNLOCK,
            productTitle = "Baori: Full Journey",
        )

    override suspend fun purchase(pkg: BillingService.ProductPackage): BillingService.PurchaseResult =
        when (outcome) {
            Outcome.SUCCESS -> {
                _isFullJourneyUnlocked.value = true
                scope.launch { preferences.setFullJourneyCached(true) }
                BillingService.PurchaseResult.Success
            }
            Outcome.CANCELLED -> BillingService.PurchaseResult.Cancelled
            Outcome.ERROR -> BillingService.PurchaseResult.Error("Simulated purchase failure")
        }

    override suspend fun restorePurchases(): BillingService.PurchaseResult {
        _isFullJourneyUnlocked.value = true
        scope.launch { preferences.setFullJourneyCached(true) }
        return BillingService.PurchaseResult.Success
    }
}
