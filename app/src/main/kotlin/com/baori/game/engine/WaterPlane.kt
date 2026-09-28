package com.baori.game.engine

import com.baori.game.data.model.WaterConfig
import kotlin.math.PI
import kotlin.math.cos

/**
 * Maps rotation angle to water height for the Level 4/5 water-tilt twist
 * (PRD §5.1, §7.3).
 *
 * The remembered water rises and falls as the well turns: some stairs drown
 * (become unwalkable) while previously "underwater" stairs are revealed.
 *
 * The model is a rising surface between the shaft bottom and a maximum:
 *
 *   surfaceY(angle) = shaftBottomY + level(angle) · (maxHeightY − shaftBottomY)
 *
 * where level(angle) is a raised-cosine bump centered on the level's
 * full-water angle. Because the surface sweeps continuously from the bottom,
 * deeper stairs drown first — the reveal reads spatially, not as a switch.
 *
 * Day 1 ships the engine and its tests; levels 4/5 consume it on Day 3.
 */
class WaterPlane(
    private val config: Config,
) {

    /**
     * @property fullWaterAngleDeg camera angle at which water peaks
     * @property spanDeg angular width of the rise-and-fall (half-width each
     *   side of the peak)
     * @property maxHeightY water surface at the peak, in well units
     * @property shaftBottomY the shaft floor; the surface starts here at
     *   level 0, so every stair above it starts dry
     */
    data class Config(
        val fullWaterAngleDeg: Float,
        val spanDeg: Float = 60f,
        val maxHeightY: Float = 0.5f,
        val shaftBottomY: Float = -1.5f,
    )

    /** Normalized 0..1 water level at [angleDeg]; 0 outside the span. */
    fun levelAt(angleDeg: Float): Float {
        val dist = kotlin.math.abs(ProjectionMath.shortestArcDeg(angleDeg, config.fullWaterAngleDeg))
        if (dist >= config.spanDeg) return 0f
        val phase = dist / config.spanDeg // 0 at peak, 1 at the edges
        return 0.5f * (1f + cos(PI.toFloat() * phase))
    }

    /** Absolute water surface height (well units) at [angleDeg]. */
    fun surfaceYAt(angleDeg: Float): Float =
        config.shaftBottomY +
            levelAt(angleDeg) * (config.maxHeightY - config.shaftBottomY)

    /**
     * A stair is submerged — and therefore unwalkable — when the water
     * surface stands above its platform. Deeper platforms drown first, which
     * is what makes "revealing hidden stairs" a spatial, readable event.
     */
    fun isSubmerged(stairPlatformY: Float, angleDeg: Float): Boolean =
        surfaceYAt(angleDeg) > stairPlatformY

    companion object {

        /**
         * Builds the plane from a level's JSON water config, or null for
         * levels without the twist (1–3). [WaterConfig.maxHeightUnits] is
         * the peak height above the shaft bottom; the engine works in
         * absolute well units, so the bottom sits at its negation.
         */
        fun fromLevel(config: WaterConfig?): WaterPlane? {
            config ?: return null
            return WaterPlane(
                Config(
                    fullWaterAngleDeg = config.fullWaterAngleDeg,
                    spanDeg = config.spanDeg,
                    maxHeightY = config.maxHeightUnits,
                    shaftBottomY = -config.maxHeightUnits,
                ),
            )
        }
    }
}
