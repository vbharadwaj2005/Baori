package com.baori.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.baori.game.ui.components.BackButton
import com.baori.game.ui.components.BaoriButton
import com.baori.game.ui.components.ButtonVariant
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.ui.theme.BaoriTypography
import com.baori.game.viewmodel.GameViewModel

/**
 * The play screen: full-bleed stepwell scene, a back affordance, and a
 * single verb — "walk" — enabled whenever the perspective-lock contract
 * offers progress (PRD §5.1: paths only connect from certain angles; on
 * levels 2–5 the walk is a chunk, so rotating between walks is expected).
 *
 * Haptics fire on level completion (PRD §9 "haptic feedback on successful
 * path-snap"); a stronger pulse marks the goal.
 */
@Composable
fun GameScreen(
    state: GameViewModel.GameUiState,
    hapticPulse: Int,
    reduceMotion: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onWalk: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors
    val haptics = LocalHapticFeedback.current

    // PRD §9: haptic feedback on the completion moment.
    LaunchedEffect(hapticPulse) {
        if (hapticPulse > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        StepwellScene(
            state = state,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            state.level?.let { level ->
                Text(
                    text = "${level.order}",
                    style = BaoriTypography.labelLarge,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(modifier = Modifier.height(44.dp))
        }

        // The one verb. Disabled until the path is real — the button state
        // itself teaches the mechanic.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isComplete) {
                BaoriButton(
                    text = "Continue",
                    onClick = onContinue,
                    variant = ButtonVariant.DEFAULT,
                    size = com.baori.game.ui.components.ButtonSize.LG,
                )
            } else {
                // Three-tier affordance: lit when the goal is reachable,
                // ghost when a chunk walk would make progress, disabled when
                // only rotation can help — the button state itself teaches
                // the mechanic (PRD §9: no text required to play).
                val tier = state.canAdvance
                BaoriButton(
                    text = "Walk",
                    onClick = onWalk,
                    enabled = tier != GameViewModel.GameUiState.AdvanceTier.STUCK &&
                        !state.isWalking,
                    variant = if (tier == GameViewModel.GameUiState.AdvanceTier.REACHABLE) {
                        ButtonVariant.DEFAULT
                    } else {
                        ButtonVariant.GHOST
                    },
                )
            }
        }
    }
}
