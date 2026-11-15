package com.skypass.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Skypass motion: confident and airy. Arrivals rise and settle (emphasized decelerate), playful
 * moments get a spring with a little overshoot (the swap button, a seat being picked). Compose
 * follows the system animation scale, so "Remove animations" turns all of it off.
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** The board's swap-button curve: cubic-bezier(.3,1.4,.5,1) — a small overshoot. */
    val Overshoot = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)

    const val ENTER = 420
    const val EXIT = 180
    const val SCREEN = 360
    const val COUNT = 900
    const val STAGGER = 55
    const val MAX_STAGGER_STEPS = 8
}

// ---------------------------------------------------------------------------------------------
// Timeline helpers — the "motion video" technique. One linear clock per scene; every element
// compares the clock with its own start time and that boolean drives a spring. The clock gives
// the choreography, the springs give the feel.
// ---------------------------------------------------------------------------------------------

/**
 * A linear clock in milliseconds that runs from 0 to [durationMs] while [active] and snaps back to
 * 0 when not, so a scene replays every time it comes back. Previews show the final frame.
 */
@Composable
fun rememberTimeline(active: Boolean, durationMs: Int): Float {
    val inspection = LocalInspectionMode.current
    val time = remember { Animatable(if (inspection) durationMs.toFloat() else 0f) }
    LaunchedEffect(active) {
        if (inspection) return@LaunchedEffect
        if (active) {
            time.snapTo(0f)
            time.animateTo(durationMs.toFloat(), tween(durationMs, easing = LinearEasing))
        } else {
            time.snapTo(0f)
        }
    }
    return time.value
}

/** 0→1 with a calm settle. */
@Composable
fun appear(shown: Boolean): Float = animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
    label = "appear",
).value

/** 0→1 with a small overshoot — for badges, stamps, the moment something "clicks". */
@Composable
fun pop(shown: Boolean): Float = animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    label = "pop",
).value

/** 0→1 linear-ish progress over [durationMs] once [shown] — for drawing paths and wipes. */
@Composable
fun draw(shown: Boolean, durationMs: Int = 700): Float = animateFloatAsState(
    targetValue = if (shown) 1f else 0f,
    animationSpec = if (shown) tween(durationMs, easing = Motion.Emphasized) else tween(0),
    label = "draw",
).value

/** Fades in while rising [distance] dp into place as [progress] goes 0→1. */
fun Modifier.rise(progress: Float, distance: Float = 18f): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    translationY = (1f - progress) * distance.dp.toPx()
}

/** Scales from [from] to 1 with the alpha following, driven by a [pop] value. */
fun Modifier.popIn(progress: Float, from: Float = 0.6f): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    val s = from + (1f - from) * progress
    scaleX = s
    scaleY = s
}

/** Typing caret blink: on for 450 ms, off for 450 ms. */
fun caretOn(timeMs: Float): Boolean = (timeMs / 450f).toInt() % 2 == 0

/** Characters of [text] typed by [timeMs] when typing starts at [startMs] at [msPerChar]. */
fun typed(text: String, timeMs: Float, startMs: Int, msPerChar: Int = 60): String {
    val n = ((timeMs - startMs) / msPerChar).toInt().coerceIn(0, text.length)
    return text.take(n)
}

// ---------------------------------------------------------------------------------------------
// Everyday motion
// ---------------------------------------------------------------------------------------------

/**
 * Fades the element in while it rises into place the first time it's shown. [index] staggers
 * siblings so a screen builds top to bottom. Survives rotation without replaying.
 */
fun Modifier.reveal(index: Int = 0): Modifier = composed {
    val shown = rememberSaveable { booleanArrayOf(false) }
    val progress = remember { Animatable(if (shown[0]) 1f else 0f) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay((index.coerceIn(0, Motion.MAX_STAGGER_STEPS) * Motion.STAGGER).toLong())
            progress.animateTo(1f, tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate))
        }
        shown[0] = true
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise
    }
}

/** The element settles slightly under the finger. Pass the same source to its `clickable`. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = if (isPressed) tween(Durations.FAST, easing = Motion.Emphasized)
        else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Counts from 0 to [target] when first shown, then animates between later values. */
@Composable
fun animateCountUp(target: Int, durationMs: Int = Motion.COUNT): Int {
    val inspection = LocalInspectionMode.current
    val value = remember { Animatable(if (inspection) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        value.animateTo(target.toFloat(), tween(durationMs, easing = Motion.Emphasized))
    }
    return value.value.roundToInt()
}
