package com.baori.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baori.game.ui.theme.BaoriTheme

/**
 * ScreenContainer (design doc §3 "Responsive Containment"): central
 * horizontal padding, a defined maximum reading width, and centered
 * alignment for every screen — one place to keep the whole app consistent.
 */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    maxWidthDp: Int = 560,
    horizontalPaddingDp: Int = 24,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BaoriTheme.colors.background)
            .padding(horizontal = horizontalPaddingDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidthDp.dp)
                .fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * Card (design doc §5 "Compound Sub-Component Breakdown"): header, title,
 * content, and footer as modular slots so screens assemble custom layouts
 * from shared primitives instead of one monolithic composable.
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    header: (@Composable RowScope.() -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BaoriTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(colors.radius.large))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = header,
            )
        }
        title?.invoke()
        content()
        if (footer != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}
