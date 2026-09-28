package com.baori.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baori.game.data.BaoriPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the accessibility and presentation settings (PRD §9):
 * reduce motion, ambient mute, and light/dark theme override.
 *
 * A tiny ViewModel, but keeping settings out of screens means the toggles
 * persist through DataStore and every screen reacts through one shared
 * state object.
 */
class SettingsViewModel(
    private val preferences: BaoriPreferences,
) : ViewModel() {

    data class SettingsUiState(
        val reduceMotion: Boolean = false,
        val muted: Boolean = false,
        val themeOverride: String = BaoriPreferences.THEME_SYSTEM,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.reduceMotion,
        preferences.muted,
        preferences.themeOverride,
    ) { reduceMotion, muted, theme ->
        SettingsUiState(reduceMotion, muted, theme)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferences.setReduceMotion(enabled) }
    }

    fun setMuted(muted: Boolean) {
        viewModelScope.launch { preferences.setMuted(muted) }
    }

    fun setThemeOverride(theme: String) {
        viewModelScope.launch { preferences.setThemeOverride(theme) }
    }
}
