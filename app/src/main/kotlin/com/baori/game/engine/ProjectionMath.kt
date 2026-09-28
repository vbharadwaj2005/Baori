package com.baori.game.engine

import com.baori.game.data.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Orthographic projection math for the perspective-lock illusion.
 *
 * Baori deliberately avoids a 3D engine (PRD §7.4): the "impossible geometry"
 * look is reproduced with a 2D projection of a stepwell built from
 * axis-aligned segments. Two steps:
 *
 *  1. [rotateAroundY] — the well turns around its vertical axis. This is the
 *     player's only verb.
 *  2. [project] — an orthographic "looking down into the well" projection:
 *     height (y) and depth (z') both contribute to screen y, weighted by the
 *     tilt angle. Depth (z') is otherwise dropped, and only feeds draw order
 *     via [depthOf] (painter's algorithm).
 *
 * Because depth leaks into screen y through the tilt, rotating the well
 * visibly rearranges the geometry on screen — which is exactly the
 * perspective-lock illusion: edges align only from certain angles.
 *
 * All functions are pure; nothing here touches Android classes, so the math
 * runs identically on the JVM in unit tests. [Vec3] is the shared geometry
 * vocabulary from the level data model.
 */
object ProjectionMath {

    const val TWO_PI: Float = 2f * PI.toFloat()

    /** How far the camera leans over the well rim. 35° reads as "standing at
     *  the edge, looking down" while keeping vertical walls visible. */
    const val DEFAULT_TILT_DEG: Float = 35f
    val DEFAULT_TILT_RAD: Float =
        Math.toRadians(DEFAULT_TILT_DEG.toDouble()).toFloat()

    /** A point in 2D Canvas space (px). */
    data class Vec2(val x: Float, val y: Float)

    /**
     * Rotates [p] around the vertical (Y) axis by [angleRadians].
     * Positive angles rotate the well clockwise as seen from above, which
     * matches the drag direction used by [RotationController].
     */
    fun rotateAroundY(p: Vec3, angleRadians: Float): Vec3 {
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        return Vec3(
            x = p.x * c + p.z * s,
            y = p.y,
            z = -p.x * s + p.z * c,
        )
    }

    /**
     * Projects an already-rotated 3D point to 2D canvas coordinates.
     *
     * canvasX = center.x + x' · scale
     * canvasY = center.y − (y · cos(tilt) + z' · sin(tilt)) · scale
     *
     * Higher points (larger y) and farther points (larger z', the far wall of
     * the well) both move up the screen, exactly as they appear when looking
     * down into a stepwell.
     */
    fun project(
        rotated: Vec3,
        center: Vec2,
        scale: Float,
        tiltRad: Float = DEFAULT_TILT_RAD,
    ): Vec2 {
        val c = cos(tiltRad)
        val s = sin(tiltRad)
        return Vec2(
            x = center.x + rotated.x * scale,
            y = center.y - (rotated.y * c + rotated.z * s) * scale,
        )
    }

    /** Convenience: rotate then project in one call. */
    fun project(
        p: Vec3,
        angleRadians: Float,
        center: Vec2,
        scale: Float,
        tiltRad: Float = DEFAULT_TILT_RAD,
    ): Vec2 = project(rotateAroundY(p, angleRadians), center, scale, tiltRad)

    /** Screen-space depth used for painter's-algorithm draw ordering. */
    fun depthOf(rotated: Vec3): Float = rotated.z

    /** Linear interpolation between two 3D points; used for hero movement. */
    fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 = Vec3(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t,
        z = a.z + (b.z - a.z) * t,
    )

    /** Wraps any angle (degrees) into [0, 360). */
    fun normalizeDeg(deg: Float): Float {
        val m = deg % 360f
        return if (m < 0f) m + 360f else m
    }

    /** Wraps any angle (radians) into [0, 2π). */
    fun normalizeRad(rad: Float): Float {
        val m = rad % TWO_PI
        return if (m < 0f) m + TWO_PI else m
    }

    /**
     * Shortest signed distance in degrees from [from] to [to], in (-180, 180].
     * Used by the snap logic so a drag never spins the long way round.
     */
    fun shortestArcDeg(from: Float, to: Float): Float {
        var d = normalizeDeg(to) - normalizeDeg(from)
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Axis-aligned bounding box of projected points, used by the renderer to
     * fit the whole well on screen at any rotation angle.
     */
    fun bounds(points: List<Vec2>): Pair<Vec2, Vec2> {
        require(points.isNotEmpty()) { "bounds() needs at least one point" }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            minX = min(minX, p.x); minY = min(minY, p.y)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        return Vec2(minX, minY) to Vec2(maxX, maxY)
    }
}
