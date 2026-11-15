package com.skypass.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.component.AnzKoru
import com.skypass.core.designsystem.component.AnzWordmark
import com.skypass.core.designsystem.component.Barcode
import com.skypass.core.designsystem.component.DashedRoute
import com.skypass.core.designsystem.component.Perforation
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.DmMono
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.softShadow
import kotlin.math.atan2
import kotlin.math.roundToInt

/*
 * Building blocks for the onboarding scenes. The pass and chips are "paper" (white, navy ink)
 * in light and dark mode alike, so their colours are fixed; anything drawn straight on the app
 * background (the arc, its labels) takes a themed ink. All positions are in the board's 327×350 hero coordinates.
 */

internal val PaperInk = Color(0xFF1C2550)
internal val PaperInk2 = Color(0xFF3D4670)
internal val PaperMuted = Color(0xFF5B6386)
internal val PaperLine = Color(0xFFC6CADB)
internal val PaperPerforation = Color(0xFFD7DAE6)
internal val PaperTeal = Color(0xFF0F5B56)
internal val PaperMint = Color(0xFFCDEBE6)

private val PassShape = RoundedCornerShape(22.dp)

/** Progress 0→1 between [startMs] and [startMs]+[durationMs] on the scene clock, eased. */
internal fun eased(timeMs: Float, startMs: Int, durationMs: Int): Float =
    Motion.Emphasized.transform(((timeMs - startMs) / durationMs).coerceIn(0f, 1f))

/** Same, without easing — for sweeps that should feel mechanical (barcode scanner). */
internal fun linear(timeMs: Float, startMs: Int, durationMs: Int): Float =
    ((timeMs - startMs) / durationMs).coerceIn(0f, 1f)

/** Parallax: the layer lags the page by [factor] of the hero width. [pageOffset] is read at draw time. */
internal fun Modifier.parallax(pageOffset: () -> Float, factor: Float): Modifier = graphicsLayer {
    translationX = pageOffset() * 327.dp.toPx() * factor
}

/** Reports only [fraction] of the child's height, clipping the rest — for "unfolding" content. */
internal fun Modifier.unfold(fraction: Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * fraction.coerceIn(0f, 1f)).roundToInt()) {
        placeable.place(0, 0)
    }
}

internal fun Modifier.at(x: Int, y: Int): Modifier = offset(x.dp, y.dp)

/**
 * The board's dotted flight path, M34 300 C 90 150, 230 220, 290 56, drawn to [draw] with a
 * plane riding its tip and turned along the tangent.
 */
@Composable
internal fun FlightArc(
    draw: Float,
    modifier: Modifier = Modifier,
    planeAlpha: Float = 0f,
    startDot: Float = 1f,
    endRing: Float = 1f,
    ink: Color = PaperInk,
) {
    val plane = rememberVectorPainter(SkypassIcons.Plane)
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        fun p(v: Float) = v.dp.toPx()
        val path = Path().apply {
            moveTo(p(34f), p(300f))
            cubicTo(p(90f), p(150f), p(230f), p(220f), p(290f), p(56f))
        }
        measure.setPath(path, false)
        val length = measure.length
        val drawn = length * draw.coerceIn(0f, 1f)
        segment.reset()
        measure.getSegment(0f, drawn, segment, true)
        drawPath(
            segment,
            ink.copy(alpha = 0.35f),
            style = Stroke(
                width = p(2f),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(p(0.5f), p(9.5f))),
            ),
        )
        drawCircle(ink, p(5f) * startDot, Offset(p(34f), p(300f)))
        if (endRing > 0f) {
            drawCircle(ink, p(5f) * endRing, Offset(p(290f), p(56f)), style = Stroke(p(2f)))
        }
        if (planeAlpha > 0f && drawn > 0f) {
            val pos = measure.getPosition(drawn)
            val tan = measure.getTangent(drawn)
            val angle = Math.toDegrees(atan2(tan.y, tan.x).toDouble()).toFloat() + 90f
            val half = p(10f)
            translate(pos.x - half, pos.y - half) {
                rotate(angle, pivot = Offset(half, half)) {
                    with(plane) {
                        draw(Size(half * 2, half * 2), alpha = planeAlpha, colorFilter = ColorFilter.tint(ink))
                    }
                }
            }
        }
    }
}

