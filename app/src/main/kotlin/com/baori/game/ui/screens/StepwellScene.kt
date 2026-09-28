package com.baori.game.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.baori.game.data.model.Level
import com.baori.game.data.model.Vec3
import com.baori.game.engine.ProjectionMath
import com.baori.game.engine.StepwellGeometry
import com.baori.game.ui.components.GameDimensions
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.viewmodel.GameViewModel
import kotlin.math.abs

/**
 * The whole perspective-lock scene, drawn in a single Compose Canvas
 * (PRD §7.1 — custom Canvas drawing, no game engine dependency).
 *
 * Layers, painter's order:
 *   1. well shaft backdrop (depth walls)
 *   2. dormant segments (dim) + drowned segments (water-tinted — aligned but
 *      under the surface, per the Level 4/5 twist)
 *   3. walkable segments (lit, with shape+color goal marker — PRD §9)
 *   4. water plane at the engine's surface height (levels 4/5)
 *   5. the girl
 *
 * Rotation: horizontal drag maps to camera degrees; every interaction keeps
 * running through the ViewModel so state stays single-sourced.
 */
@Composable
fun StepwellScene(
    state: GameViewModel.GameUiState,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors
    val level = state.level ?: return
    var dragStartX by remember { mutableStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        "Stepwell puzzle. Drag horizontally to rotate the well."
                }
                .pointerInput(level.id) {
                    detectDragGestures(
                        onDragStart = { offset -> dragStartX = offset.x },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val degrees = dragAmount.x * GameDimensions.DRAG_DEGREES_PER_PIXEL
                            onDrag(degrees)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
        ) {
            val canvasCenter = ProjectionMath.Vec2(size.width / 2f, size.height / 2f)
            // Fit the whole well inside the canvas at any rotation.
            val scale = computeScale(level, size.width, size.height)
            val angleRad = Math.toRadians(state.angleDeg.toDouble()).toFloat()

            val nodePositions = StepwellGeometry.nodePositions(level.segments)
            val rotatedNodes = nodePositions.mapValues { (_, p) ->
                ProjectionMath.rotateAroundY(p, angleRad)
            }
            val projectedNodes = rotatedNodes.mapValues { (_, p) ->
                ProjectionMath.project(p, canvasCenter, scale)
            }

            // 1. Shaft backdrop: the deep rectangular throat of the well.
            drawShaftBackdrop(rotatedNodes.values, canvasCenter, scale, colors.pathStructure)

            // 2. Dormant segments (dim) and drowned ones (water-tinted):
            // a drowned segment IS aligned — its window is open — but the
            // remembered water covers it, so it reads as unavailable while
            // still telling the player "this exists, rotate the water away".
            for (segment in level.segments) {
                if (segment.id in state.walkableSegmentIds) continue
                if (segment.id in state.alignedSegmentIds) {
                    drawSegment(
                        projectedNodes, segment,
                        color = colors.water.copy(alpha = 0.55f), width = 6f,
                    )
                } else {
                    drawSegment(projectedNodes, segment, color = colors.pathDormant, width = 6f)
                }
            }

            // 3. Walkable segments — sunlit, plus a shape+color goal marker.
            for (segment in level.segments) {
                if (segment.id !in state.walkableSegmentIds) continue
                drawSegment(projectedNodes, segment, color = colors.pathLit, width = 10f)
            }

            // Goal marker: marigold ring + diamond outline (shape + color,
            // colorblind-safe per PRD §9).
            projectedNodes[level.goalNode]?.let { goal ->
                drawGoalMarker(goal, colors.goal)
            }

            // 4. Water plane (levels 4/5 only), at the height the engine
            // actually used for submersion — the picture can never lie about
            // what is walkable.
            val waterY = state.waterSurfaceY
            if (waterY != null) {
                drawWaterPlane(
                    rotatedNodes = rotatedNodes.values,
                    canvasCenter = canvasCenter,
                    scale = scale,
                    waterY = waterY,
                    color = colors.water,
                )
            }

            // 5. The girl.
            drawHero(
                state = state,
                level = level,
                angleRad = angleRad,
                canvasCenter = canvasCenter,
                scale = scale,
                color = colors.protagonist,
            )
        }
    }
}

