package com.baori.game.data.model

import kotlinx.serialization.Serializable

/**
 * Level data model (PRD §7.1: "Level data: Kotlin data classes, loaded from
 * bundled JSON in assets/levels/. No hardcoded level geometry in
 * Composables").
 *
 * The JSON contract is intentionally small and flat so level iteration on
 * Day 2/3 is a matter of editing text, not code.
 */
@Serializable
data class Level(
    /** Stable id, e.g. "level1" — matches the JSON filename. */
    val id: String,
    /** Sort order / display number. */
    val order: Int,
    /** Free levels are playable without the `full_journey` entitlement. */
    val free: Boolean,
    /** Camera angles (degrees) at which paths align — the puzzle's "answer". */
    val goalAnglesDeg: List<Float>,
    /** How far (degrees) the current angle may sit from a goal and still align. */
    val toleranceDeg: Float = 4f,
    /** Drag increments snap to this grid when no goal captures the release. */
    val snapStepDeg: Float = 15f,
    /** Initial camera angle when the level starts. */
    val startAngleDeg: Float = 0f,
    /** Water-tilt config; null for levels 1–3 (PRD §6). */
    val water: WaterConfig? = null,
    /** The walkable geometry. */
    val segments: List<PathSegment>,
    /** Where the girl starts. */
    val startNode: String,
    /** The node that completes the level. */
    val goalNode: String,
    /** Mural shown after completing this level, if any (PRD §6.1). */
    val muralAfter: String? = null,
)

/**
 * One walkable piece of the stepwell. `a` and `b` are node ids; the segment
 * only becomes real (walkable) while the camera sits inside this segment's
 * alignment window.
 */
@Serializable
data class PathSegment(
    val id: String,
    val a: String,
    val b: String,
    /** Goal angle (degrees) at which this segment "exists" on screen. */
    val alignsAtDeg: Float,
    /** Half-width of the alignment window in degrees. */
    val toleranceDeg: Float,
    /** 3D start point in well units. */
    val start: Vec3,
    /** 3D end point in well units. */
    val end: Vec3,
)

/**
 * Water-tilt parameters for levels 4/5. Rotation does not just reveal paths —
 * it raises and lowers the remembered water, drowning some stairs and
 * revealing others (PRD §5.1 "the one twist").
 */
@Serializable
data class WaterConfig(
    /** Camera angle at which the water peaks. */
    val fullWaterAngleDeg: Float,
    /** Angular half-width of the rise-and-fall. */
    val spanDeg: Float = 60f,
    /** Water height at the peak, in well units. */
    val maxHeightUnits: Float = 1f,
)

/** 3D point in well units, shared with the projection engine. */
@Serializable
data class Vec3(val x: Float, val y: Float, val z: Float)

// ---- JSON envelope ---------------------------------------------------------

@Serializable
data class LevelIndex(
    val levels: List<LevelRef>,
) {
    @Serializable
    data class LevelRef(
        val id: String,
        val file: String,
        val free: Boolean,
    )
}

// ---- Helpers used by the engine -------------------------------------------

/**
 * Returns the goal angle this segment is currently satisfied by, or null when
 * the camera angle sits outside its tolerance window. Angles are compared on
 * the shortest arc so 359° and 1° are neighbors.
 */
fun PathSegment.alignsWithin(angleDeg: Float, defaultToleranceDeg: Float): Float? {
    val tol = if (toleranceDeg > 0f) toleranceDeg else defaultToleranceDeg
    val dist = kotlin.math.abs(
        com.baori.game.engine.ProjectionMath.shortestArcDeg(angleDeg, alignsAtDeg),
    )
    return if (dist <= tol) alignsAtDeg else null
}