/** DM Mono airport label on the arc (`from` bottom-left, `to` top-right). */
@Composable
internal fun ArcLabel(text: String, modifier: Modifier = Modifier, color: Color = PaperInk) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.04.em),
        color = color,
    )
}

/** Pastel card tilted behind the pass — the slide's accent colour. */
@Composable
internal fun GhostCard(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(232.dp, 172.dp).background(color, PassShape))
}

/**
 * The boarding pass from the board (254dp): ANZ marks + flight, FROM ── ✈ ── TO, perforation,
 * GATE / SEAT and a barcode. Every part can be driven by the scene clock.
 */
@Composable
internal fun PaperPass(
    from: String,
    to: String,
    notchColor: Color,
    modifier: Modifier = Modifier,
    flight: String = "NZ 2",
    gate: String = "A12",
    seat: String = "14C",
    price: String? = null,
    route: Float = 1f,
    unfold: Float = 1f,
    perforation: Float = 1f,
    scan: Float = 0f,
    gateHighlight: Float = 0f,
    shadow: Boolean = true,
) {
    Column(
        modifier
            .width(254.dp)
            .softShadow(SoftShadow.Float, PassShape, enabled = shadow)
            .clip(PassShape)
            .background(Color.White),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AnzWordmark(height = 11.dp, tint = PaperInk)
            Spacer(Modifier.width(6.dp))
            AnzKoru(size = 18.dp, tint = PaperInk)
            Spacer(Modifier.weight(1f))
            if (price != null) {
                Text(price, style = codeStyle(16), color = PaperInk)
            } else {
                Text(flight, style = monoStyle(10), color = PaperMuted)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(62.dp)) { Text(from, style = codeStyle(26), color = PaperInk) }
            DashedRoute(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                color = PaperInk,
                dashColor = PaperLine,
                progress = route,
                planeBackground = Color.White,
            )
            Box(Modifier.width(62.dp), contentAlignment = Alignment.CenterEnd) { Text(to, style = codeStyle(26), color = PaperInk) }
        }
        Column(Modifier.unfold(unfold)) {
            Perforation(notchColor = notchColor, height = 16.dp, dashColor = PaperPerforation, progress = perforation)
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PassField("Gate", gate, highlight = gateHighlight)
                Spacer(Modifier.width(18.dp))
                PassField("Seat", seat)
                Spacer(Modifier.weight(1f))
                Barcode(Modifier.size(84.dp, 26.dp), color = PaperInk, scan = scan)
            }
        }
    }
}

@Composable
private fun PassField(label: String, value: String, highlight: Float = 0f) {
    Column {
        Text(label.uppercase(), style = monoStyle(9).copy(letterSpacing = 0.08.em), color = PaperMuted)
        Text(
            value,
            modifier = Modifier
                .padding(top = 2.dp)
                .background(lerp(Color.Transparent, PaperMint, highlight.coerceIn(0f, 1f)), RoundedCornerShape(5.dp))
                .padding(horizontal = (3 * highlight.coerceIn(0f, 1f)).dp),
            style = codeStyle(13).copy(fontWeight = FontWeight.Bold),
            color = lerp(PaperInk, PaperTeal, highlight.coerceIn(0f, 1f)),
        )
    }
}

internal fun codeStyle(size: Int) = TextStyle(
    fontFamily = JakartaSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = (size * 1.1f).sp,
    letterSpacing = (-0.02).em,
)

internal fun monoStyle(size: Int) = TextStyle(fontFamily = DmMono, fontSize = size.sp, lineHeight = (size * 1.3f).sp)
