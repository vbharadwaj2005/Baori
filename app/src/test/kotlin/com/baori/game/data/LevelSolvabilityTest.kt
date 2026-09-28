package com.baori.game.data

import com.baori.game.engine.PathAligner
import com.baori.game.engine.ProjectionMath
import com.baori.game.engine.StepwellGeometry
import com.baori.game.engine.WaterPlane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Day 2/3 capstone: every bundled level is simulated end to end with the
 * exact engine the shipping game runs — [PathAligner] windows, the
 * [WaterPlane] submersion rule, and the greedy chunk-walk contract — using
 * the snap angles a player actually lands on.
 *
 * A level that becomes unwinnable after a JSON edit fails here, at build
 * time, instead of in front of a judge. This is the machine-checkable form
 * of PRD §6's mechanic progression.
 */
class LevelSolvabilityTest {

    private val repository = LevelRepository(FakeAssets)
    private val aligner = PathAligner()

    /**
     * Serves the real level JSON to the JVM. AGP does not reliably put
     * src/main/assets on the unit-test classpath, so this reads the files
     * straight from the repo, walking upward from the working directory
     * (Gradle may run from a module or the root).
     */
    private object FakeAssets : AssetReader {
        private val assetsDir: File by lazy {
            var dir = File(System.getProperty("user.dir") ?: ".")
            for (attempt in 0 until 6) {
                val candidate = File(dir, "src/main/assets")
                if (candidate.isDirectory) return@lazy candidate
                dir = dir.parentFile ?: break
            }
            File("src/main/assets")
        }

        override fun readText(path: String): String {
            val file = File(assetsDir, path)
            return file.takeIf { it.isFile }?.readText()
                ?: error("Missing bundled asset: ${file.absolutePath}")
        }
    }

    /** What a player pressing Walk gets at one angle: goal path or chunk. */
    private data class Offer(
        val pathToGoal: List<String>?,
        val chunk: List<String>?,
    )

    private fun walkableAt(
        level: com.baori.game.data.model.Level,
        angleDeg: Float,
    ): Set<String> {
        val aligned = aligner.evaluate(level.segments, angleDeg).walkableIds
        return StepwellGeometry.walkableSegmentIds(
            level.segments, aligned, WaterPlane.fromLevel(level.water), angleDeg,
        )
    }

    private fun offerAt(
        level: com.baori.game.data.model.Level,
        angleDeg: Float,
        heroNode: String,
    ): Offer {
        val walkable = walkableAt(level, angleDeg)
        val pathToGoal = StepwellGeometry.findPath(
            level.segments, walkable, heroNode, level.goalNode,
        )
        val chunk = StepwellGeometry.chunkWalkTarget(
            level.segments, walkable, heroNode, level.goalNode,
        )
        return Offer(pathToGoal, chunk)
    }

    /**
     * One Walk press from [heroNode] at [angleDeg]: advances along the
     * currently walkable graph exactly as
     * [com.baori.game.viewmodel.GameViewModel.tryWalk] does — goal first,
     * else the progress chunk — and returns the node the girl lands on.
     */
    private fun walkOnce(
        level: com.baori.game.data.model.Level,
        angleDeg: Float,
        heroNode: String,
    ): String {
        val walkable = walkableAt(level, angleDeg)
        val target = StepwellGeometry.findPath(
            level.segments, walkable, heroNode, level.goalNode,
        )?.last()
            ?: StepwellGeometry.chunkWalkTarget(
                level.segments, walkable, heroNode, level.goalNode,
            )?.last()
            ?: error("No walk from '$heroNode' at $angleDeg° — level is stuck")
        return StepwellGeometry.findPath(
            level.segments, walkable, heroNode, target,
        )?.last() ?: target
    }

