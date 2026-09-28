package com.baori.game.data

import com.baori.game.data.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day 1 engine-adjacent test: level JSON is the whole content pipeline
 * (PRD §7.1), so parsing and ordering are pinned here against an in-memory
 * [AssetReader] — no Android required.
 */
class LevelRepositoryTest {

    /** In-memory asset reader: the JVM seam the repository was built around. */
    private class FakeAssetReader(private val files: Map<String, String>) : AssetReader {
        override fun readText(path: String): String =
            files[path] ?: error("No asset bundled at $path")
    }

    private val indexJson = """
        {
          "levels": [
            { "id": "level2", "file": "level2.json", "free": false },
            { "id": "level1", "file": "level1.json", "free": true }
          ]
        }
    """.trimIndent()

    private fun levelJson(id: String, order: Int, free: Boolean) = """
        {
          "id": "$id",
          "order": $order,
          "free": $free,
          "goalAnglesDeg": [90, 270],
          "toleranceDeg": 4,
          "snapStepDeg": 15,
          "startAngleDeg": 0,
          "water": null,
          "startNode": "start",
          "goalNode": "goal",
          "muralAfter": "mural_1",
          "segments": [
            {
              "id": "s1",
              "a": "start",
              "b": "goal",
              "alignsAtDeg": 90,
              "toleranceDeg": 4,
              "start": { "x": 0.0, "y": 0.0, "z": 0.0 },
              "end":   { "x": 1.0, "y": -1.0, "z": 0.0 }
            }
          ]
        }
    """.trimIndent()

    private fun repository(files: Map<String, String> = mapOf(
        "levels/index.json" to indexJson,
        "levels/level1.json" to levelJson("level1", order = 1, free = true),
        "levels/level2.json" to levelJson("level2", order = 2, free = false),
    )) = LevelRepository(FakeAssetReader(files))

    @Test
    fun `loadIndex reads the declared levels`() {
        val index = repository().loadIndex()
        assertEquals(2, index.levels.size)
        // loadIndex preserves declaration order; sorting by `order` is
        // loadAll's job and is pinned by its own test below.
        assertEquals("level2", index.levels.first().id)
        assertEquals("level2.json", index.levels.first().file)
        assertFalse(index.levels.first().free)
        assertEquals("level1", index.levels.last().id)
        assertTrue(index.levels.last().free)
    }

    @Test
    fun `loadLevel parses one level file`() {
        val level: Level = repository().loadLevel("levels/level1.json")
        assertEquals("level1", level.id)
        assertEquals(1, level.order)
        assertEquals(listOf(90f, 270f), level.goalAnglesDeg)
        assertEquals(15f, level.snapStepDeg)
        assertEquals("start", level.startNode)
        assertEquals("goal", level.goalNode)
        assertEquals(1, level.segments.size)
    }

    @Test
    fun `loadAll orders levels by order not file order`() {
        val levels = repository().loadAll()
        assertEquals(listOf("level1", "level2"), levels.map { it.id })
        assertEquals(1, levels.first().order)
    }

    @Test
    fun `water config parses when present`() {
        val withWater = """
            {
              "id": "level4",
              "order": 4,
              "free": false,
              "goalAnglesDeg": [60],
              "startNode": "a",
              "goalNode": "b",
              "water": { "fullWaterAngleDeg": 60, "spanDeg": 45, "maxHeightUnits": 1.1 },
              "segments": [
                {
                  "id": "s",
                  "a": "a",
                  "b": "b",
                  "alignsAtDeg": 60,
                  "toleranceDeg": 5,
                  "start": { "x": 0.0, "y": 0.0, "z": 0.0 },
                  "end": { "x": 1.0, "y": 0.0, "z": 0.0 }
                }
              ]
            }
        """.trimIndent()
        val level = repository(
            mapOf(
                "levels/index.json" to """{ "levels": [] }""",
                "levels/level4.json" to withWater,
            ),
        ).loadLevel("levels/level4.json")
        assertEquals(60f, level.water?.fullWaterAngleDeg)
        assertEquals(45f, level.water?.spanDeg)
        assertEquals(1.1f, level.water?.maxHeightUnits)
    }

    @Test
    fun `unknown json keys are ignored for forward compatibility`() {
        val forward = levelJson("level9", order = 9, free = true)
            .replace("\"segments\"", "\"futureField\": 42, \"segments\"")
        val level = repository(
            mapOf(
                "levels/index.json" to """{ "levels": [] }""",
                "levels/level9.json" to forward,
            ),
        ).loadLevel("levels/level9.json")
        assertEquals("level9", level.id)
    }

    @Test
    fun `a missing asset surfaces as an error rather than silence`() {
        assertThrows(IllegalStateException::class.java) {
            repository(files = mapOf("levels/index.json" to """{ "levels": [] }"""))
                .loadLevel("levels/missing.json")
        }
    }
}
