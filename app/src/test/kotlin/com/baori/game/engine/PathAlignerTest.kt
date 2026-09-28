package com.baori.game.engine

import com.baori.game.data.model.PathSegment
import com.baori.game.data.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 1 engine tests: PathAligner is the perspective-lock contract (PRD
 * §5.1) — a segment exists exactly when the camera sits inside its window,
 * compared on the shortest arc.
 */
class PathAlignerTest {

    private val aligner = PathAligner(toleranceDeg = 4f)

    private fun segment(
        id: String,
        alignsAtDeg: Float,
        toleranceDeg: Float = 0f,
    ) = PathSegment(
        id = id,
        a = "a",
        b = "b",
        alignsAtDeg = alignsAtDeg,
        toleranceDeg = toleranceDeg,
        start = Vec3(0f, 0f, 0f),
        end = Vec3(1f, 0f, 0f),
    )

    @Test
    fun `segment is walkable inside its window and dormant outside`() {
        val segments = listOf(segment("s", alignsAtDeg = 90f))
        assertTrue(aligner.evaluate(segments, 87f).walkableIds.contains("s"))
        assertTrue(aligner.evaluate(segments, 90f).walkableIds.contains("s"))
        assertTrue(aligner.evaluate(segments, 94f).walkableIds.contains("s"))
        assertFalse(aligner.evaluate(segments, 95f).walkableIds.contains("s"))
    }

    @Test
    fun `alignment is compared on the shortest arc`() {
        val segments = listOf(segment("wrap", alignsAtDeg = 2f))
        // 359° is 3° away from 2° across the zero boundary.
        assertTrue(aligner.evaluate(segments, 359f).walkableIds.contains("wrap"))
        assertFalse(aligner.evaluate(segments, 356f).walkableIds.contains("wrap"))
    }

    @Test
    fun `per-segment tolerance overrides the default`() {
        val wide = listOf(segment("wide", alignsAtDeg = 0f, toleranceDeg = 20f))
        assertTrue(aligner.evaluate(wide, 18f).walkableIds.contains("wide"))

        val narrow = listOf(segment("narrow", alignsAtDeg = 0f, toleranceDeg = 1f))
        assertFalse(aligner.evaluate(narrow, 3f).walkableIds.contains("narrow"))
    }

    @Test
    fun `zero tolerance falls back to the aligner default`() {
        val none = listOf(segment("s", alignsAtDeg = 45f, toleranceDeg = 0f))
        assertTrue(aligner.evaluate(none, 44f).walkableIds.contains("s"))
        assertFalse(aligner.evaluate(none, 50f).walkableIds.contains("s"))
    }

    @Test
    fun `only aligned segments are reported`() {
        val segments = listOf(
            segment("aligned", alignsAtDeg = 90f),
            segment("dormant", alignsAtDeg = 270f),
        )
        val result = aligner.evaluate(segments, 90f)
        assertEquals(setOf("aligned"), result.walkableIds)
    }

    @Test
    fun `activeGoal is the closest satisfied goal`() {
        val segments = listOf(
            segment("near", alignsAtDeg = 90f),
            segment("far", alignsAtDeg = 86f),
        )
        // Both windows (4° default) contain 87°, but 86° is strictly
        // closer (1° vs 3°) — the nearest goal wins.
        assertEquals(
            86f,
            aligner.evaluate(segments, 87f).activeGoalDeg!!,  // premise guarantees non-null
            1e-5f,
        )
    }

    @Test
    fun `activeGoal is null when nothing aligns`() {
        val result = aligner.evaluate(listOf(segment("s", alignsAtDeg = 90f)), 180f)
        assertNull(result.activeGoalDeg)
        assertTrue(result.walkableIds.isEmpty())
    }

    @Test
    fun `empty level evaluates to nothing walkable`() {
        val result = aligner.evaluate(emptyList(), 90f)
        assertTrue(result.walkableIds.isEmpty())
        assertNull(result.activeGoalDeg)
    }
}
