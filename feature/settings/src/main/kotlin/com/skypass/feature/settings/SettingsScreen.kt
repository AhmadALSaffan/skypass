package com.skypass.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.SkypassToggle
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.ThemeMode
import com.skypass.core.ui.LocalUserVerifier
import com.skypass.core.ui.VerifyResult
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val verifier = LocalUserVerifier.current
    val scope = rememberCoroutineScope()
    val signedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.SignedOut -> signedOut()
            }
        }
    }
    // Android 13+ asks before an app may post notifications: turning push on asks too.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEvent(SettingsEvent.SetPush(granted))
    }
    val lockTitleOn = stringResource(R.string.settings_lock_confirm_on)
    val lockTitleOff = stringResource(R.string.settings_lock_confirm_off)
    val lockSubtitle = stringResource(R.string.settings_lock_confirm_subtitle)
    SettingsScreen(
        state = state,
        biometricAvailable = remember { verifier.isAvailable() },
        version = remember { appVersion(context) },
        onEvent = { event ->
            when {
                event is SettingsEvent.SetPush && event.enabled && needsNotificationPermission(context) ->
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                // Turning the lock on or off is itself protected: prove it's you first.
                event is SettingsEvent.SetBiometricLock -> scope.launch {
                    val result = verifier.verify(if (event.enabled) lockTitleOn else lockTitleOff, lockSubtitle)
                    if (result == VerifyResult.Verified) viewModel.onEvent(event)
                }
                else -> viewModel.onEvent(event)
            }
        },
        onBack = onBack,
    )
}

private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** "Skypass 1.0.0 (1)", from the installed package. */
private fun appVersion(context: android.content.Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    return "Skypass ${info.versionName} ($code)"
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    biometricAvailable: Boolean,
    version: String,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val settings = state.settings
    val notifications = settings.account.notifications
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        SkypassTopBar(stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 40.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.settings_save_failed),
                    style = SkypassTheme.typography.bodySm,
                    color = colors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Shapes.sm)
                        .background(colors.peach)
                        .clickable { onEvent(SettingsEvent.DismissSaveFailed) }
                        .padding(14.dp),
                )
            }
            Section(stringResource(R.string.settings_notifications)) {
                ToggleRow(
                    stringResource(R.string.settings_push),
                    stringResource(R.string.settings_push_sub),
                    notifications.push,
                    { onEvent(SettingsEvent.SetPush(it)) },
                )
                ToggleRow(
                    stringResource(R.string.settings_gate),
                    stringResource(R.string.settings_gate_sub),
                    notifications.gateAlerts,
                    { onEvent(SettingsEvent.SetGateAlerts(it)) },
                    divider = true,
                )
                ToggleRow(
                    stringResource(R.string.settings_price),
                    stringResource(R.string.settings_price_sub),
                    notifications.priceAlerts,
                    { onEvent(SettingsEvent.SetPriceAlerts(it)) },
                    divider = true,
                )
            }
            Section(stringResource(R.string.settings_preferences)) {
                ValueRow(stringResource(R.string.settings_currency), settings.account.currency.code, onClick = { onEvent(SettingsEvent.OpenCurrency) })
                ValueRow(stringResource(R.string.settings_language), stringResource(R.string.settings_english), divider = true)
                ValueRow(stringResource(R.string.settings_home_airport), state.homeAirport ?: "—", divider = true, onClick = { onEvent(SettingsEvent.OpenHomeAirport) })
                ValueRow(
                    stringResource(R.string.settings_dark_mode),
                    stringResource(themeLabel(settings.device.themeMode)),
                    divider = true,
                    onClick = { onEvent(SettingsEvent.OpenAppearance) },
                )
            }
            Section(stringResource(R.string.settings_security)) {
                ToggleRow(
                    stringResource(R.string.settings_biometric),
                    stringResource(if (biometricAvailable) R.string.settings_biometric_sub else R.string.settings_biometric_unavailable),
                    settings.device.biometricLock,
                    { onEvent(SettingsEvent.SetBiometricLock(it)) },
                    enabled = biometricAvailable || settings.device.biometricLock,
                )
                ValueRow(
                    stringResource(R.string.settings_connected),
                    stringResource(if (state.isGuest) R.string.settings_guest else R.string.settings_google),
                    divider = true,
                    onClick = { onEvent(SettingsEvent.OpenConnectedAccounts) },
                )
            }
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .clickable(enabled = !state.signingOut, role = Role.Button) { onEvent(SettingsEvent.SignOut) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (state.signingOut) R.string.settings_signing_out else R.string.settings_sign_out),
                    style = SkypassTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                    color = colors.danger,
                )
            }
            Text(
                version,
                style = SkypassTheme.typography.monoSm,
                color = colors.text2,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    SettingsSheets(state, onEvent)
}

internal fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Eyebrow(title, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp).semantics { heading() })
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SkypassTheme.colors.surface)
            .padding(horizontal = 16.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SkypassTheme.colors.borderSoft))
}

/** The whole row toggles, so the target is the full width, not just the switch. */
@Composable
private fun ToggleRow(
    label: String,
    sub: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    divider: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = SkypassTheme.colors
    if (divider) Divider()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = SkypassTheme.typography.bodyStrong, color = colors.ink)
            if (sub != null) Text(sub, style = SkypassTheme.typography.caption, color = colors.text2)
        }
        SkypassToggle(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueRow(label: String, value: String, divider: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = SkypassTheme.colors
    if (divider) Divider()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = SkypassTheme.typography.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
        Text(value, style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        if (onClick != null) Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.text3, modifier = Modifier.size(16.dp))
    }
}
