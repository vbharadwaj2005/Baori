package com.baori.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baori.game.ui.components.BaoriButton
import com.baori.game.ui.components.BaoriIconButton
import com.baori.game.ui.components.ButtonSize
import com.baori.game.ui.components.ButtonVariant
import com.baori.game.ui.components.GameDimensions
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.ui.theme.BaoriTypography
import com.baori.game.viewmodel.MenuViewModel

/**
 * Main menu (PRD §7.2 ui/ — MainMenu).
 *
 * The story voice opens the app: title, tagline, then the level list. Level
 * rows show state through shape + color (marigold diamond = goal reached,
 * lock glyph = purchase required) so no mechanic depends on reading text
 * (PRD §9). Settings toggles live here on Day 1; Day 4 may move them into a
 * dedicated screen if the playtests ask for it.
 */
@Composable
fun MainMenuScreen(
    menuState: MenuViewModel.MenuUiState,
    reduceMotion: Boolean,
    muted: Boolean,
    onPlayLevel: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleReduceMotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GameDimensions.screenPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            BaoriIconButton(
                imageVector = if (muted) Icons.Filled.MusicOff else Icons.Filled.MusicNote,
                contentDescription = if (muted) "Unmute ambient sound" else "Mute ambient sound",
                onClick = onToggleMute,
            )
            Spacer(Modifier.height(0.dp))
            BaoriIconButton(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Reduce motion",
                onClick = onToggleReduceMotion,
            )
        }

        Spacer(Modifier.height(48.dp))

        // The title, in the story voice.
        Text(
            text = "बावड़ी",
            style = BaoriTypography.displayLarge,
            color = colors.onBackground,
        )
        Text(
            text = "Baori",
            style = BaoriTypography.displaySmall,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "the well remembers",
            style = BaoriTypography.titleLarge,
            color = colors.accent,
        )

        Spacer(Modifier.height(48.dp))

        menuState.items.forEach { item ->
            LevelRow(
                label = "${item.level.order}",
                playable = item.playable,
                needsPurchase = item.needsPurchase,
                onClick = { onPlayLevel(item.level.order) },
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * One level row. Playable rows are primary buttons; locked rows keep the
 * same silhouette with a lock glyph so the menu stays calm and predictable
 * (design doc §6 "Visual Minimalism").
 */
@Composable
private fun LevelRow(
    label: String,
    playable: Boolean,
    needsPurchase: Boolean,
    onClick: () -> Unit,
) {
    val colors = BaoriTheme.colors
    val shape = RoundedCornerShape(colors.radius.medium)
    val bg = if (playable) colors.card else Color.Transparent
    val fg = if (playable) colors.onCard else colors.onMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bg, shape)
            .border(1.dp, if (playable) colors.border else colors.muted, shape)
            .clickable(enabled = playable, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Roman-style numeral chip — shape carries the state.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(colors.primary.copy(alpha = if (playable) 1f else 0.3f), CircleShape)
                    .padding(10.dp),
            ) {
                Text(
                    text = label,
                    style = BaoriTypography.labelLarge,
                    color = if (playable) colors.onPrimary else colors.onMuted,
                )
            }
            Text(
                text = when {
                    needsPurchase -> "The full journey"
                    else -> "The well"
                },
                style = BaoriTypography.bodyLarge,
                color = fg,
            )
        }
        if (needsPurchase) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Level requires the full journey purchase",
                tint = colors.onMuted,
            )
        }
    }
}
