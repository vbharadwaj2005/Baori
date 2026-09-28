package com.baori.game.engine

import com.baori.game.data.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Day 1 engine tests: the orthographic projection is the whole perspective-
 * lock illusion (PRD §5.1, §7.4), so its math is pinned before any content
 * work (PRD §13 "de-risk this first").
 */
class ProjectionMathTest {

    private val center = ProjectionMath.Vec2(100f, 100f)
    private val unit = Vec3(1f, 0f, 0f)

    @Test
    fun `rotation by zero leaves points unchanged`() {
        val rotated = ProjectionMath.rotateAroundY(unit, 0f)
        assertEquals(1f, rotated.x, 1e-5f)
        assertEquals(0f, rotated.y, 1e-5f)
        assertEquals(0f, rotated.z, 1e-5f)
    }

    @Test
    fun `quarter turn maps x onto z with preserved length`() {
        val quarter = Math.toRadians(90.0).toFloat()
        val rotated = ProjectionMath.rotateAroundY(unit, quarter)
        // cos 90 ~ 0, sin 90 = 1.
        assertEquals(0f, rotated.x, 1e-5f)
        assertEquals(-1f, rotated.z, 1e-5f)
        assertEquals(1f, kotlin.math.sqrt(rotated.x * rotated.x + rotated.z * rotated.z), 1e-5f)
    }

    @Test
    fun `rotation preserves radius at arbitrary angles`() {
        val p = Vec3(0.6f, 0.7f, 0.8f)
        val angle = Math.toRadians(37.0).toFloat()
        val rotated = ProjectionMath.rotateAroundY(p, angle)
        val before = kotlin.math.sqrt(p.x * p.x + p.z * p.z)
        val after = kotlin.math.sqrt(rotated.x * rotated.x + rotated.z * rotated.z)
        assertEquals(before, after, 1e-5f)
        assertEquals(p.y, rotated.y, 1e-6f)
    }

    @Test
    fun `rotation is invertible`() {
        val p = Vec3(0.5f, 0.25f, -0.75f)
        val angle = Math.toRadians(123.0).toFloat()
        val rotated = ProjectionMath.rotateAroundY(p, angle)
        val back = ProjectionMath.rotateAroundY(rotated, -angle)
        assertEquals(p.x, back.x, 1e-5f)
        assertEquals(p.y, back.y, 1e-5f)
        assertEquals(p.z, back.z, 1e-5f)
    }

    @Test
    fun `project centers the origin at the canvas center`() {
        val projected = ProjectionMath.project(Vec3(0f, 0f, 0f), center, 10f)
        assertEquals(center.x, projected.x, 1e-5f)
        assertEquals(center.y, projected.y, 1e-5f)
    }

    @Test
    fun `project uses x directly and folds height and depth into y`() {
        // Straight out of the doc comment: canvasY = center.y - (y·cosT + z'·sinT)·scale.
        val tilt = ProjectionMath.DEFAULT_TILT_RAD
        val p = Vec3(2f, 1f, 3f)
        val projected = ProjectionMath.project(p, center, 10f)
        assertEquals(center.x + 20f, projected.x, 1e-4f)
        assertEquals(
            center.y - (1f * cos(tilt) + 3f * sin(tilt)) * 10f,
            projected.y,
            1e-4f,
        )
    }

    @Test
    fun `higher points render above lower ones`() {
        val top = ProjectionMath.project(Vec3(0f, 1f, 0f), center, 10f)
        val bottom = ProjectionMath.project(Vec3(0f, -1f, 0f), center, 10f)
        assert(top.y < bottom.y)
    }

    @Test
    fun `combined convenience overload matches rotate-then-project`() {
        val p = Vec3(1f, 2f, 3f)
        val angle = Math.toRadians(25.0).toFloat()
        val oneShot = ProjectionMath.project(p, angle, center, 5f)
        val twoStep = ProjectionMath.project(
            ProjectionMath.rotateAroundY(p, angle),
            center,
            5f,
        )
        assertEquals(twoStep.x, oneShot.x, 1e-5f)
        assertEquals(twoStep.y, oneShot.y, 1e-5f)
    }

    @Test
    fun `normalizeDeg wraps into half-open 0 to 360`() {
        assertEquals(0f, ProjectionMath.normalizeDeg(0f), 1e-5f)
        assertEquals(359.5f, ProjectionMath.normalizeDeg(-0.5f), 1e-5f)
        assertEquals(10f, ProjectionMath.normalizeDeg(370f), 1e-5f)
        assertEquals(0f, ProjectionMath.normalizeDeg(720f), 1e-5f)
    }

    @Test
    fun `shortestArcDeg takes the short way and stays signed`() {
        assertEquals(90f, ProjectionMath.shortestArcDeg(350f, 80f), 1e-5f)
        assertEquals(-90f, ProjectionMath.shortestArcDeg(80f, 350f), 1e-5f)
        assertEquals(0f, ProjectionMath.shortestArcDeg(45f, 45f), 1e-5f)
        assertEquals(180f, ProjectionMath.shortestArcDeg(0f, 180f), 1e-5f)
        assertEquals(1f, ProjectionMath.shortestArcDeg(359f, 0f), 1e-5f)
    }

    @Test
    fun `lerp walks a straight line between two points`() {
        val a = Vec3(0f, 0f, 0f)
        val b = Vec3(2f, -4f, 6f)
        val mid = ProjectionMath.lerp(a, b, 0.5f)
        assertEquals(1f, mid.x, 1e-6f)
        assertEquals(-2f, mid.y, 1e-6f)
        assertEquals(3f, mid.z, 1e-6f)
        assertEquals(b.x, ProjectionMath.lerp(a, b, 1f).x, 1e-6f)
        assertEquals(a.x, ProjectionMath.lerp(a, b, 0f).x, 1e-6f)
    }

    @Test
    fun `bounds spans all points`() {
        val points = listOf(
            ProjectionMath.Vec2(-10f, 5f),
            ProjectionMath.Vec2(30f, -7f),
            ProjectionMath.Vec2(0f, 12f),
        )
        val (min, max) = ProjectionMath.bounds(points)
        assertEquals(-10f, min.x, 1e-5f)
        assertEquals(-7f, min.y, 1e-5f)
        assertEquals(30f, max.x, 1e-5f)
        assertEquals(12f, max.y, 1e-5f)
    }
}
