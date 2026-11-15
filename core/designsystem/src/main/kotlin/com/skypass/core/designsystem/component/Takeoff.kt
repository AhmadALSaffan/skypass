package com.skypass.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.SkypassTheme
import kotlin.math.atan2
import kotlin.math.pow

/**
 * The search "takeoff": a plane that leaves a button, climbs across the whole screen, growing as it
 * comes "closer", and pulls the next page in behind it. Lives at the app root so the plane can fly
 * over every screen and over the page transition.
 *
 * Use: wrap the app in [TakeoffHost]; a button calls `LocalTakeoff.current?.launch(start) { navigate() }`
 * with the plane's centre in root coordinates. [launch]'s callback runs as the plane starts climbing,
 * so the navigation transition plays underneath it.
 */
@Stable
class TakeoffController internal constructor() {
    internal var flight by mutableStateOf<Flight?>(null)
        private set
    private var nextId = 0

    fun launch(start: Offset, onDeparted: () -> Unit) {
        flight = Flight(nextId++, start, onDeparted)
    }

    internal fun land() {
        flight = null
    }

    internal data class Flight(val id: Int, val start: Offset, val onDeparted: () -> Unit)
}

val LocalTakeoff = staticCompositionLocalOf<TakeoffController?> { null }

@Composable
fun TakeoffHost(content: @Composable () -> Unit) {
    val controller = remember { TakeoffController() }
    CompositionLocalProvider(LocalTakeoff provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            controller.flight?.let { flight -> FlightOverlay(flight, onLanded = controller::land) }
        }
    }
}

/** Slow roll, then a strong climb out. */
private val TakeoffEasing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.55f)

@Composable
private fun FlightOverlay(flight: TakeoffController.Flight, onLanded: () -> Unit) {
    val progress = remember(flight.id) { Animatable(0f) }
    val landed by rememberUpdatedState(onLanded)
    val plane = rememberVectorPainter(SkypassIcons.Plane)
    val colors = SkypassTheme.colors
    // White while still over the navy button, then the page's ink once it's out over the content.
    val startTint = colors.onInk
    val skyTint = colors.ink

    LaunchedEffect(flight.id) {
        flight.onDeparted()
        progress.animateTo(1f, tween(DURATION_MS, easing = TakeoffEasing))
        landed()
    }

    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val start = flight.start
        // Quadratic path: a short run along the button, then up and out past the top-right corner.
        val control = Offset(start.x + size.width * 0.42f, start.y - size.height * 0.02f)
        val end = Offset(size.width * 1.25f, -size.height * 0.18f)
        val pos = bezier(start, control, end, p)
        val tangent = bezierTangent(start, control, end, p)
        val angle = Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat() + 90f
        // Grows from icon size to ~11x as it climbs toward the viewer.
        val scale = 1f + 10f * p.pow(1.7f)
        val base = 20.dp.toPx()

        drawContrail(start, control, end, p, skyTint)

        val tint = lerp(startTint, skyTint, (p * 5f).coerceIn(0f, 1f))
        // Shadow on the "ground": offset down-right, softer as the plane gets higher.
        drawPlane(plane, pos + Offset(base * scale * 0.18f, base * scale * 0.32f), angle, base * scale, Color.Black.copy(alpha = 0.10f * p))
        drawPlane(plane, pos, angle, base * scale, tint)
    }
}

private fun DrawScope.drawPlane(painter: VectorPainter, center: Offset, angle: Float, side: Float, color: Color) {
    translate(center.x - side / 2, center.y - side / 2) {
        rotate(angle, pivot = Offset(side / 2, side / 2)) {
            with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(color)) }
        }
    }
}

/** Dashed trail from the runway to the plane, fading as the plane leaves. */
private fun DrawScope.drawContrail(a: Offset, b: Offset, c: Offset, p: Float, color: Color) {
    if (p <= 0f) return
    val path = Path()
    val steps = 32
    path.moveTo(a.x, a.y)
    for (i in 1..steps) {
        val t = p * i / steps
        val point = bezier(a, b, c, t)
        path.lineTo(point.x, point.y)
    }
    drawPath(
        path,
        color.copy(alpha = 0.28f * (1f - p)),
        style = Stroke(
            width = (2f + 6f * p).dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 10.dp.toPx())),
        ),
    )
}

private fun bezier(a: Offset, b: Offset, c: Offset, t: Float): Offset {
    val u = 1 - t
    return a * (u * u) + b * (2 * u * t) + c * (t * t)
}

private fun bezierTangent(a: Offset, b: Offset, c: Offset, t: Float): Offset {
    val d = (b - a) * (2 * (1 - t)) + (c - b) * (2 * t)
    return if (d.getDistanceSquared() < 0.0001f) Offset(1f, 0f) else d
}

private const val DURATION_MS = 1000
