package com.baori.game.data

import com.baori.game.data.model.Level
import com.baori.game.data.model.LevelIndex
import kotlinx.serialization.json.Json

/**
 * Loads level geometry from bundled JSON in assets/levels/ (PRD §7.1).
 *
 * Keeping levels out of Kotlin means Day 2/3 level design is data editing,
 * and judges can read the puzzle definitions as plain text in the repo.
 */
class LevelRepository(
    private val assetReader: AssetReader,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Ordered list of level references from assets/levels/index.json. */
    fun loadIndex(): LevelIndex {
        val text = assetReader.readText("levels/index.json")
        return json.decodeFromString<LevelIndex>(text)
    }

    /** Loads and parses one level file, e.g. "levels/level1.json". */
    fun loadLevel(path: String): Level {
        val text = assetReader.readText(path)
        return json.decodeFromString<Level>(text)
    }

    /**
     * Convenience: loads every level from the index, ordered by [Level.order].
     * Called once by the ViewModels; levels are tiny so eager loading is fine.
     */
    fun loadAll(): List<Level> =
        loadIndex().levels
            .map { loadLevel("levels/${it.file}") }
            .sortedBy { it.order }
}

/**
 * Abstraction over Android assets so level loading is unit-testable on the
 * JVM (PRD §7.3 — clean seams judges can inspect).
 */
interface AssetReader {
    fun readText(path: String): String
}
