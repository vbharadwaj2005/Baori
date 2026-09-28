package com.baori.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baori.game.data.billing.BillingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Paywall state and actions (PRD §8.2 flow, steps 2–6).
 *
 * All RevenueCat contact stays behind [BillingService]; this ViewModel only
 * ever sees app-owned types. Loading, error, and unlocked states are all
 * explicit so the screen never guesses.
 */
class PaywallViewModel(
    private val billingService: BillingService,
) : ViewModel() {

    data class PaywallUiState(
        val isLoading: Boolean = true,
        val priceText: String? = null,
        val isUnlocked: Boolean = false,
        val errorText: String? = null,
    )

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        // PRD §8.2 step 2: awaitOfferings -> fetch the single package.
        viewModelScope.launch {
            val pkg = billingService.loadUnlockPackage()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                priceText = pkg?.priceText,
                errorText = if (pkg == null) {
                    "The journey could not be fetched. Check your connection and try again."
                } else {
                    null
                },
            )
        }
        // PRD §8.2 step 6: entitlement changes reflect immediately — a
        // restored purchase unlocks the UI without a restart.
        viewModelScope.launch {
            billingService.isFullJourneyUnlocked.collect { unlocked ->
                _uiState.value = _uiState.value.copy(isUnlocked = unlocked)
            }
        }
    }

    /** PRD §8.2 step 4: purchase -> unlock -> proceed. */
    fun purchase() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorText = null)
            val pkg = billingService.loadUnlockPackage()
            val result = if (pkg != null) {
                billingService.purchase(pkg)
            } else {
                BillingService.PurchaseResult.Error("The journey could not be fetched.")
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorText = when (result) {
                    is BillingService.PurchaseResult.Success -> null
                    is BillingService.PurchaseResult.Cancelled -> null // never an error (PRD §8.3)
                    is BillingService.PurchaseResult.Error -> result.message
                },
            )
        }
    }

    /** PRD §8.2 step 5: restore, always available. */
    fun restore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorText = null)
            when (val result = billingService.restorePurchases()) {
                is BillingService.PurchaseResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is BillingService.PurchaseResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is BillingService.PurchaseResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorText = result.message,
                    )
                }
            }
        }
    }
}
