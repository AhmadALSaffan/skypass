package com.skypass.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays [content] out at a fixed design size (the board's own coordinates, e.g. 327×350) and
 * scales it uniformly to fit the space it's given. Illustrations keep their exact composition on
 * every screen size without re-deriving positions.
 */
@Composable
fun FitBox(
    designWidth: Dp,
    designHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Layout(
        content = { Box(Modifier.size(designWidth, designHeight), content = content) },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = designWidth.roundToPx()
        val h = designHeight.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixed(w, h))
        val sx = if (constraints.hasBoundedWidth) constraints.maxWidth / w.toFloat() else 1f
        val sy = if (constraints.hasBoundedHeight) constraints.maxHeight / h.toFloat() else sx
        val scale = min(sx, sy)
        val outW = (w * scale).roundToInt()
        val outH = (h * scale).roundToInt()
        layout(outW, outH) {
            placeable.placeWithLayer((outW - w) / 2, (outH - h) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
