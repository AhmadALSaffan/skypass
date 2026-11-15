package com.skypass.feature.confirmation

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PageDots
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.icon.GoogleWalletMark
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.BoardingPass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ConfirmationRoute(onHome: () -> Unit, viewModel: ConfirmationViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ConfirmationScreen(uiState, onPrinted = viewModel::onPrinted, onRetry = viewModel::retry, onHome = onHome)
}

@Composable
internal fun ConfirmationScreen(
    uiState: ConfirmationUiState,
    onPrinted: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    val colors = SkypassTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        AnimatedContent(
            targetState = when {
                uiState.failed -> Stage.Failed
                uiState.loading || uiState.passes.isEmpty() -> Stage.Warming
                !uiState.printed -> Stage.Printing
                else -> Stage.Done
            },
            transitionSpec = {
                if (targetState == Stage.Done) {
                    (fadeIn(tween(Motion.ENTER)) + slideInVertically(tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate)) { it / 12 }) togetherWith
                        (fadeOut(tween(Motion.EXIT)) + slideOutVertically { -it / 10 })
                } else {
                    fadeIn(tween(Motion.ENTER)) togetherWith fadeOut(tween(Motion.EXIT))
                }
            },
            label = "confirmation",
        ) { stage ->
            when (stage) {
                Stage.Warming -> PrintingStage(passes = emptyList(), onDone = {})
                Stage.Printing -> PrintingStage(passes = uiState.passes, onDone = onPrinted)
                Stage.Done -> DoneStage(uiState, onHome)
                Stage.Failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.confirmation_load_error), style = SkypassTheme.typography.title, color = colors.ink)
                    SkypassTextButton(stringResource(R.string.confirmation_retry), onRetry, color = colors.brand)
                }
            }
        }
    }
}

private enum class Stage { Warming, Printing, Done, Failed }

// ---------------------------------------------------------------------------------------------
// The printer
// ---------------------------------------------------------------------------------------------

private const val FEED_STEPS = 16

/**
 * A boarding-pass printer. Each traveller's pass feeds out of the slot in short motor steps (with a
 * haptic tick per step and the print head glowing teal), then tears off with a little jolt. The
 * next pass follows; after the last one the screen settles into the confirmation.
 * With system animations off, everything completes instantly.
 */
