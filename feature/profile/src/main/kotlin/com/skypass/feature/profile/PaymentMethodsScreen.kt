package com.skypass.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PaymentLogo
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.PaymentMethodType
import com.skypass.core.model.SavedCard
import com.skypass.core.ui.AddCardSheet

@Composable
fun PaymentMethodsRoute(onBack: () -> Unit, viewModel: PaymentMethodsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentMethodsScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun PaymentMethodsScreen(state: PaymentMethodsUiState, onEvent: (PaymentMethodsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        SkypassTopBar(
            stringResource(R.string.profile_payments),
            onBack = onBack,
            subtitle = stringResource(R.string.profile_payments_subtitle),
            action = { IconCircleButton(SkypassIcons.Plus, stringResource(R.string.profile_add_card), { onEvent(PaymentMethodsEvent.AddCard) }) },
        )
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cards-header") { Eyebrow(stringResource(R.string.profile_cards)) }
            when {
                state.loading -> Unit
                state.error != null -> item(key = "error") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_offline), style = SkypassTheme.typography.bodySm, color = colors.text2, modifier = Modifier.weight(1f))
                        SkypassTextButton(stringResource(R.string.profile_retry), { onEvent(PaymentMethodsEvent.Retry) }, color = colors.brand)
                    }
                }
                state.cards.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.profile_no_cards), style = SkypassTheme.typography.bodySm, color = colors.text2)
                }
                else -> items(state.cards, key = { it.id }) { card ->
                    CardRow(card, expired = card.isExpired(state.today), confirming = state.confirmingDelete == card.id, onEvent = onEvent, modifier = Modifier.animateItem())
                }
            }
            item(key = "add") {
                PrimaryButton(
                    stringResource(R.string.profile_add_card),
                    onClick = { onEvent(PaymentMethodsEvent.AddCard) },
                    leadingIcon = SkypassIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "wallet-header") { Eyebrow(stringResource(R.string.profile_wallets), Modifier.padding(top = 8.dp)) }
            item(key = "gpay") {
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PaymentLogo(PaymentMethodType.GooglePay, width = 64.dp, height = 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_pay_google), style = SkypassTheme.typography.label, color = colors.ink)
                        Text(stringResource(R.string.profile_pay_google_detail), style = SkypassTheme.typography.caption, color = colors.text2)
                    }
                    StatusPill(stringResource(R.string.profile_pay_ready))
                }
            }
            item(key = "notes") {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolicyNote(stringResource(R.string.profile_pay_not_stored), positive = true)
                    PolicyNote(stringResource(R.string.profile_pay_stays), positive = false)
                }
            }
        }
    }
    state.cardForm?.let { form ->
        AddCardSheet(
            form = form,
            onChange = { onEvent(PaymentMethodsEvent.UpdateCard(it)) },
            onSave = { onEvent(PaymentMethodsEvent.SaveCard) },
            onDismiss = { onEvent(PaymentMethodsEvent.DismissAddCard) },
        )
    }
}

@Composable
private fun CardRow(card: SavedCard, expired: Boolean, confirming: Boolean, onEvent: (PaymentMethodsEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PaymentLogo(card.brand.paymentType, width = 64.dp, height = 42.dp, dimmed = expired)
            Column(Modifier.weight(1f)) {
                Text(card.brand.label, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1)
                Text(
                    "•••• ${card.last4} · " + stringResource(if (expired) R.string.profile_card_expired else R.string.profile_card_expires, card.expiry),
                    style = SkypassTheme.typography.caption,
                    color = if (expired) colors.danger else colors.text2,
                    maxLines = 1,
                )
            }
            if (card.isDefault) StatusPill(stringResource(R.string.profile_card_default))
        }
        AnimatedContent(
            targetState = confirming,
            transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
            label = "cardActions",
        ) { asking ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (asking) {
                    Text(stringResource(R.string.profile_remove_card_q), style = SkypassTheme.typography.bodySm, color = colors.ink, modifier = Modifier.weight(1f))
                    SkypassTextButton(stringResource(R.string.profile_keep), { onEvent(PaymentMethodsEvent.CancelDelete) })
                    SkypassTextButton(stringResource(R.string.profile_remove), { onEvent(PaymentMethodsEvent.ConfirmDelete) }, color = colors.danger)
                } else {
                    if (!card.isDefault && !expired) {
                        SkypassTextButton(stringResource(R.string.profile_make_default), { onEvent(PaymentMethodsEvent.MakeDefault(card)) }, color = colors.brand)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    SkypassTextButton(stringResource(R.string.profile_remove), { onEvent(PaymentMethodsEvent.AskDelete(card.id)) }, color = colors.danger)
                }
            }
        }
    }
}
