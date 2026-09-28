package com.baori.game.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Day 1 engine tests: the easing vocabulary is the house motion language
 * (PRD §9), so its boundary behavior is pinned here.
 */
class EasingsTest {

    @Test
    fun `easeOutCubic hits exact endpoints`() {
        assertEquals(0f, Easings.easeOutCubic(0f), 1e-6f)
        assertEquals(1f, Easings.easeOutCubic(1f), 1e-6f)
    }

    @Test
    fun `easeOutCubic is fast early and gentle late`() {
        // Half the clock, most of the distance — the "fast start".
        assert(Easings.easeOutCubic(0.5f) > 0.75f)
        // Never overshoots the target.
        assert(Easings.easeOutCubic(0.9f) < 1f)
    }

    @Test
    fun `easeOutCubic is monotonically increasing`() {
        var previous = Easings.easeOutCubic(0f)
        for (step in 1..100) {
            val t = step / 100f
            val current = Easings.easeOutCubic(t)
            assert(current >= previous) { "regressed at t=$t" }
            previous = current
        }
    }

    @Test
    fun `easeInOutCubic hits exact endpoints with slow middle start`() {
        assertEquals(0f, Easings.easeInOutCubic(0f), 1e-6f)
        assertEquals(1f, Easings.easeInOutCubic(1f), 1e-6f)
        assertEquals(0.5f, Easings.easeInOutCubic(0.5f), 1e-6f)
        assert(Easings.easeInOutCubic(0.25f) < 0.25f)
    }

    @Test
    fun `progress clamps and handles zero duration`() {
        assertEquals(0f, Easings.progress(0L, 100L), 1e-6f)
        assertEquals(0.5f, Easings.progress(50L, 100L), 1e-6f)
        assertEquals(1f, Easings.progress(150L, 100L), 1e-6f)
        assertEquals(1f, Easings.progress(50L, 0L), 1e-6f)
    }

    @Test
    fun `breathe is a bounded sine`() {
        assertEquals(0f, Easings.breathe(0f), 1e-5f)
        assert(Easings.breathe(0.25f, 1f) > 0.9f)
        assert(Easings.breathe(0.75f, 1f) < -0.9f)
        for (t in 0..100) {
            val v = Easings.breathe(t / 10f, 1f)
            assert(v in -1.0001f..1.0001f)
        }
    }
}
