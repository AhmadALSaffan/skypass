package com.skypass.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.FitBox
import com.skypass.core.designsystem.component.PageDots
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.Wordmark
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.preview.ThemePreviews
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.SkypassTheme
import kotlin.math.abs

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.Finished -> finished()
            }
        }
    }
    OnboardingScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

private data class Slide(val title: Int, val body: Int)

private val Slides = listOf(
    Slide(R.string.onboarding_1_title, R.string.onboarding_1_body),
    Slide(R.string.onboarding_2_title, R.string.onboarding_2_body),
    Slide(R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = uiState.currentPage) { uiState.pageCount }
    PagerSync(pagerState, uiState.currentPage, onEvent)
    BackHandler(enabled = uiState.currentPage > 0) { onEvent(OnboardingEvent.Back) }

    // Chrome follows the pager so it reacts mid-swipe.
    val visiblePage = pagerState.currentPage
    val isLastPage = visiblePage == uiState.pageCount - 1
    val accents = with(SkypassTheme.colors) { listOf(mint, lavender, peach) }
    // Each slide's pastel accent blends continuously with the swipe, not per page.
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val accent = blend(accents, position)

    Column(
        modifier
            .fillMaxSize()
            .background(SkypassTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(showSkip = !isLastPage, onSkip = { onEvent(OnboardingEvent.Skip) })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction }
            OnboardingPage(
                index = page,
                count = uiState.pageCount,
                slide = Slides[page],
                pageOffset = pageOffset,
            ) {
                val active = visiblePage == page
                when (page) {
                    0 -> SearchScene(active, accent, pageOffset)
                    1 -> ResultScene(active, accent, pageOffset)
                    else -> PocketScene(active, accent, pageOffset)
                }
            }
        }

        Footer(
            pageCount = uiState.pageCount,
            currentPage = visiblePage,
            isLastPage = isLastPage,
            finishing = uiState.finishing,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun Header(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = showSkip, enter = fadeIn(), exit = fadeOut()) {
            SkypassTextButton(stringResource(R.string.onboarding_skip), onSkip)
        }
    }
}

@Composable
private fun OnboardingPage(
    index: Int,
    count: Int,
    slide: Slide,
    pageOffset: () -> Float,
    scene: @Composable () -> Unit,
) {
    // Scene + text are centred as one group; on short phones the scene shrinks to make room.
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        // No card and no side gutter: the scene runs nearly edge to edge on the app background.
        // The 327×350 board scene is framed at 304×330 (its empty margins cropped) so it scales
        // up as large as the screen allows.
        FitBox(
            designWidth = 304.dp,
            designHeight = 330.dp,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Box(Modifier.requiredSize(327.dp, 350.dp).align(Alignment.Center)) { scene() }
        }
        Spacer(Modifier.height(24.dp))
        // Text trails the swipe slightly, at two speeds, and fades as it leaves.
        Column(
            Modifier.padding(horizontal = 24.dp).graphicsLayer {
                val o = pageOffset()
                translationX = o * size.width * 0.18f
                alpha = 1f - abs(o).coerceIn(0f, 1f) * 0.7f
            },
        ) {
            Text(
                stringResource(R.string.onboarding_step, index + 1, count),
                style = SkypassTheme.typography.mono.copy(fontSize = 12.sp),
                color = SkypassTheme.colors.brand,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(slide.title),
                style = SkypassTheme.typography.display,
                color = SkypassTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
                minLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(slide.body),
                style = SkypassTheme.typography.body,
                color = SkypassTheme.colors.text2,
                minLines = 2,
                modifier = Modifier.graphicsLayer { translationX = pageOffset() * size.width * 0.08f },
            )
        }
    }
}

@Composable
private fun Footer(
    pageCount: Int,
    currentPage: Int,
    isLastPage: Boolean,
    finishing: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageDots(pageCount, currentPage, onDotClick = { onEvent(OnboardingEvent.DotClicked(it)) })
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = isLastPage,
            transitionSpec = {
                (fadeIn(tween(Durations.BASE)) + slideInVertically { it / 3 }) togetherWith
                    (fadeOut(tween(Durations.FAST)) + slideOutVertically { -it / 3 })
            },
            label = "cta",
        ) { last ->
            PrimaryButton(
                text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next),
                onClick = { onEvent(OnboardingEvent.Primary) },
                trailingIcon = SkypassIcons.ArrowRight,
                loading = finishing,
                modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            )
        }
    }
}

/** Keeps the pager and the ViewModel's page in step, in both directions. */
@Composable
private fun PagerSync(pagerState: PagerState, targetPage: Int, onEvent: (OnboardingEvent) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnEvent(OnboardingEvent.PageSettled(it)) }
    }
}

/** Colour at a fractional position along [stops] (0 → first, 1 → second, …). */
private fun blend(stops: List<Color>, position: Float): Color {
    val p = position.coerceIn(0f, (stops.size - 1).toFloat())
    val i = p.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], p - i)
}

@ThemePreviews
@Composable
private fun OnboardingScreenPreview() {
    SkypassTheme {
        OnboardingScreen(uiState = OnboardingUiState(), onEvent = {})
    }
}

@ThemePreviews
@Composable
private fun OnboardingLastPagePreview() {
    SkypassTheme {
        OnboardingScreen(uiState = OnboardingUiState(currentPage = 2), onEvent = {})
    }
}
