package com.baori.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baori.game.data.BaoriPreferences
import com.baori.game.data.LevelRepository
import com.baori.game.data.billing.BillingService
import com.baori.game.data.model.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Main menu state: which levels exist, which are playable, and whether the
 * full journey has been purchased (PRD §6 free/paid gating).
 *
 * Gating rule, in one place: a level is playable when it is free **or** the
 * `full_journey` entitlement is active, and the player has reached it.
 * `needsPurchase` drives the lock chip on the menu row — the paywall itself
 * only ever appears at the end of Level 3 (PRD §8.1, no dark patterns).
 */
class MenuViewModel(
    levelRepository: LevelRepository,
    preferences: BaoriPreferences,
    billingService: BillingService,
) : ViewModel() {

    data class MenuItem(
        val level: Level,
        val playable: Boolean,
        val needsPurchase: Boolean,
    )

    data class MenuUiState(
        val items: List<MenuItem> = emptyList(),
        val fullJourneyUnlocked: Boolean = false,
        /** The level the "continue" affordance points at. */
        val continueLevelOrder: Int = 1,
    )

    val uiState: StateFlow<MenuUiState> = combine(
        // Asset file IO and JSON parsing stay off the main thread.
        flow { emit(levelRepository.loadAll()) }.flowOn(Dispatchers.IO),
        preferences.highestUnlockedLevel,
        billingService.isFullJourneyUnlocked,
    ) { levels, highestUnlocked, isEntitled ->
        MenuUiState(
            items = levels.map { level ->
                val reached = level.order <= highestUnlocked
                val entitlementOk = level.free || isEntitled
                MenuItem(
                    level = level,
                    playable = reached && entitlementOk,
                    needsPurchase = !level.free && !isEntitled,
                )
            },
            fullJourneyUnlocked = isEntitled,
            continueLevelOrder = highestUnlocked.coerceAtMost(levels.maxOfOrNull { it.order } ?: 1),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MenuUiState())
}