/** Largest scale that keeps every node on-canvas with a margin. */
private fun computeScale(level: Level, widthPx: Float, heightPx: Float): Float {
    val nodes = StepwellGeometry.nodePositions(level.segments).values
    if (nodes.isEmpty()) return 1f
    var maxRadius = 1f
    for (node in nodes) {
        // Worst-case rotation preserves the x/z magnitude.
        maxRadius = maxOf(maxRadius, kotlin.math.sqrt(node.x * node.x + node.z * node.z))
    }
    val verticalSpan = (nodes.maxOf { it.y } - nodes.minOf { it.y }).coerceAtLeast(1f)
    val scaleByWidth = (widthPx / 2f - 48f) / maxRadius
    val scaleByHeight = (heightPx / 2f - 48f) / (verticalSpan + maxRadius * 0.6f)
    return minOf(scaleByWidth, scaleByHeight).coerceAtLeast(1f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShaftBackdrop(
    rotatedNodes: Collection<Vec3>,
    center: ProjectionMath.Vec2,
    scale: Float,
    color: Color,
) {
    if (rotatedNodes.isEmpty()) return
    val depth = rotatedNodes.minOf { it.z }
    val width = rotatedNodes.maxOf { abs(it.x) }.coerceAtLeast(0.5f)
    val topY = rotatedNodes.maxOf { it.y }
    val bottomY = rotatedNodes.minOf { it.y }
    val topLeft = ProjectionMath.project(Vec3(-width, topY, depth), center, scale)
    val bottomRight = ProjectionMath.project(Vec3(width, bottomY, depth), center, scale)
    drawRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(topLeft.x, topLeft.y),
        size = androidx.compose.ui.geometry.Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegment(
    projectedNodes: Map<String, ProjectionMath.Vec2>,
    segment: com.baori.game.data.model.PathSegment,
    color: Color,
    width: Float,
) {
    val a = projectedNodes[segment.a] ?: return
    val b = projectedNodes[segment.b] ?: return
    drawLine(
        color = color,
        start = Offset(a.x, a.y),
        end = Offset(b.x, b.y),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/** Marigold ring + diamond: meaning survives without color (PRD §9). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGoalMarker(
    goal: ProjectionMath.Vec2,
    color: Color,
) {
    val radius = 26f
    drawCircle(color = color, radius = radius.toFloat(), center = Offset(goal.x, goal.y), style = Stroke(width = 4f))
    val d = radius * 0.55f
    drawLine(color, Offset(goal.x, goal.y - d), Offset(goal.x + d, goal.y), 3f)
    drawLine(color, Offset(goal.x + d, goal.y), Offset(goal.x, goal.y + d), 3f)
    drawLine(color, Offset(goal.x, goal.y + d), Offset(goal.x - d, goal.y), 3f)
    drawLine(color, Offset(goal.x - d, goal.y), Offset(goal.x, goal.y - d), 3f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaterPlane(
    rotatedNodes: Collection<Vec3>,
    canvasCenter: ProjectionMath.Vec2,
    scale: Float,
    waterY: Float,
    color: Color,
) {
    val width = (rotatedNodes.maxOf { abs(it.x) } + 0.4f)
    val depth = rotatedNodes.minOf { it.z }
    val left = ProjectionMath.project(Vec3(-width, waterY, depth), canvasCenter, scale)
    val right = ProjectionMath.project(Vec3(width, waterY, depth), canvasCenter, scale)
    drawLine(
        color = color.copy(alpha = 0.9f),
        start = Offset(left.x, left.y),
        end = Offset(right.x, right.y),
        strokeWidth = 8f,
        cap = StrokeCap.Round,
    )
    // Translucent body below the surface line.
    val bottom = ProjectionMath.project(Vec3(-width, -2f, depth), canvasCenter, scale)
    drawRect(
        color = color.copy(alpha = 0.25f),
        topLeft = Offset(left.x, left.y),
        size = androidx.compose.ui.geometry.Size(right.x - left.x, bottom.y - left.y),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHero(
    state: GameViewModel.GameUiState,
    level: Level,
    angleRad: Float,
    canvasCenter: ProjectionMath.Vec2,
    scale: Float,
    color: Color,
) {
    val nodePositions = StepwellGeometry.nodePositions(level.segments)
    val from = nodePositions[state.heroNode] ?: return
    val target = state.heroTargetNode?.let { nodePositions[it] }
    val position = when {
        target != null -> ProjectionMath.lerp(from, target, state.heroStepProgress)
        else -> from
    }
    val rotated = ProjectionMath.rotateAroundY(position, angleRad)
    val screen = ProjectionMath.project(rotated, canvasCenter, scale)
    // The girl: a sari-colored dot over a soft halo — readable at every size.
    drawCircle(color = color.copy(alpha = 0.25f), radius = 30f, center = Offset(screen.x, screen.y))
    drawCircle(color = color, radius = 14f, center = Offset(screen.x, screen.y))
}
