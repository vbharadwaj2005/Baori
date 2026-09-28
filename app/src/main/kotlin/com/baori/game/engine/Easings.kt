package com.baori.game.engine

import kotlin.math.sin

/**
 * Easing curves for every animated motion in Baori (PRD §9: "Easing (not
 * linear motion) on every camera rotation and character move").
 *
 * Kept as pure functions so the same curves shape snap animation, character
 * walking, and mural transitions identically.
 */
object Easings {

    /** Ease-out cubic: fast start, gentle landing. The default for snaps. */
    fun easeOutCubic(t: Float): Float {
        val p = 1f - t
        return 1f - p * p * p
    }

    /** Ease-in-out cubic: for longer camera travels between murals. */
    fun easeInOutCubic(t: Float): Float = when {
        t < 0.5f -> 4f * t * t * t
        else -> 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    /**
     * Gentle "breath" curve used for idle water shimmer and mural glow.
     * Pure sine, amplitude-scaled by the caller.
     */
    fun breathe(t: Float, cyclesPerSecond: Float = 0.5f): Float =
        sin(2f * Math.PI.toFloat() * cyclesPerSecond * t)

    /**
     * Maps a linear clock (milliseconds) to a 0..1 progress with the given
     * duration, clamped — the tiny helper every animator ends up writing.
     */
    fun progress(elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        return (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}
