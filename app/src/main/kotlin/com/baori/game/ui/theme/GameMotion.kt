package com.baori.game.ui.theme

import androidx.compose.runtime.Immutable
import com.baori.game.engine.Easings

/**
 * Motion tokens (design doc §3 "Motion and Transition Extensions" — every
 * state change eased, never linear; PRD §9 requires easing on every camera
 * rotation and character move).
 *
 * All durations in milliseconds. When [reduceMotion] is on (PRD §9), screen
 * code multiplies by [durationScale] — which collapses decorative motion to
 * near-instant while keeping gameplay-critical transitions legible.
 */
@Immutable
data class GameMotion(
    val reduceMotion: Boolean = false,
) {

    /** Camera snap after a drag release. */
    val snapDurationMs: Long = if (reduceMotion) 0L else 260L

    /** The girl walking one segment. */
    val stepDurationMs: Long = if (reduceMotion) 0L else 420L

    /** Mural fade between levels. */
    val muralFadeMs: Long = if (reduceMotion) 60L else 900L

    /** Paywall / dialog entrance. */
    val dialogEnterMs: Long = if (reduceMotion) 0L else 220L

    /** Pressed-state scale feedback. */
    val pressMs: Long = if (reduceMotion) 0L else 90L

    /** Apply this to any duration to honor the reduce-motion setting. */
    fun scaled(durationMs: Long): Long =
        if (reduceMotion) 0L else durationMs

    /** Progress mapper pairing a duration with the house ease-out curve. */
    fun easedProgress(elapsedMs: Long, durationMs: Long): Float =
        Easings.easeOutCubic(Easings.progress(elapsedMs, durationMs))

    companion object {
        /** Single easing vocabulary for the whole game (design doc §3). */
        val EASE_OUT: (Float) -> Float = Easings::easeOutCubic
        val EASE_IN_OUT: (Float) -> Float = Easings::easeInOutCubic
    }
}
