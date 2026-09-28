package com.baori.game.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/**
 * Shapes derived from the centralized radius token (design doc §2 "Radius:
 * a centralized mathematical scaling property that governs corner curvature
 * consistency across all components"). Components never pick their own dp
 * values; they pick a shape role.
 */
object GameShapes {

    fun small(colors: GameColors): Shape = RoundedCornerShape(colors.radius.small)

    fun medium(colors: GameColors): Shape = RoundedCornerShape(colors.radius.medium)

    fun large(colors: GameColors): Shape = RoundedCornerShape(colors.radius.large)

    /** Pill shape for buttons/chips — full radius, independent of tokens. */
    val pill: Shape = RoundedCornerShape(50)
}