    @Test
    fun `bundled index lists every level file that ships`() {
        val index = repository.loadIndex()
        val levels = repository.loadAll()
        assertEquals(index.levels.size, levels.size)
        assertEquals(index.levels.map { it.id }, levels.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 5), levels.map { it.order })
    }

    @Test
    fun `level1 completes in one walk at its goal angle`() {
        val level = repository.loadLevel("levels/level1.json")
        val offer = offerAt(level, 90f, heroNode = level.startNode)
        assertEquals(level.goalNode, offer.pathToGoal?.last())
        // The starting angle is not already a solve, but offers a chunk.
        val start = offerAt(level, level.startAngleDeg, heroNode = level.startNode)
        assertTrue(start.pathToGoal == null && start.chunk != null)
    }

    @Test
    fun `level2 needs both alignment axes and neither alone suffices`() {
        val level = repository.loadLevel("levels/level2.json")
        // At the first axis the goal is NOT reachable — the south-east stair
        // (aligned only at 270°) blocks the ring's far side.
        val first = offerAt(level, 90f, heroNode = level.startNode)
        assertTrue(first.pathToGoal == null && first.chunk != null)
        val hero1 = walkOnce(level, 90f, level.startNode)
        assertEquals("ring_south", hero1)
        // The second axis opens the ring's far side but only as far as the
        // east stair — the final descent needs the first axis again.
        val hero2 = walkOnce(level, 270f, hero1)
        assertEquals("ring_east", hero2)
        val hero3 = walkOnce(level, 90f, hero2)
        assertEquals(level.goalNode, hero3)
        // From the start, the second axis alone can never finish: the entry
        // corridor still aligns (it spans half the circle by design), but
        // the goal stair does not — the goal is out of reach at 270°.
        val stuck = offerAt(level, 270f, heroNode = level.startNode)
        assertTrue(stuck.pathToGoal == null)
    }

    @Test
    fun `level3 punishes releasing late and rewards the tight bridge`() {
        val level = repository.loadLevel("levels/level3.json")
        // Off-window: the ±2° bridge window is closed one snap-step away.
        assertFalse(
            aligner.evaluate(level.segments, 135f + 15f).walkableIds.contains("s3_bridge"),
        )
        // On-window: from ledge_a the bridge alone is real and crosses.
        val mid = offerAt(level, 135f, heroNode = "ledge_a")
        assertTrue(mid.pathToGoal == null && mid.chunk!!.last() == "ledge_b")
        // The full solve runs through ledge_b: 45° → tight 135° → 225°.
        var hero = walkOnce(level, 45f, level.startNode)
        assertEquals("ledge_a", hero)
        hero = walkOnce(level, 135f, hero)
        assertEquals("ledge_b", hero)
        hero = walkOnce(level, 225f, hero)
        assertEquals(level.goalNode, hero)
    }

    @Test
    fun `level4 drowns the direct decoy and solves through the drained gallery`() {
        val level = repository.loadLevel("levels/level4.json")
        val water = WaterPlane.fromLevel(level.water)!!
        val decoy = level.segments.first { it.id == "s4_flooded_direct" }

        // The direct stair is aligned at the flood peak but under water —
        // aligned ≠ walkable, and around its whole window it never dries.
        assertTrue("s4_flooded_direct" in aligner.evaluate(level.segments, 90f).walkableIds)
        assertTrue(water.isSubmerged(StepwellGeometry.platformYOf(decoy), 90f))
        assertFalse("s4_flooded_direct" in walkableAt(level, 90f))
        assertFalse("s4_flooded_direct" in walkableAt(level, 96f))

        // Solve: 0° (upper route) → 240° (mid landing, dry above the flood
        // band) → 330° (the drained far side: gallery and deep stair align
        // together, one walk reaches the goal). The rotation from 240° to
        // 330° crosses the flood peak — the twist made visible.
        val hero1 = walkOnce(level, 0f, level.startNode)
        assertEquals("west_shelf", hero1)
        val hero2 = walkOnce(level, 240f, hero1)
        assertEquals("mid_landing", hero2)
        val hero3 = walkOnce(level, 330f, hero2)
        assertEquals(level.goalNode, hero3)
    }

    @Test
    fun `level5 opens flooded, drains at the drought angle, and finishes dry`() {
        val level = repository.loadLevel("levels/level5.json")
        val water = WaterPlane.fromLevel(level.water)!!
        val rim = level.segments.first { it.id == "s5_rim_first" }
        val decoy = level.segments.first { it.id == "s5_flooded_short" }

        // The finale opens at the flood peak: the entry stair is aligned but
        // the remembered water covers everything — nothing is walkable.
        assertTrue("s5_rim_first" in aligner.evaluate(level.segments, 90f).walkableIds)
        assertTrue(water.isSubmerged(StepwellGeometry.platformYOf(rim), 90f))
        assertTrue("s5_flooded_short" in aligner.evaluate(level.segments, 90f).walkableIds)
        assertTrue(water.isSubmerged(StepwellGeometry.platformYOf(decoy), 90f))
        assertTrue(walkableAt(level, 90f).isEmpty())

        // The solve: 0° (rim, drained) → 270° (the drought side, where the
        // hidden stair aligns dry and one walk crosses both its flights)
        // → 0° (reach the water). Water peaks at 90°, opposite the reveal.
        val hero1 = walkOnce(level, 0f, level.startNode)
        assertEquals("first_step", hero1)
        val hero2 = walkOnce(level, 270f, hero1)
        assertEquals("second_step", hero2)
        val hero3 = walkOnce(level, 0f, hero2)
        assertEquals(level.goalNode, hero3)
    }
}
