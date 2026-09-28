package com.baori.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme composition (design doc §3 — tokens bridge into the UI framework
 * through dynamic lookup, never literal values at call sites).
 *
 * Usage:
 * ```
 * BaoriTheme(reduceMotion = settings.reduceMotion) {
 *     Surface(colors = dustyColorScheme) { ... }
 * }
 * ```
 * and inside any composable: `BaoriTheme.tokens` / `BaoriTheme.motion`.
 */
@Immutable
data class BaoriTokens(
    val colors: GameColors,
    val isDark: Boolean,
)

val LocalBaoriTokens = staticCompositionLocalOf<BaoriTokens> {
    error("BaoriTheme not provided — wrap your UI in BaoriTheme")
}

val LocalBaoriMotion = staticCompositionLocalOf { GameMotion() }

/** Shorthand accessors used across the UI. */
object BaoriTheme {

    val colors: GameColors
        @Composable get() = LocalBaoriTokens.current.colors

    val motion: GameMotion
        @Composable get() = LocalBaoriMotion.current

    val isDark: Boolean
        @Composable get() = LocalBaoriTokens.current.isDark
}

/**
 * @param themeOverride one of [com.baori.game.data.BaoriPreferences.THEME_SYSTEM],
 *   THEME_LIGHT, THEME_DARK — the player's explicit choice wins over the OS.
 * @param reduceMotion accessibility toggle; every animation in the app reads
 *   [GameMotion.reduceMotion] through [LocalBaoriMotion] (PRD §9).
 */
@Composable
fun BaoriTheme(
    themeOverride: String = "system",
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeOverride) {
        com.baori.game.data.BaoriPreferences.THEME_LIGHT -> false
        com.baori.game.data.BaoriPreferences.THEME_DARK -> true
        else -> systemDark
    }
    val colors = if (isDark) WellRemembersColors else SunBakedColors

    CompositionLocalProvider(
        LocalBaoriTokens provides BaoriTokens(colors = colors, isDark = isDark),
        LocalBaoriMotion provides GameMotion(reduceMotion = reduceMotion),
        content = content,
    )
}
