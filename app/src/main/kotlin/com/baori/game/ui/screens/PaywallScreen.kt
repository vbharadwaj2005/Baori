package com.baori.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baori.game.ui.components.BaoriButton
import com.baori.game.ui.components.BackButton
import com.baori.game.ui.components.ButtonSize
import com.baori.game.ui.components.ButtonVariant
import com.baori.game.ui.components.GameDimensions
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.ui.theme.BaoriTypography
import com.baori.game.viewmodel.PaywallViewModel

/**
 * Paywall (PRD §8.2 step 3): shown once, at the end of Level 3, with the
 * pot/well art motif — never a generic system paywall.
 *
 * PRD §8.3 hard rules honored here: no fake urgency, no countdowns, no
 * dismiss-blocking; Restore is always visible and functional. The price line
 * comes from the loaded RevenueCat package so the Test Store dialog and this
 * screen always agree.
 */
@Composable
fun PaywallScreen(
    state: PaywallViewModel.PaywallUiState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = GameDimensions.screenPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Back affordance at the top — the paywall never traps the player.
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            BackButton(onClick = onDismiss)
        }

        Spacer(Modifier.height(32.dp))

        // The art motif: a marigold pot over the well's descending steps,
        // drawn from tokens so it follows light/dark automatically.
        androidx.compose.foundation.Canvas(modifier = Modifier.size(140.dp)) {
            val c = center
            drawCircle(colors.goal, 34f, androidx.compose.ui.geometry.Offset(c.x, c.y - 30f))
            for (i in 0..3) {
                val w = 120f - i * 28f
                drawRect(
                    colors.pathLit,
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - w / 2f, c.y + i * 18f),
                    size = androidx.compose.ui.geometry.Size(w, 10f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "The rest of the well",
            style = BaoriTypography.displaySmall,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Two more levels. The water remembers the way down.",
            style = BaoriTypography.bodyLarge,
            color = colors.onMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        BaoriButton(
            text = when {
                state.isLoading -> "…"
                state.priceText != null -> "Unlock the Full Journey — ${state.priceText}"
                else -> "Unlock the Full Journey"
            },
            onClick = onPurchase,
            enabled = !state.isLoading && !state.isUnlocked,
            variant = ButtonVariant.DEFAULT,
            size = ButtonSize.LG,
        )

        Spacer(Modifier.height(12.dp))

        // PRD §8.2 step 5: Restore always visible on the paywall.
        BaoriButton(
            text = "Restore purchases",
            onClick = onRestore,
            enabled = !state.isLoading,
            variant = ButtonVariant.GHOST,
            size = ButtonSize.SM,
        )

        state.errorText?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = BaoriTypography.bodySmall,
                color = colors.destructive,
                textAlign = TextAlign.Center,
            )
        }

        if (state.isUnlocked) {
            Spacer(Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "The journey is whole again.",
                style = BaoriTypography.bodyLarge,
                color = colors.accent,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
