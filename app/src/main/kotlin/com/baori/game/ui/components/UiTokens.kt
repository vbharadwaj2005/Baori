package com.baori.game.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and layout dimension tokens (design doc §2/§3 — centralized
 * spacing and containment values; components never pick ad-hoc dp numbers).
 */
object GameSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

object GameDimensions {
    /** Minimum touch target — accessibility floor (design doc §6). */
    val minTouchTarget: Dp = 44.dp

    /** Max reading width for centered screens (design doc §3). */
    val maxReadingWidth: Dp = 560.dp

    /** Standard screen horizontal padding. */
    val screenPadding: Dp = 24.dp

    /** Drag sensitivity: degrees of camera rotation per pixel of drag. */
    const val DRAG_DEGREES_PER_PIXEL: Float = 0.35f
}
