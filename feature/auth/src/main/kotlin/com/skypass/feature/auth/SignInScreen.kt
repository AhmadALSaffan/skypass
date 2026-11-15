package com.skypass.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.Spinner
import com.skypass.core.designsystem.component.Wordmark
import com.skypass.core.designsystem.icon.GoogleG
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.preview.ThemePreviews
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.UserSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SignInRoute(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by rememberUpdatedState(onSignedIn)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The Google sheet needs the Activity, so it runs here and reports back.
                SignInEffect.LaunchGoogleSheet -> scope.launch {
                    viewModel.onEvent(SignInEvent.GoogleTokenReceived(viewModel.googleIdTokens.requestIdToken(context)))
                }
                SignInEffect.ForgetGoogleAccount -> scope.launch { viewModel.googleIdTokens.clearChosenAccount(context) }
                SignInEffect.NavigateHome -> signedIn()
            }
        }
    }

    SignInScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val problemText = uiState.problem?.let { stringResource(it.text()) }
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(problemText) {
        if (problemText != null) {
            snackbar.showSnackbar(problemText)
            currentOnEvent(SignInEvent.ProblemShown)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(SkypassTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Box(Modifier.height(52.dp), contentAlignment = Alignment.CenterStart) { Wordmark() }
            // Tiles + welcome + actions sit together, centred in the space above the legal line.
            Spacer(Modifier.weight(1f))
            AirportTiles()
            Spacer(Modifier.height(36.dp))

            Text(
                stringResource(R.string.sign_in_title),
                style = SkypassTheme.typography.displayLg,
                color = SkypassTheme.colors.ink,
                modifier = Modifier.reveal(6).semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sign_in_body),
                style = SkypassTheme.typography.body,
                color = SkypassTheme.colors.text2,
                modifier = Modifier.reveal(7),
            )
            Spacer(Modifier.height(28.dp))

            AnimatedContent(
                targetState = uiState.stage == SignInStage.SignedIn,
                transitionSpec = {
                    (fadeIn(tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate)) + slideInVertically { it / 4 }) togetherWith
                        (fadeOut(tween(Motion.EXIT)) + slideOutVertically { -it / 6 })
                },
                label = "signInActions",
                modifier = Modifier.reveal(8),
            ) { signedIn ->
                if (signedIn && uiState.account != null) {
                    SignedInActions(uiState.account, onEvent)
                } else {
                    IdleActions(uiState, onEvent)
                }
            }

            Spacer(Modifier.weight(1f))
            LegalLine(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Snackbar(
                snackbarData = it,
                containerColor = SkypassTheme.colors.ink,
                contentColor = SkypassTheme.colors.onInk,
                shape = Shapes.sm,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------

private data class Tile(val code: String, val city: String, val color: @Composable () -> Color)

/** 3×2 airport tiles from the board; the navy one holds a plane that turns into place and floats. */
@Composable
private fun AirportTiles() {
    val colors = SkypassTheme.colors
    val tiles: List<Tile?> = listOf(
        Tile("JFK", "New York") { colors.mint },
        Tile("CDG", "Paris") { colors.lavender },
        Tile("DXB", "Dubai") { colors.peach },
        Tile("HND", "Tokyo") { colors.butter },
        null,
        Tile("LHR", "London") { colors.sky },
    )
    val description = stringResource(R.string.sign_in_tiles_description)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { column, tile ->
                    val index = row * 3 + column
                    val tileModifier = Modifier.weight(1f).aspectRatio(1.1f).reveal(index)
                    if (tile == null) PlaneTile(tileModifier, index) else AirportTile(tile, tileModifier)
                }
            }
        }
    }
}

@Composable
private fun AirportTile(tile: Tile, modifier: Modifier) {
    Column(
        modifier
            .clip(Shapes.card)
            .background(tile.color())
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(tile.code, style = SkypassTheme.typography.code, color = SkypassTheme.colors.onPastel)
        Text(
            tile.city,
            style = SkypassTheme.typography.pill.copy(fontWeight = FontWeight.SemiBold),
            color = SkypassTheme.colors.onPastel2,
        )
    }
}

@Composable
private fun PlaneTile(modifier: Modifier, index: Int) {
    val inspection = LocalInspectionMode.current
    val turn = remember { Animatable(if (inspection) 45f else -40f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER + 260L)
        turn.animateTo(45f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = Motion.Emphasized), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier
            .clip(Shapes.card)
            .background(SkypassTheme.colors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SkypassIcons.Plane,
            contentDescription = null,
            tint = SkypassTheme.colors.onInk,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    rotationZ = turn.value
                    translationX = drift * 3.dp.toPx()
                    translationY = -drift * 3.dp.toPx()
                },
        )
    }
}

