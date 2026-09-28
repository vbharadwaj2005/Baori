package com.baori.game.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.baori.game.ui.components.BaoriButton
import com.baori.game.ui.components.ButtonSize
import com.baori.game.ui.components.ButtonVariant
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.ui.theme.BaoriTypography
import kotlinx.coroutines.delay

/**
 * Mural screen (PRD §6.1): static full-screen illustrations between levels,
 * no text, no dialogue — the story stays legible through imagery alone.
 *
 * Day 1 ships the screen and the drawing code; the three murals render as
 * original flat-color vector scenes composed here (a file-based asset
 * pipeline is a Day 2 item, if the code-drawn scenes need replacing).
 *
 * Tapping anywhere (or the continue affordance) advances with an eased fade
 * honoring reduce motion (PRD §9).
 */
enum class MuralId { GIRL_LEAVES_VILLAGE, OUTER_RING, GRANDMOTHERS_MEMORY, THE_DROUGHT_BEGAN, WATER_RETURNS }

@Composable
fun MuralScreen(
    mural: MuralId,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors
    val motion = BaoriTheme.motion
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(mural) { delay(16); appeared = true }

    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = motion.muralFadeMs.toInt()),
        label = "muralFade",
    )

    // The eased fade lives on the container via graphicsLayer, so the whole
    // mural cross-fades in one pass (PRD §9: easing on every transition).
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer { this.alpha = alpha }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                drawMural(mural, colors)
            }
            Spacer(Modifier.height(40.dp))
            BaoriButton(
                text = "Continue",
                onClick = onContinue,
                variant = ButtonVariant.GHOST,
                size = ButtonSize.MD,
            )
        }
    }
}

/**
 * Original flat-color mural scenes. Each is drawn from the world palette
 * only — pigments from the token set, never raw values (design doc §6).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMural(
    mural: MuralId,
    colors: com.baori.game.ui.theme.GameColors,
) {
    val ground = colors.mural
    val ink = colors.muralInk

    // Shared ground line — every mural stands on the same earth.
    drawLine(ink, Offset(0f, size.height * 0.82f), Offset(size.width, size.height * 0.82f), 3f)

    when (mural) {
        MuralId.GIRL_LEAVES_VILLAGE -> {
            // A small figure with a pot on her hip, walking away from huts.
            val fx = size.width * 0.62f
            val fy = size.height * 0.82f
            drawCircle(colors.protagonist, 16f, Offset(fx, fy - 74f))          // head
            drawLine(colors.protagonist, Offset(fx, fy - 58f), Offset(fx, fy - 20f), 8f) // body
            drawLine(ink, Offset(fx, fy - 20f), Offset(fx - 12f, fy), 4f)      // stride
            drawLine(ink, Offset(fx, fy - 20f), Offset(fx + 14f, fy), 4f)
            drawCircle(colors.goal, 10f, Offset(fx + 18f, fy - 44f))           // the clay pot
            // Village huts behind her.
            hut(Offset(size.width * 0.2f, fy), 90f, ink, ground)
            hut(Offset(size.width * 0.38f, fy), 70f, ink, ground)
        }
        MuralId.OUTER_RING -> {
            // The outer ring of the well, seen from above; she stands at its edge.
            val c = Offset(size.width * 0.5f, size.height * 0.52f)
            drawCircle(ground, 150f, c)
            drawCircle(ink, 150f, c, style = Stroke(3f))
            drawCircle(ink, 110f, c, style = Stroke(3f))
            drawCircle(ink, 70f, c, style = Stroke(3f))
            drawCircle(colors.protagonist, 12f, Offset(c.x + 150f, c.y))
        }
        MuralId.GRANDMOTHERS_MEMORY -> {
            // A fragment: two figures by water, one holding a full pot.
            val fy = size.height * 0.82f
            drawCircle(colors.protagonist, 14f, Offset(size.width * 0.35f, fy - 70f))
            drawLine(colors.protagonist, Offset(size.width * 0.35f, fy - 56f), Offset(size.width * 0.35f, fy - 18f), 7f)
            drawCircle(ink, 14f, Offset(size.width * 0.55f, fy - 70f))
            drawLine(ink, Offset(size.width * 0.55f, fy - 56f), Offset(size.width * 0.55f, fy - 18f), 7f)
            drawCircle(colors.water, 12f, Offset(size.width * 0.45f, fy - 40f))
            // A full pot between them.
            drawCircle(colors.goal, 10f, Offset(size.width * 0.45f, fy - 26f))
        }
        MuralId.THE_DROUGHT_BEGAN -> {
            // A cracked well: the ring, hairline fractures, no water.
            val c = Offset(size.width * 0.5f, size.height * 0.55f)
            drawCircle(ground, 140f, c)
            drawCircle(ink, 140f, c, style = Stroke(3f))
            drawLine(ink, Offset(c.x - 90f, c.y - 60f), Offset(c.x - 30f, c.y - 10f), 2.5f)
            drawLine(ink, Offset(c.x - 30f, c.y - 10f), Offset(c.x - 55f, c.y + 55f), 2.5f)
            drawLine(ink, Offset(c.x + 20f, c.y - 120f), Offset(c.x + 45f, c.y - 40f), 2.5f)
        }
        MuralId.WATER_RETURNS -> {
            // Rain over the well; the water line returns.
            val fy = size.height * 0.62f
            for (i in 0..8) {
                val x = size.width * (0.15f + 0.09f * i)
                drawLine(colors.water, Offset(x, size.height * 0.12f), Offset(x - 8f, size.height * 0.3f), 3f)
            }
            drawLine(colors.water, Offset(size.width * 0.25f, fy), Offset(size.width * 0.75f, fy), 5f)
            // Steps descending into the water, filling.
            for (i in 0..3) {
                val w = 160f - i * 36f
                drawRect(
                    colors.pathStructure,
                    topLeft = Offset(size.width / 2f - w / 2f, fy + 14f + i * 22f),
                    size = androidx.compose.ui.geometry.Size(w, 14f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.hut(
    base: Offset,
    width: Float,
    ink: Color,
    ground: Color,
) {
    val wallHeight = width * 0.7f
    drawRect(
        ground,
        topLeft = Offset(base.x - width / 2f, base.y - wallHeight),
        size = androidx.compose.ui.geometry.Size(width, wallHeight),
    )
    // Roof: a simple triangle.
    val roof = Path().apply {
        moveTo(base.x - width * 0.62f, base.y - wallHeight)
        lineTo(base.x, base.y - wallHeight - width * 0.45f)
        lineTo(base.x + width * 0.62f, base.y - wallHeight)
        close()
    }
    drawPath(roof, ink)
}
