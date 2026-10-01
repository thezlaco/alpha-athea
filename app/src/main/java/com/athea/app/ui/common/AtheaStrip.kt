package com.athea.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A horizontal strip of equal-width cells with thin separators.
 *
 * The one "one-shape" place for equal-cell strips (the key row today):
 * the cell width is derived from the measured viewport, so exactly
 * [visibleCount] cells fill it and extra cells scroll.
 *
 * The viewport division is guarded: while the viewport is unknown or
 * unbounded (first layout pass, Infinity constraint) the strip degrades
 * to zero-width cells instead of propagating Infinity into the scroll
 * state — that is what made the old unguarded variant hang.
 */
@Composable
fun <T> EqualCellStrip(
    items: List<T>,
    visibleCount: Int,
    cellHeight: Dp,
    separatorWidth: Dp,
    separatorHeight: Dp,
    contentPaddingH: Dp,
    contentPaddingV: Dp,
    modifier: Modifier = Modifier,
    separator: @Composable () -> Unit,
    cell: @Composable (T) -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewport = if (maxWidth.value.isFinite() && maxWidth.value > 0f) maxWidth else Dp.Zero
        val fixed = contentPaddingH * 2 + separatorWidth * (visibleCount - 1).coerceAtLeast(0)
        val cellWidth = if (visibleCount <= 0 || viewport <= fixed) Dp.Zero else (viewport - fixed) / visibleCount
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = contentPaddingH, vertical = contentPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    // The strip owns separator geometry so callers only
                    // describe the separator's appearance.
                    Box(
                        Modifier
                            .width(separatorWidth)
                            .height(separatorHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        separator()
                    }
                }
                Box(
                    Modifier
                        .width(cellWidth)
                        .height(cellHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    cell(item)
                }
            }
        }
    }
}