@Composable
private fun IdleActions(uiState: SignInUiState, onEvent: (SignInEvent) -> Unit) {
    val signingIn = uiState.stage == SignInStage.SigningIn
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            text = stringResource(if (signingIn) R.string.sign_in_signing_in else R.string.sign_in_google),
            onClick = { onEvent(SignInEvent.ContinueWithGoogle) },
            enabled = !uiState.busy,
            height = 58.dp,
            contentColor = if (signingIn) SkypassTheme.colors.text2 else SkypassTheme.colors.ink,
            leading = {
                Crossfade(signingIn, label = "googleLeading") { busy ->
                    if (busy) Spinner() else Image(rememberVectorPainter(GoogleG), contentDescription = null, Modifier.size(20.dp))
                }
            },
            modifier = Modifier.fillMaxWidth().softShadow(SoftShadow.Button, Shapes.button),
        )
        Crossfade(uiState.guestInFlight, label = "guest") { inFlight ->
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                if (inFlight) {
                    Spinner()
                } else {
                    SkypassTextButton(
                        text = stringResource(R.string.sign_in_guest),
                        onClick = { if (!uiState.busy) onEvent(SignInEvent.ContinueAsGuest) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedInActions(account: UserSession, onEvent: (SignInEvent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountRow(account)
        PrimaryButton(
            text = stringResource(R.string.sign_in_continue),
            onClick = { onEvent(SignInEvent.ContinueToApp) },
            trailingIcon = SkypassIcons.ArrowRight,
            height = 58.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        SkypassTextButton(stringResource(R.string.sign_in_another_account), { onEvent(SignInEvent.UseAnotherAccount) })
    }
}

@Composable
private fun AccountRow(account: UserSession) {
    val colors = SkypassTheme.colors
    val inspection = LocalInspectionMode.current
    val check = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(180)
        check.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    val name = account.fullName ?: account.email.orEmpty()
    val description = stringResource(R.string.sign_in_signed_in_as, name)
    Row(
        Modifier
            .fillMaxWidth()
            .softShadow(SoftShadow.Button, Shapes.button)
            .background(colors.surface, Shapes.button)
            .border(1.5.dp, colors.border, Shapes.button)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(account.initials, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1)
            if (account.fullName != null && account.email != null) {
                Text(account.email!!, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
            }
        }
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = check.value
                    scaleY = check.value
                    alpha = check.value.coerceIn(0f, 1f)
                }
                .background(colors.brand, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SkypassIcons.Check, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun LegalLine(modifier: Modifier = Modifier) {
    val strong = SpanStyle(color = SkypassTheme.colors.ink, fontWeight = FontWeight.SemiBold)
    val text = buildAnnotatedString {
        append(stringResource(R.string.sign_in_legal_prefix))
        withStyle(strong) { append(stringResource(R.string.sign_in_terms)) }
        append(stringResource(R.string.sign_in_legal_and))
        withStyle(strong) { append(stringResource(R.string.sign_in_privacy)) }
        append(".")
    }
    Text(
        text,
        modifier = modifier,
        style = SkypassTheme.typography.caption,
        color = SkypassTheme.colors.text2,
        textAlign = TextAlign.Center,
    )
}

private fun SignInProblem.text(): Int = when (this) {
    SignInProblem.Offline -> R.string.sign_in_problem_offline
    SignInProblem.NoGoogleAccount -> R.string.sign_in_problem_no_account
    SignInProblem.NotConfigured -> R.string.sign_in_problem_not_configured
    SignInProblem.GuestUnavailable -> R.string.sign_in_problem_guest
    SignInProblem.Other -> R.string.sign_in_problem_other
}

@ThemePreviews
@Composable
private fun SignInIdlePreview() {
    SkypassTheme { SignInScreen(SignInUiState(), onEvent = {}) }
}

@ThemePreviews
@Composable
private fun SignInSignedInPreview() {
    SkypassTheme {
        SignInScreen(
            SignInUiState(
                stage = SignInStage.SignedIn,
                account = UserSession("1", "tina.anderson@gmail.com", "Tina Anderson", isGuest = false),
            ),
            onEvent = {},
        )
    }
}