@Composable
private fun PrintingStage(passes: List<BoardingPass>, onDone: () -> Unit) {
    val colors = SkypassTheme.colors
    val haptics = LocalHapticFeedback.current
    val inspection = LocalInspectionMode.current
    val feed = remember { Animatable(if (inspection) 1f else 0f) }
    val tear = remember { Animatable(0f) }
    var current by remember { mutableIntStateOf(0) }
    val printing = passes.isNotEmpty()

    LaunchedEffect(passes) {
        if (passes.isEmpty() || inspection) return@LaunchedEffect
        for (i in passes.indices) {
            current = i
            feed.snapTo(0f)
            tear.snapTo(0f)
            delay(if (i == 0) 450 else 250)
            for (step in 1..FEED_STEPS) {
                feed.animateTo(step / FEED_STEPS.toFloat(), tween(85, easing = LinearEasing))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(45)
            }
            delay(260)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            tear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            delay(if (i == passes.lastIndex) 450 else 350)
        }
        onDone()
    }

    val blink by rememberInfiniteTransition(label = "led").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "blink",
    )

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        val cardWidth = maxWidth - 8.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text(
                if (printing) stringResource(R.string.confirmation_printing, current + 1, passes.size) else stringResource(R.string.confirmation_preparing),
                style = SkypassTheme.typography.eyebrow,
                color = colors.text2,
            )
            Spacer(Modifier.height(14.dp))

            // Printer body with its slot and status light.
            Box(
                Modifier
                    .width(cardWidth + 28.dp)
                    .height(70.dp)
                    .softShadow(SoftShadow.Card, RoundedCornerShape(22.dp))
                    .background(Color(0xFF1C2550), RoundedCornerShape(22.dp)),
            ) {
                Row(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Skypass", style = SkypassTheme.typography.labelSm, color = Color.White.copy(alpha = 0.85f))
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 18.dp, top = 18.dp)
                        .size(8.dp)
                        .graphicsLayer { alpha = if (printing) blink else 0.35f }
                        .background(Color(0xFF5FC2B6), CircleShape),
                )
                // The slot
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .width(cardWidth + 6.dp)
                        .height(8.dp)
                        .background(Color(0xFF0B0E1E), RoundedCornerShape(4.dp)),
                )
            }

            // Paper coming out: the pass is laid out at full height but only the fed part is shown,
            // so it slides down out of the slot.
            Box(Modifier.width(cardWidth).weight(1f, fill = false)) {
                val pass = passes.getOrNull(current)
                if (pass != null) {
                    val t = tear.value
                    BoardingPassCard(
                        pass = pass,
                        notchColor = colors.bg,
                        modifier = Modifier
                            .graphicsLayer {
                                // The tear: a jolt down with a small wobble.
                                translationY = 16.dp.toPx() * t
                                rotationZ = 2.5f * sin(t * PI).toFloat()
                                alpha = if (current < passes.lastIndex) 1f - (t * 0.15f) else 1f
                            }
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(maxHeight = Int.MAX_VALUE))
                                val visible = (placeable.height * feed.value).roundToInt()
                                layout(placeable.width, visible) { placeable.place(0, visible - placeable.height) }
                            },
                    )
                    // The print head's glow right under the slot while feeding.
                    if (feed.value in 0.001f..0.999f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .graphicsLayer { alpha = 0.6f + 0.4f * blink }
                                .background(colors.brand),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Done: confirmation + passes
// ---------------------------------------------------------------------------------------------

@Composable
private fun DoneStage(uiState: ConfirmationUiState, onHome: () -> Unit) {
    val colors = SkypassTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { uiState.passes.size }
    val walletSoon = stringResource(R.string.confirmation_wallet_soon)
    FullBrightness()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SuccessBadge()
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.confirmation_title),
                style = SkypassTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            val pnrs = uiState.pnrs.joinToString(" · ")
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.confirmation_booking_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink)) { append(pnrs) }
                    append(stringResource(R.string.confirmation_booking_suffix))
                    uiState.email?.let { append(stringResource(R.string.confirmation_emailed, it)) }
                },
                style = SkypassTheme.typography.bodySm,
                color = colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(20.dp))

            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 12.dp,
            ) { page ->
                BoardingPassCard(
                    uiState.passes[page],
                    notchColor = colors.bg,
                    modifier = Modifier.softShadow(SoftShadow.Dialog, RoundedCornerShape(26.dp)),
                )
            }
            if (uiState.passes.size > 1) {
                PageDots(uiState.passes.size, pager.currentPage, onDotClick = { scope.launch { pager.animateScrollToPage(it) } })
            }
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Row(
                    Modifier
                        .weight(1f)
                        .pressScale(interaction)
                        .height(54.dp)
                        .background(Color.Black, Shapes.button)
                        .clickable(interaction, null, role = Role.Button) { scope.launch { snackbar.showSnackbar(walletSoon) } },
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(rememberVectorPainter(GoogleWalletMark), contentDescription = null, modifier = Modifier.size(width = 24.dp, height = 22.dp))
                    Text(stringResource(R.string.confirmation_wallet), style = SkypassTheme.typography.label, color = Color.White)
                }
                IconCircleButton(
                    SkypassIcons.Share,
                    stringResource(R.string.confirmation_share),
                    onClick = { uiState.passes.getOrNull(pager.currentPage)?.let { share(context, it) } },
                    size = 54.dp,
                )
            }
            SkypassTextButton(stringResource(R.string.confirmation_home), onHome, color = colors.text2)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Snackbar(it, containerColor = colors.ink, contentColor = colors.onInk, shape = Shapes.sm)
        }
    }
}

/** Mint disc with a teal circle; the check draws itself in, then the badge pops. */
@Composable
private fun SuccessBadge() {
    val colors = SkypassTheme.colors
    val inspection = LocalInspectionMode.current
    val draw = remember { Animatable(if (inspection) 1f else 0f) }
    val pop = remember { Animatable(if (inspection) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        delay(150)
        draw.animateTo(1f, tween(420, easing = Motion.EmphasizedDecelerate))
    }
    Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.background(colors.mint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(52.dp).background(colors.brand, CircleShape), contentAlignment = Alignment.Center) {
            val measure = remember { PathMeasure() }
            Canvas(Modifier.size(26.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.17f, size.height * 0.52f)
                    lineTo(size.width * 0.40f, size.height * 0.74f)
                    lineTo(size.width * 0.84f, size.height * 0.27f)
                }
                measure.setPath(path, false)
                val partial = Path()
                measure.getSegment(0f, measure.length * draw.value, partial, true)
                drawPath(partial, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Boarding passes are scanned at the gate: full screen brightness while they're shown. */
@Composable
private fun FullBrightness() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = before } }
    }
}

private val ShareDate = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

private fun share(context: android.content.Context, pass: BoardingPass) {
    val text = "Skypass booking ${pass.pnr}: ${pass.airlineName} ${pass.flightNumber}, " +
        "${pass.originCity} (${pass.originCode}) → ${pass.destinationCity} (${pass.destinationCode}), " +
        "${pass.departure.format(ShareDate)}. ${pass.passengerName}, seat ${pass.seat}, gate ${pass.gate}."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
