package com.skypass.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.skypass.core.designsystem.component.LocalTakeoff
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.model.Cabin
import kotlinx.coroutines.launch

/** CABIN field with a dropdown of the four cabins; the chevron flips while it's open. */
@Composable
internal fun CabinField(
    cabin: Cabin,
    expanded: Boolean,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(Durations.SLOW, easing = Motion.Emphasized), label = "chevron")
    Box(modifier) {
        Field(stringResource(R.string.home_cabin), Modifier.fillMaxWidth(), onClick = { onEvent(HomeEvent.OpenCabinMenu) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cabin.label, style = SkypassTheme.typography.label, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(
                    SkypassIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevron },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onEvent(HomeEvent.DismissCabinMenu) },
            offset = DpOffset(0.dp, 6.dp),
            shape = Shapes.card,
            containerColor = colors.surface,
            modifier = Modifier.width(260.dp),
        ) {
            Cabin.entries.forEach { option ->
                val selected = option == cabin
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(option.label, style = SkypassTheme.typography.label, color = colors.ink)
                            Text(stringResource(option.note()), style = SkypassTheme.typography.caption, color = colors.text2)
                        }
                    },
                    trailingIcon = if (selected) {
                        { Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = { onEvent(HomeEvent.CabinSelected(option)) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(Shapes.tile)
                        .background(if (selected) colors.surfaceSunken else colors.surface),
                )
            }
        }
    }
}

private fun Cabin.note(): Int = when (this) {
    Cabin.Economy -> R.string.home_cabin_economy_note
    Cabin.Premium -> R.string.home_cabin_premium_note
    Cabin.Business -> R.string.home_cabin_business_note
    Cabin.First -> R.string.home_cabin_first_note
}

/**
 * "Search flights" that takes off: on tap the magnifier turns into a plane, the plane taxis right
 * and climbs out of the button leaving a dashed contrail, and only then the results open.
 * Resets itself when the screen comes back. With animations off it all completes instantly.
 */
@Composable
internal fun TakeoffSearchButton(
    text: String,
    enabled: Boolean,
    onTakeoff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val morph = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    var running by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val takeoff = LocalTakeoff.current
    /** Where the icon sits on screen, so the full-screen flight starts exactly there. */
    var iconCenter by remember { mutableStateOf(Offset.Zero) }
    /** The plane has left the button for the full-screen flight. */
    var handedOff by remember { mutableStateOf(false) }

    // Back on Home after the results: park the plane and show the magnifier again.
    LifecycleResumeEffect(Unit) {
        if (running) {
            scope.launch {
                fly.snapTo(0f)
                morph.snapTo(0f)
                handedOff = false
                running = false
            }
        }
        onPauseOrDispose { }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(56.dp)) {
        val density = LocalDensity.current
        // From the icon's resting spot to past the right edge, climbing out over the card.
        val runway = with(density) { (maxWidth * 0.62f).toPx() }
        val climb = with(density) { 86.dp.toPx() }

        Box(
            Modifier
                .matchParentSize()
                .pressScale(interaction)
                .clip(Shapes.button)
                .background(if (enabled) colors.ink else colors.borderStrong)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !running,
                    role = Role.Button,
                    onClickLabel = text,
                ) {
                    running = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch {
                        morph.animateTo(1f, tween(240, easing = Motion.Emphasized))
                        if (takeoff != null) {
                            // Hand the plane to the app-wide flight: it climbs across the screen,
                            // growing, while the results page slides in beneath it.
                            handedOff = true
                            launch { fly.animateTo(1f, tween(260)) } // label fades out
                            takeoff.launch(iconCenter) { onTakeoff() }
                        } else {
                            fly.animateTo(1f, tween(560, easing = Motion.EmphasizedAccelerate))
                            onTakeoff()
                        }
                    }
                },
        )

        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .onGloballyPositioned { iconCenter = it.boundsInRoot().center },
                contentAlignment = Alignment.Center,
            ) {
                val m = morph.value
                val f = fly.value
                // Magnifier shrinks and turns away…
                Icon(
                    SkypassIcons.Search,
                    contentDescription = null,
                    tint = colors.onInk,
                    modifier = Modifier.size(19.dp).graphicsLayer {
                        alpha = 1f - m
                        val s = 1f - 0.5f * m
                        scaleX = s
                        scaleY = s
                        rotationZ = -90f * m
                    },
                )
                // …as the plane turns in, nose to the right, then takes off.
                Icon(
                    SkypassIcons.Plane,
                    contentDescription = null,
                    tint = colors.onInk,
                    modifier = Modifier
                        .size(20.dp)
                        .drawBehind {
                            if (f > 0f && !handedOff) {
                                // Contrail from where the plane started to where it is now.
                                // Drawn before the plane's transform, so (0,0) is where it started.
                                val origin = Offset(size.width / 2, size.height / 2)
                                drawLine(
                                    color = colors.onInk.copy(alpha = 0.55f * (1f - f * 0.6f)),
                                    start = origin,
                                    end = origin + Offset(runway * f, -climb * f * f),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                                )
                            }
                        }
                        .graphicsLayer {
                            alpha = if (handedOff) 0f else m
                            val f = if (handedOff) 0f else f
                            val s = lerp(0.4f, 1f, m) + 0.35f * f
                            scaleX = s
                            scaleY = s
                            // Nose right (90°), lifting toward up-right as it climbs.
                            rotationZ = lerp(0f, 90f, m) - 38f * f
                            translationX = runway * f
                            translationY = -climb * f * f
                        },
                )
            }
            Text(
                text,
                style = SkypassTheme.typography.label,
                color = colors.onInk,
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - fly.value * 1.6f).coerceIn(0f, 1f)
                    translationX = 18.dp.toPx() * fly.value
                },
            )
        }
    }
}
