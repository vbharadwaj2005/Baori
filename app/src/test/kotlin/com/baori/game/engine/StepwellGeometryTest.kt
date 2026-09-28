package com.baori.game.engine

import com.baori.game.data.model.PathSegment
import com.baori.game.data.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 1 engine tests: nodes are derived from segment endpoints and the walk
 * graph exists only over currently-aligned segments — the girl can never
 * step onto stone that is not real right now (PRD §5.1).
 */
class StepwellGeometryTest {

    private fun seg(
        id: String,
        a: String,
        b: String,
        from: Vec3,
        to: Vec3,
        alignsAtDeg: Float = 0f,
    ) = PathSegment(
        id = id,
        a = a,
        b = b,
        alignsAtDeg = alignsAtDeg,
        toleranceDeg = 4f,
        start = from,
        end = to,
    )

    @Test
    fun `node positions derive from the first mentioning segment`() {
        val segments = listOf(
            seg("s1", "village", "gate", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("s2", "gate", "landing", Vec3(1f, 0f, 0f), Vec3(1f, -1f, 0f)),
        )
        val nodes = StepwellGeometry.nodePositions(segments)
        assertEquals(3, nodes.size)
        assertEquals(Vec3(0f, 0f, 0f), nodes["village"])
        assertEquals(Vec3(1f, 0f, 0f), nodes["gate"])
        assertEquals(Vec3(1f, -1f, 0f), nodes["landing"])
    }

    @Test
    fun `connectingSegment finds both orientations of a walkable edge`() {
        val segments = listOf(
            seg("s", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
        )
        assertNotNull(
            StepwellGeometry.connectingSegment(segments, setOf("s"), "a", "b"),
        )
        assertNotNull(
            StepwellGeometry.connectingSegment(segments, setOf("s"), "b", "a"),
        )
    }

    @Test
    fun `connectingSegment refuses dormant or unrelated edges`() {
        val segments = listOf(
            seg("s", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("t", "b", "c", Vec3(1f, 0f, 0f), Vec3(1f, -1f, 0f)),
        )
        // Right pair, wrong walkable set.
        assertNull(StepwellGeometry.connectingSegment(segments, emptySet(), "a", "b"))
        // Walkable segment, but the nodes do not touch it.
        assertNull(StepwellGeometry.connectingSegment(segments, setOf("t"), "a", "b"))
    }

    @Test
    fun `findPath walks a straight chain`() {
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("s2", "b", "c", Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f)),
            seg("s3", "c", "d", Vec3(2f, 0f, 0f), Vec3(3f, 0f, 0f)),
        )
        val path = StepwellGeometry.findPath(segments, setOf("s1", "s2", "s3"), "a", "d")
        assertEquals(listOf("a", "b", "c", "d"), path)
    }

    @Test
    fun `findPath excludes dormant segments from the graph`() {
        val segments = listOf(
            seg("real", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("dormant", "b", "c", Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f)),
        )
        assertNull(StepwellGeometry.findPath(segments, setOf("real"), "a", "c"))
        // And with the bridge aligned, the way opens.
        assertNotNull(
            StepwellGeometry.findPath(segments, setOf("real", "dormant"), "a", "c"),
        )
    }

    @Test
    fun `findPath reaches the goal through a branch the player never sees`() {
        val segments = listOf(
            seg("s1", "start", "fork", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("decoy", "fork", "dead_end", Vec3(1f, 0f, 0f), Vec3(2f, 1f, 0f)),
            seg("s2", "fork", "goal", Vec3(1f, 0f, 0f), Vec3(2f, -1f, 0f)),
        )
        val path = StepwellGeometry.findPath(segments, setOf("s1", "decoy", "s2"), "start", "goal")
        assertEquals(listOf("start", "fork", "goal"), path)
    }

    @Test
    fun `findPath returns a trivial path for the current node`() {
        val segments = listOf(
            seg("s", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
        )
        assertEquals(listOf("a"), StepwellGeometry.findPath(segments, emptySet(), "a", "a"))
    }

    @Test
    fun `findPath reports null for unknown goals`() {
        val segments = listOf(
            seg("s", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
        )
        assertNull(StepwellGeometry.findPath(segments, setOf("s"), "a", "elsewhere"))
    }

    // ---- chunk walk (levels 2–5: rotate → walk → rotate) -------------------

    @Test
    fun `chunkWalkTarget is null when already at the goal`() {
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
        )
        assertNull(
            StepwellGeometry.chunkWalkTarget(segments, setOf("s1"), "a", "a"),
        )
    }

    @Test
    fun `chunkWalkTarget walks only as far as the aligned graph allows`() {
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), alignsAtDeg = 0f),
            seg("s2", "b", "c", Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), alignsAtDeg = 90f),
            seg("s3", "c", "g", Vec3(2f, 0f, 0f), Vec3(3f, 0f, 0f), alignsAtDeg = 180f),
        )
        // Only s1 aligned: walk the first chunk, stop, rotate.
        assertEquals(
            listOf("a", "b"),
            StepwellGeometry.chunkWalkTarget(segments, setOf("s1"), "a", "g"),
        )
        // s1 + s2 aligned: two chunks in one walk, still short of the goal.
        assertEquals(
            listOf("a", "b", "c"),
            StepwellGeometry.chunkWalkTarget(segments, setOf("s1", "s2"), "a", "g"),
        )
        // Everything aligned: straight to the goal.
        assertEquals(
            listOf("a", "b", "c", "g"),
            StepwellGeometry.chunkWalkTarget(segments, setOf("s1", "s2", "s3"), "a", "g"),
        )
    }

    @Test
    fun `chunkWalkTarget never steps onto a decoy dead end`() {
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("s2", "b", "decoy_end", Vec3(1f, 0f, 0f), Vec3(2f, 1f, 0f)),
            seg("s3", "b", "c", Vec3(1f, 0f, 0f), Vec3(1f, -1f, 0f)),
            seg("s4", "c", "g", Vec3(1f, -1f, 0f), Vec3(2f, -1f, 0f)),
        )
        // s2 leads nowhere (decoy_end touches nothing else), so even though
        // it is aligned it must not be chosen; b is the only progress.
        assertEquals(
            listOf("a", "b"),
            StepwellGeometry.chunkWalkTarget(segments, setOf("s1", "s2"), "a", "g"),
        )
    }

    @Test
    fun `chunkWalkTarget is null when nothing reachable makes progress`() {
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("s2", "b", "g", Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f)),
        )
        // Nothing aligned from a; b is aligned but does not shorten the
        // distance to g — the player must rotate instead.
        assertNull(
            StepwellGeometry.chunkWalkTarget(segments, emptySet(), "a", "g"),
        )
    }

    // ---- water gating (levels 4–5) -----------------------------------------

    @Test
    fun `platformYOf is the shallower endpoint`() {
        val s = seg("s", "a", "b", Vec3(0f, -1.6f, 0f), Vec3(1f, -0.5f, 0f))
        assertEquals(-0.5f, StepwellGeometry.platformYOf(s), 1e-6f)
    }

    @Test
    fun `walkableSegmentIds drops submerged segments and keeps the rest`() {
        val segments = listOf(
            seg("high", "a", "b", Vec3(0f, -0.2f, 0f), Vec3(1f, -0.4f, 0f)),
            seg("deep", "b", "c", Vec3(1f, -0.4f, 0f), Vec3(2f, -1.6f, 0f)),
        )
        // At the peak angle the surface stands at maxHeightY (-0.3): the
        // deep stair (top at -0.4) drowns, the high one (top at -0.2) stands.
        val water = WaterPlane(WaterPlane.Config(fullWaterAngleDeg = 0f, spanDeg = 90f, maxHeightY = -0.3f, shaftBottomY = -1f))
        assertEquals(1f, water.levelAt(0f), 1e-4f)
        assertEquals(
            setOf("high"),
            StepwellGeometry.walkableSegmentIds(segments, setOf("high", "deep"), water, 0f),
        )
        // No water: identity — levels 1–3 are untouched by the twist.
        assertEquals(
            setOf("high", "deep"),
            StepwellGeometry.walkableSegmentIds(segments, setOf("high", "deep"), null, 0f),
        )
    }

    @Test
    fun `distancesTo measures real geography over dormant segments`() {
        // Distance ignores alignment entirely — walkability never enters.
        // The decoy hangs off "b" in the graph, so it IS reachable in
        // well-space (distance 2); it can never win a chunk walk because it
        // does not IMPROVE on b's distance (see the chunkWalkTarget tests).
        val segments = listOf(
            seg("s1", "a", "b", Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)),
            seg("s2", "b", "g", Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f)),
            seg("decoy", "b", "dead", Vec3(1f, 0f, 0f), Vec3(1f, 1f, 0f)),
        )
        val distances = StepwellGeometry.distancesTo(segments, "g")
        assertEquals(0, distances["g"])
        assertEquals(1, distances["b"])
        assertEquals(2, distances["a"])
        assertEquals(2, distances["dead"])
    }
}
