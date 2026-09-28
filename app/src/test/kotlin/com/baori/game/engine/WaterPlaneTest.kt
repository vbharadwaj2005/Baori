package com.baori.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 1 engine tests: the water-tilt curve ships before its levels do
 * (PRD §6 — Level 4/5 consume it on Day 3; WaterPlane.kt: "Day 1 ships the
 * engine and its tests").
 */
class WaterPlaneTest {

    private fun plane(
        fullWaterAngleDeg: Float = 60f,
        spanDeg: Float = 60f,
        maxHeightY: Float = 1.0f,
        shaftBottomY: Float = -1.5f,
    ) = WaterPlane(
        WaterPlane.Config(
            fullWaterAngleDeg = fullWaterAngleDeg,
            spanDeg = spanDeg,
            maxHeightY = maxHeightY,
            shaftBottomY = shaftBottomY,
        ),
    )

    @Test
    fun `water peaks exactly at the full-water angle`() {
        val water = plane()
        assertEquals(1f, water.levelAt(60f), 1e-5f)
    }

    @Test
    fun `water is dry outside the span`() {
        val water = plane()
        assertEquals(0f, water.levelAt(0f), 1e-5f)
        // Antipodal to the peak is the farthest the span can reach — dry.
        assertEquals(0f, water.levelAt(180f), 1e-5f)
        // The span is a HALF-width: for a 60° peak the raised-cosine closes
        // exactly at 120° (one span either side of the peak), and beyond.
        assertEquals(0f, water.levelAt(120f), 1e-5f)
        assertEquals(0f, water.levelAt(121f), 1e-5f)
    }

    @Test
    fun `water rises symmetrically and wraps across zero`() {
        val water = plane()
        val rising = water.levelAt(45f)
        val falling = water.levelAt(75f)
        assertEquals(rising, falling, 1e-5f)

        // 5° from 60° the short way round is symmetrical either side.
        assertEquals(water.levelAt(55f), water.levelAt(65f), 1e-5f)

        // A span that actually crosses zero proves the shortest-arc rule:
        // peak 10° with a 30° half-width waters the arc [340°, 40°].
        val wrapping = plane(fullWaterAngleDeg = 10f, spanDeg = 30f)
        assert(wrapping.levelAt(355f) > 0f)
        assertEquals(wrapping.levelAt(5f), wrapping.levelAt(15f), 1e-5f)
    }

    @Test
    fun `level is monotonic from the edges to the peak`() {
        val water = plane()
        var previous = 0f
        for (step in 0..60) {
            val level = water.levelAt(60f - (60 - step).toFloat())
            assert(level >= previous - 1e-6f)
            previous = level
        }
    }

    @Test
    fun `surface spans shaft floor at level zero and peak height at level one`() {
        val water = plane(maxHeightY = 1.0f, shaftBottomY = -1.5f)
        assertEquals(-1.5f, water.surfaceYAt(180f), 1e-5f)
        assertEquals(1.0f, water.surfaceYAt(60f), 1e-5f)
    }

    @Test
    fun `deep stairs drown before shallow ones`() {
        val water = plane()
        val angle = 60f
        // Surface at the peak stands at maxHeightY (1.0): a stair below it
        // drowns; one whose platform rises above the surface stays dry.
        assertTrue(water.isSubmerged(stairPlatformY = -1.0f, angleDeg = angle))
        assertFalse(water.isSubmerged(stairPlatformY = 1.2f, angleDeg = angle))
    }

    @Test
    fun `a stair is dry again once the water recedes`() {
        val water = plane()
        // Submerged at the peak…
        assertTrue(water.isSubmerged(stairPlatformY = 0.0f, angleDeg = 60f))
        // …but dry half a turn away.
        assertFalse(water.isSubmerged(stairPlatformY = 0.0f, angleDeg = 240f))
    }
}
