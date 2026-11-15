package com.skypass.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The board's soft navy shadows: big blur with a negative spread so light pools under the card
 * instead of outlining it (CSS `0 y blur -spread rgba(28,37,80,a)`).
 */
enum class SoftShadow(val y: Dp, val blur: Dp, val spread: Dp, val alpha: Float, val tint: Color = ShadowInk) {
    /** Search card: 0 18 36 -24 / .35 */
    Hero(18.dp, 36.dp, (-24).dp, 0.35f),
    /** Ticket and hotel cards: 0 14 26 -20 / .40 */
    Card(14.dp, 26.dp, (-20).dp, 0.40f),
    /** Small cards: 0 12 24 -18 / .30 */
    Low(12.dp, 24.dp, (-18).dp, 0.30f),
    /** Google button: 0 8 20 -14 / .35 */
    Button(8.dp, 20.dp, (-14).dp, 0.35f),
    /** Active segment: 0 2 8 -2 / .18 */
    Segment(2.dp, 8.dp, (-2).dp, 0.18f),
    /** Floating illustration card: 0 24 40 -20 / .45 */
    Float(24.dp, 40.dp, (-20).dp, 0.45f),
    /** Dialog pass: 0 30 60 -20 / black .5 */
    Dialog(30.dp, 60.dp, (-20).dp, 0.5f, Color.Black),
    /** App icon: 0 20 40 -18 teal .7 */
    Brand(20.dp, 40.dp, (-18).dp, 0.7f, Color(0xFF0F5B56)),
}

private val ShadowInk = Color(0xFF1C2550)

fun Modifier.softShadow(shadow: SoftShadow, shape: Shape, enabled: Boolean = true): Modifier =
    if (!enabled) this else dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = shadow.blur,
            spread = shadow.spread,
            color = shadow.tint.copy(alpha = shadow.alpha),
            offset = DpOffset(0.dp, shadow.y),
        ),
    )
