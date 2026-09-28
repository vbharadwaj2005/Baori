package com.baori.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.baori.game.ui.theme.BaoriTheme

/**
 * Focus ring modifier (design doc §6 "Consistent Focus Accessibility"):
 * every interactive control draws the semantic ring token when focused, so
 * keyboard/d-pad navigation is always visible.
 */
fun Modifier.baoriFocusRing(visible: Boolean, ringColor: Color, shape: Shape): Modifier =
    then(
        if (visible) {
            Modifier.border(2.dp, ringColor, shape)
        } else {
            Modifier
        },
    )

/**
 * The one low-level pressable surface every button variant builds on
 * (design doc §1 "Composition over Configuration": BaoriButton composes
 * this primitive instead of each variant restating tap/focus behavior).
 *
 * Handles: background, optional border, focus ring, disabled alpha, and
 * semantics role. Content layout is left to the caller.
 */
@Composable
fun FocusableButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape,
    background: Color,
    contentColor: Color,
    border: Color? = null,
    horizontalPaddingDp: Int,
    semanticRole: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = BaoriTheme.colors
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .alpha(alpha = if (enabled) 1f else 0.5f)
            .background(background, shape)
            .baoriFocusRing(visible = focused, ringColor = colors.ring, shape = shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = semanticRole, onClick = onClick)
            .then(
                if (border != null) {
                    Modifier.border(1.dp, border, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = horizontalPaddingDp.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Convenience for a rounded content tile with card tokens (menus, paywall). */
@Composable
fun TiledSurface(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = BaoriTheme.colors
    val shape = RoundedCornerShape(colors.radius.large)
    Row(
        modifier = modifier
            .background(colors.card, shape)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
