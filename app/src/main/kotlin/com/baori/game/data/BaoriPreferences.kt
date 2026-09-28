package com.baori.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.baoriDataStore by preferencesDataStore(name = "baori")

/**
 * Local persistence for progress and settings (PRD §7.1 "Persistence:
 * DataStore for progress (which level unlocked, purchase state cache)" and
 * §9 accessibility toggles).
 *
 * Purchase state itself is owned by RevenueCat's CustomerInfo listener; the
 * cached entitlement here is only a fast pre-render hint so the paywall never
 * flashes for an already-unlocked player.
 */
class BaoriPreferences(private val context: Context) {

    private object Keys {
        val highestUnlocked = intPreferencesKey("highest_unlocked_level")
        val entitledCached = booleanPreferencesKey("full_journey_cached")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val muted = booleanPreferencesKey("audio_muted")
        val themeOverride = stringPreferencesKey("theme_override")
    }

    /** 1-based index of the furthest playable level. */
    val highestUnlockedLevel: Flow<Int> =
        context.baoriDataStore.data.map { it[Keys.highestUnlocked] ?: 1 }

    /** Cached `full_journey` entitlement for instant UI on cold start. */
    val fullJourneyCached: Flow<Boolean> =
        context.baoriDataStore.data.map { it[Keys.entitledCached] ?: false }

    /** Accessibility: shorten/remove rotation easing (PRD §9). */
    val reduceMotion: Flow<Boolean> =
        context.baoriDataStore.data.map { it[Keys.reduceMotion] ?: false }

    /** Ambient audio mute toggle (PRD §9). */
    val muted: Flow<Boolean> =
        context.baoriDataStore.data.map { it[Keys.muted] ?: false }

    /** "system" | "light" | "dark" — the player may override the default. */
    val themeOverride: Flow<String> =
        context.baoriDataStore.data.map { it[Keys.themeOverride] ?: THEME_SYSTEM }

    suspend fun setHighestUnlockedLevel(level: Int) {
        context.baoriDataStore.edit { prefs ->
            val current = prefs[Keys.highestUnlocked] ?: 1
            if (level > current) prefs[Keys.highestUnlocked] = level
        }
    }

    suspend fun setFullJourneyCached(entitled: Boolean) {
        context.baoriDataStore.edit { it[Keys.entitledCached] = entitled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.baoriDataStore.edit { it[Keys.reduceMotion] = enabled }
    }

    suspend fun setMuted(muted: Boolean) {
        context.baoriDataStore.edit { it[Keys.muted] = muted }
    }

    suspend fun setThemeOverride(theme: String) {
        context.baoriDataStore.edit { it[Keys.themeOverride] = theme }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
