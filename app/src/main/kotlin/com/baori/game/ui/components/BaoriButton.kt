package com.baori.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.ui.theme.BaoriTypography
import com.baori.game.ui.theme.GameShapes

/**
 * Variant-driven Button (design doc §5 "Variant-Driven States").
 *
 * Variants: default (primary emphasis), secondary, outline, ghost,
 * destructive. Sizes: sm, md, lg. Every control is focusable with a visible
 * focus ring (design doc §6 "Consistent Focus Accessibility") and honors the
 * reduce-motion token for its pressed feedback.
 */
enum class ButtonVariant { DEFAULT, SECONDARY, OUTLINE, GHOST, DESTRUCTIVE }

enum class ButtonSize(val heightDp: Int, val horizontalPaddingDp: Int) {
    SM(36, 14),
    MD(48, 22),
    LG(56, 28),
}

@Composable
fun BaoriButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.DEFAULT,
    size: ButtonSize = ButtonSize.MD,
    enabled: Boolean = true,
) {
    val colors = BaoriTheme.colors
    val motion = BaoriTheme.motion

    val bg = when (variant) {
        ButtonVariant.DEFAULT -> colors.primary
        ButtonVariant.SECONDARY -> colors.secondary
        ButtonVariant.OUTLINE -> colors.background
        ButtonVariant.GHOST -> colors.background
        ButtonVariant.DESTRUCTIVE -> colors.destructive
    }
    val fg = when (variant) {
        ButtonVariant.DEFAULT -> colors.onPrimary
        ButtonVariant.SECONDARY -> colors.onSecondary
        ButtonVariant.OUTLINE -> colors.primary
        ButtonVariant.GHOST -> colors.onBackground
        ButtonVariant.DESTRUCTIVE -> colors.onDestructive
    }
    val shape: Shape = when (variant) {
        ButtonVariant.GHOST -> CircleShape
        else -> RoundedCornerShape(colors.radius.medium)
    }

    // Subtle pressed scale — eased, and collapsed entirely under reduce motion.
    val pressScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.97f,
        animationSpec = tween(durationMillis = motion.pressMs.toInt()),
        label = "buttonPressScale",
    )

    FocusableButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = size.heightDp.dp)
            .scale(pressScale),
        shape = shape,
        background = bg,
        contentColor = fg,
        border = if (variant == ButtonVariant.OUTLINE) colors.border else null,
        horizontalPaddingDp = size.horizontalPaddingDp,
    ) {
        Text(
            text = text,
            style = BaoriTypography.labelLarge,
            color = fg,
        )
    }
}

/**
 * Square icon button used for mute / settings / back affordances.
 * [contentDescription] is required — the game has no readable text anywhere,
 * so TalkBack labels are the one place meaning must be spoken (PRD §9).
 */
@Composable
fun BaoriIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BaoriTheme.colors
    FocusableButton(
        onClick = onClick,
        enabled = true,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        background = colors.card,
        contentColor = colors.onCard,
        border = colors.border,
        horizontalPaddingDp = 0,
        semanticRole = Role.Button,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = colors.onCard,
        )
    }
}

/** Back affordance with the required accessibility label. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaoriIconButton(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Go back",
        onClick = onClick,
        modifier = modifier,
    )
}

/** Restore-purchases affordance reused by the Day 2 paywall. */
@Composable
fun RestoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaoriButton(
        text = "Restore",
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.GHOST,
        size = ButtonSize.SM,
    )
}

/** Small icon row used by settings toggles. */
@Composable
fun IconRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative; the label carries meaning
            tint = BaoriTheme.colors.onBackground,
        )
        Text(
            text = label,
            style = BaoriTypography.bodyLarge,
            color = BaoriTheme.colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
