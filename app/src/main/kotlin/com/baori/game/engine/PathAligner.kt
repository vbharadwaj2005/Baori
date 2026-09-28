package com.baori.game.engine

import com.baori.game.data.model.alignsWithin
import kotlin.math.abs

/**
 * Decides which path segments are currently "connected" (PRD §5.1, §7.3).
 *
 * A segment is walkable when its screen-projected midpoint lies inside the
 * tolerance window of one of the level's goal angles — that is the moment the
 * perspective-lock illusion makes the geometry "line up" and the path become
 * real. Everything else in the mechanic (animation, rendering) hangs off this
 * pure, unit-testable decision.
 */
class PathAligner(
    private val toleranceDeg: Float = DEFAULT_TOLERANCE_DEG,
) {

    /**
     * Result of evaluating a level at one camera angle.
     *
     * @property walkableIds ids of segments whose alignment window contains
     *   the current angle (drawn solid; the girl may walk them)
     * @property activeGoalDeg the goal angle currently satisfied, if any —
     *   used to drive the "snap glow" and haptic moment
     */
    data class Alignment(
        val walkableIds: Set<String>,
        val activeGoalDeg: Float?,
    )

    /**
     * @param segments every path segment defined by the level JSON
     * @param angleDeg current camera angle in degrees (any wrap value)
     */
    fun evaluate(
        segments: List<com.baori.game.data.model.PathSegment>,
        angleDeg: Float,
    ): Alignment {
        val walkable = mutableSetOf<String>()
        var activeGoal: Float? = null
        var bestArc = Float.MAX_VALUE

        for (segment in segments) {
            val goal = segment.alignsWithin(angleDeg, toleranceDeg) ?: continue
            walkable += segment.id
            // Remember the tightest-satisfied goal (smallest shortest-arc
            // distance from the current angle) for glow/haptics.
            val arc = abs(ProjectionMath.shortestArcDeg(angleDeg, goal))
            if (activeGoal == null || arc < bestArc) {
                activeGoal = goal
                bestArc = arc
            }
        }
        return Alignment(walkableIds = walkable, activeGoalDeg = activeGoal)
    }

    companion object {
        /** Default alignment window; wide enough to feel generous, narrow
         *  enough that the "aha" stays earned. Tuned during Day 1 playtest. */
        const val DEFAULT_TOLERANCE_DEG: Float = 4f
    }
}
