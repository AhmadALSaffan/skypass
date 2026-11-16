package com.skypass.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.icon.GoogleG
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheets(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val sheet = state.sheet ?: return
    ModalBottomSheet(
        onDismissRequest = { onEvent(SettingsEvent.DismissSheet) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SkypassTheme.colors.bg,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (sheet) {
                SettingsSheet.Currency -> {
                    Title(stringResource(R.string.settings_currency), stringResource(R.string.settings_currency_hint))
                    Options(DisplayCurrency.entries, state.settings.account.currency, { "${it.code} · ${it.label}" }) {
                        onEvent(SettingsEvent.CurrencyPicked(it))
                    }
                }
                SettingsSheet.Appearance -> {
                    Title(stringResource(R.string.settings_dark_mode), stringResource(R.string.settings_theme_hint))
                    Options(ThemeMode.entries, state.settings.device.themeMode, { stringResource(themeLabel(it)) }) {
                        onEvent(SettingsEvent.ThemePicked(it))
                    }
                }
                is SettingsSheet.HomeAirport -> AirportPicker(sheet, state.homeAirport, onEvent)
                SettingsSheet.ConnectedAccounts -> ConnectedAccounts(state, onEvent)
                SettingsSheet.ConfirmGuestSignOut -> {
                    Title(stringResource(R.string.settings_guest_sign_out_title), stringResource(R.string.settings_guest_sign_out_body))
                    PrimaryButton(stringResource(R.string.settings_guest_sign_out_confirm), onClick = { onEvent(SettingsEvent.ConfirmSignOut) }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(stringResource(R.string.settings_keep_signed_in), onClick = { onEvent(SettingsEvent.DismissSheet) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = SkypassTheme.typography.figure, color = SkypassTheme.colors.ink)
        Text(subtitle, style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2)
    }
}

/** A short single-choice list (currency, appearance). */
@Composable
private fun <T> Options(options: List<T>, selected: T, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    val colors = SkypassTheme.colors
    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface)) {
        options.forEachIndexed { i, option ->
            if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(colors.borderSoft))
            val isSelected = option == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(isSelected, role = Role.RadioButton) { onPick(option) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(option), style = SkypassTheme.typography.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(22.dp).border(2.dp, if (isSelected) colors.ink else colors.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Box(Modifier.size(11.dp).background(colors.ink, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun AirportPicker(sheet: SettingsSheet.HomeAirport, current: String?, onEvent: (SettingsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    Title(stringResource(R.string.settings_home_airport), stringResource(R.string.settings_home_airport_hint))
    SkypassTextField(
        value = sheet.query,
        onValueChange = { onEvent(SettingsEvent.AirportQueryChanged(it)) },
        label = stringResource(R.string.settings_search),
        placeholder = stringResource(R.string.settings_search_hint),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
    )
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.heightIn(max = 380.dp)) {
        items(sheet.results, key = { it.code }) { airport ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.sm)
                    .clickable(role = Role.Button) { onEvent(SettingsEvent.AirportPicked(airport)) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(airport.code, style = SkypassTheme.typography.title, color = colors.ink, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(airport.city, style = SkypassTheme.typography.label, color = colors.ink)
                    Text(airport.name, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
                }
                if (airport.code == current) Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConnectedAccounts(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    Title(stringResource(R.string.settings_connected), stringResource(R.string.settings_connected_hint))
    Row(
        Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.isGuest) {
            Box(Modifier.size(40.dp).background(colors.peach, CircleShape), contentAlignment = Alignment.Center) {
                Icon(SkypassIcons.User, contentDescription = null, tint = colors.onPastel, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(Modifier.size(40.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(GoogleG, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (state.isGuest) R.string.settings_guest_account else R.string.settings_google), style = SkypassTheme.typography.label, color = colors.ink)
            Text(
                state.session?.email ?: stringResource(R.string.settings_guest_detail),
                style = SkypassTheme.typography.caption,
                color = colors.text2,
            )
        }
    }
    if (state.isGuest) {
        PolicyNote(stringResource(R.string.settings_guest_note), positive = false)
    } else {
        PolicyNote(stringResource(R.string.settings_google_note), positive = true)
    }
    OutlinedButton(stringResource(R.string.settings_close), onClick = { onEvent(SettingsEvent.DismissSheet) }, modifier = Modifier.fillMaxWidth())
}
