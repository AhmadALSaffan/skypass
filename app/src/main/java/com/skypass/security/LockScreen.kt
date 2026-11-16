package com.skypass.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypass.R
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.Wordmark
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.ui.LocalUserVerifier
import com.skypass.core.ui.VerifyResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStateAtLeast

/**
 * Covers the app until the traveller proves it's them. Asks straight away; if they cancel, the
 * Unlock button asks again. If the phone no longer has any lock set up, it lets them in.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val colors = SkypassTheme.colors
    val verifier = LocalUserVerifier.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    suspend fun ask() {
        when (verifier.verify(title, subtitle)) {
            VerifyResult.Verified, VerifyResult.Unavailable -> onUnlocked()
            VerifyResult.Cancelled -> Unit
        }
    }
    // BiometricPrompt can only show over a resumed activity (not under the splash screen).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {}
        delay(PROMPT_DELAY_MS)
        ask()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            // Swallow touches so nothing underneath can be used while locked.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Wordmark()
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(72.dp).background(colors.mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(SkypassIcons.Lock, contentDescription = null, tint = colors.brand, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(R.string.lock_title), style = SkypassTheme.typography.titleLg, color = colors.ink)
            Text(stringResource(R.string.lock_body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.lock_unlock), onClick = { scope.launch { ask() } }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val PROMPT_DELAY_MS = 350L
