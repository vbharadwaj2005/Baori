package com.baori.game.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the camera angle for the perspective-lock mechanic (PRD §7.3).
 *
 * The raw angle is continuous while the player drags; on release the caller
 * asks [endDrag] for the snap destination (the closest level goal angle if it
 * is within capture range, otherwise the nearest step on the free-rotation
 * grid) and animates toward it with an ease-out curve — never linear motion
 * (PRD §9). [interpolatedAngle] supplies the in-between angles.
 *
 * Keeping this a plain class (no Android imports) lets the snap behavior be
 * unit-tested headlessly.
 */
class RotationController(
    initialAngleDeg: Float = 0f,
    /** Drag releases snap to this grid when no goal captures the release. */
    var snapStepDegrees: Float = 15f,
) {

    /** Snap target candidates for a level; empty means "nearest snap step". */
    val snapTargetsDeg = MutableStateFlow<List<Float>>(emptyList())

    private val _angleDeg = MutableStateFlow(ProjectionMath.normalizeDeg(initialAngleDeg))
    val angleDeg: StateFlow<Float> = _angleDeg.asStateFlow()

    /** True while the player is actively dragging. */
    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    /**
     * When true (accessibility "reduce motion", PRD §9), the caller skips
     * easing and lands snaps instantly. The controller only exposes the
     * intent; the screen decides how to animate.
     */
    var reduceMotion: Boolean = false

    fun setSnapTargets(targets: List<Float>) {
        snapTargetsDeg.value = targets.map { ProjectionMath.normalizeDeg(it) }
    }

    /** Adds a drag delta in degrees (positive = clockwise on screen). */
    fun dragBy(deltaDeg: Float) {
        _isDragging.value = true
        _angleDeg.value = ProjectionMath.normalizeDeg(_angleDeg.value + deltaDeg)
    }

    /**
     * Marks the end of a gesture and returns the angle the snap should land
     * on, without moving the camera — the caller animates from the current
     * angle to the returned target (or calls [snapTo] under reduce motion).
     */
    fun endDrag(): Float {
        _isDragging.value = false
        return nearestSnapTarget(_angleDeg.value)
    }

    /** Snaps directly to [angleDeg] (reduce motion, level start, tests). */
    fun snapTo(angleDeg: Float) {
        _isDragging.value = false
        _angleDeg.value = ProjectionMath.normalizeDeg(angleDeg)
    }

    /** Sets the angle exactly, mid-animation. Not a player-facing action. */
    fun setAngleDeg(angleDeg: Float) {
        _angleDeg.value = ProjectionMath.normalizeDeg(angleDeg)
    }

    /**
     * Chooses the snap destination for the current angle: the closest of the
     * level's goal angles if one is within [goalCaptureDeg], otherwise the
     * nearest multiple of [snapStepDegrees]. A goal only captures the snap if
     * it is meaningfully closer than the plain step grid, so free rotation
     * never feels sticky.
     */
    fun nearestSnapTarget(currentDeg: Float, goalCaptureDeg: Float = 10f): Float {
        val goals = snapTargetsDeg.value
        var best = roundToStep(currentDeg, snapStepDegrees)
        var bestDist = Float.MAX_VALUE
        for (goal in goals) {
            val dist = kotlin.math.abs(ProjectionMath.shortestArcDeg(currentDeg, goal))
            if (dist < bestDist) {
                bestDist = dist
                best = goal
            }
        }
        if (bestDist > goalCaptureDeg && goals.isNotEmpty()) {
            best = roundToStep(currentDeg, snapStepDegrees)
        }
        return ProjectionMath.normalizeDeg(best)
    }

    /**
     * Animation helper: the angle at easing progress [t] (0..1) when traveling
     * from [fromDeg] to [target] along the shortest arc. The caller supplies
     * the eased t (see [Easings.easeOutCubic]).
     */
    fun interpolatedAngle(fromDeg: Float, target: Float, t: Float): Float =
        ProjectionMath.normalizeDeg(
            fromDeg + ProjectionMath.shortestArcDeg(fromDeg, target) * t,
        )

    private fun roundToStep(value: Float, step: Float): Float =
        kotlin.math.round(value / step) * step

    companion object {
        const val FULL_TURN_DEG: Float = 360f
    }
}
