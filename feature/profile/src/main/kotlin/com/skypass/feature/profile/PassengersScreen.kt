package com.skypass.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.Passenger
import com.skypass.core.ui.PassengerSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Expiry = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
fun PassengersRoute(onBack: () -> Unit, viewModel: PassengersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PassengersScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun PassengersScreen(state: PassengersUiState, onEvent: (PassengersEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        SkypassTopBar(
            stringResource(R.string.profile_passengers_title),
            onBack = onBack,
            subtitle = stringResource(R.string.profile_passengers_subtitle),
            action = { IconCircleButton(SkypassIcons.Plus, stringResource(R.string.profile_add_traveller), { onEvent(PassengersEvent.Add) }) },
        )
        when {
            state.loading -> Unit
            state.error != null -> CenterMessage(stringResource(R.string.profile_offline), stringResource(R.string.profile_retry)) { onEvent(PassengersEvent.Retry) }
            state.passengers.isEmpty() -> CenterMessage(stringResource(R.string.profile_no_travellers), stringResource(R.string.profile_add_traveller)) { onEvent(PassengersEvent.Add) }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.passengers, key = { it.id ?: it.fullName }) { passenger ->
                    TravellerCard(
                        passenger = passenger,
                        today = state.today,
                        confirming = state.confirmingDelete == passenger.id,
                        onEvent = onEvent,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    state.editor?.let { editor ->
        PassengerSheet(
            editor = editor,
            onChange = { onEvent(PassengersEvent.UpdateEditor(it)) },
            onSave = { onEvent(PassengersEvent.Save) },
            onDismiss = { onEvent(PassengersEvent.DismissEditor) },
        )
    }
}

@Composable
private fun TravellerCard(passenger: Passenger, today: LocalDate, confirming: Boolean, onEvent: (PassengersEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val expires = passenger.passportExpiryDate
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .clickable(role = Role.Button) { onEvent(PassengersEvent.Edit(passenger)) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(passenger.initials, size = 46.dp, container = if (passenger.isPrimary) colors.peach else colors.lavender)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(passenger.fullName, style = SkypassTheme.typography.label, color = colors.ink)
                Text(
                    passenger.passportLast4?.let { stringResource(R.string.profile_passport_ending, it) } ?: stringResource(R.string.profile_no_passport),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
            }
            if (passenger.isPrimary) StatusPill(stringResource(R.string.profile_primary))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (text, warn) = when {
                expires == null -> stringResource(R.string.profile_details_missing) to true
                expires.isBefore(today) -> stringResource(R.string.profile_passport_expired, expires.format(Expiry)) to true
                expires.isBefore(today.plusMonths(6)) -> stringResource(R.string.profile_passport_soon, expires.format(Expiry)) to true
                else -> stringResource(R.string.profile_passport_valid, expires.format(Expiry)) to false
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (warn) colors.alert else colors.brand))
            Text(text, style = SkypassTheme.typography.captionStrong, color = if (warn) colors.danger else colors.text2, modifier = Modifier.weight(1f))
            val id = passenger.id
            if (!passenger.isPrimary && id != null) {
                AnimatedContent(
                    targetState = confirming,
                    transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
                    label = "removeTraveller",
                ) { asking ->
                    if (asking) {
                        Row {
                            SkypassTextButton(stringResource(R.string.profile_keep), { onEvent(PassengersEvent.CancelDelete) })
                            SkypassTextButton(stringResource(R.string.profile_remove), { onEvent(PassengersEvent.ConfirmDelete) }, color = colors.danger)
                        }
                    } else {
                        IconCircleButton(
                            SkypassIcons.Trash,
                            stringResource(R.string.profile_remove_named, passenger.fullName),
                            { onEvent(PassengersEvent.AskDelete(id)) },
                            container = colors.bg,
                            tint = colors.text2,
                            size = 36.dp,
                            iconSize = 16.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(SkypassIcons.Passport, contentDescription = null, tint = SkypassTheme.colors.text2, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = SkypassTheme.typography.body, color = SkypassTheme.colors.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(action, onClick = onAction, height = 52.dp)
    }
}
