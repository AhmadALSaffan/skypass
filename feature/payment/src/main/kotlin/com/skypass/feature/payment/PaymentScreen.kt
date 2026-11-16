package com.skypass.feature.payment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.ui.AddCardSheet
import com.skypass.core.ui.SkypassCardFace
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.skypass.core.model.DisplayCurrency
import com.skypass.core.designsystem.theme.money
import com.skypass.core.designsystem.theme.LocalCurrency
import com.skypass.core.ui.VerifyResult
import com.skypass.core.ui.LocalUserVerifier
import androidx.compose.ui.draw.clip
import com.skypass.core.designsystem.component.PaymentLogo
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.SectionHeader
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.Spinner
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.DmMono
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.JakartaSans
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.animateCountUp
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.BookingError
import com.skypass.core.model.PaymentMethod
import com.skypass.core.model.PaymentMethodType
import java.text.NumberFormat
import java.util.Locale

/** In the traveller's currency (Settings), from US dollars. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun usd(amount: Int): String = com.skypass.core.designsystem.theme.money(amount)

private val PaperGold = Color(0xFFE9D8A6)
private val CardTeal = Color(0xFF0F5B56)
private val CardNavy = Color(0xFF1C2550)

@Composable
fun PaymentRoute(
    onBack: () -> Unit,
    onPaid: (List<String>) -> Unit,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val paid by rememberUpdatedState(onPaid)
    val verifier = LocalUserVerifier.current
    val scope = rememberCoroutineScope()
    val confirmTitle = stringResource(R.string.payment_confirm_title)
    val confirmSubtitle = stringResource(R.string.payment_confirm_subtitle, money(uiState.price.totalUsd))
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaymentEffect.Paid -> paid(effect.bookingIds)
            }
        }
    }
    PaymentScreen(
        uiState = uiState,
        onEvent = { event ->
            // With the biometric lock on, paying needs the person's fingerprint, face or screen lock.
            if (event == PaymentEvent.Pay && uiState.confirmWithBiometric) {
                scope.launch {
                    when (verifier.verify(confirmTitle, confirmSubtitle)) {
                        VerifyResult.Verified, VerifyResult.Unavailable -> viewModel.onEvent(event)
                        VerifyResult.Cancelled -> Unit
                    }
                }
            } else {
                viewModel.onEvent(event)
            }
        },
        onBack = onBack,
    )
}

@Composable
internal fun PaymentScreen(uiState: PaymentUiState, onEvent: (PaymentEvent) -> Unit, onBack: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(SkypassIcons.ArrowLeft, "Back", onBack)
            Text(stringResource(R.string.payment_title), style = SkypassTheme.typography.topBar, color = colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                Text(stringResource(R.string.payment_step), style = SkypassTheme.typography.monoSm, color = colors.text2)
            }
        }

        if (uiState.error != null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.payment_load_error), style = SkypassTheme.typography.title, color = colors.ink)
                SkypassTextButton(stringResource(R.string.payment_retry), { onEvent(PaymentEvent.Retry) }, color = colors.brand)
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            uiState.method?.let { CardPreview(it, Modifier.reveal(0)) } ?: Box(Modifier.fillMaxWidth().height(192.dp))

            SectionHeader(stringResource(R.string.payment_method), Modifier.padding(top = 8.dp).reveal(1))
            Column(Modifier.reveal(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.methods.forEachIndexed { i, m ->
                    MethodRow(m, selected = i == uiState.selectedMethod, onClick = { onEvent(PaymentEvent.SelectMethod(i)) })
                }
                AddCardRow(onClick = { onEvent(PaymentEvent.AddCard) })
            }

            PromoRow(uiState, onEvent, Modifier.reveal(2))

            SectionHeader(stringResource(R.string.payment_summary), Modifier.padding(top = 8.dp).reveal(3))
            Summary(uiState, Modifier.reveal(3))

            AnimatedVisibility(uiState.payError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                uiState.payError?.let { PayErrorBanner(it, onDismiss = { onEvent(PaymentEvent.DismissPayError) }) }
            }
        }

        Footer(uiState, onPay = { onEvent(PaymentEvent.Pay) })
    }
    uiState.cardForm?.let { form ->
        AddCardSheet(
            form = form,
            onChange = { onEvent(PaymentEvent.UpdateCard(it)) },
            onSave = { onEvent(PaymentEvent.SaveCard) },
            onDismiss = { onEvent(PaymentEvent.DismissAddCard) },
            saveLabel = stringResource(R.string.payment_save_card),
        )
    }
}

/** Dashed "Add a card" row under the saved methods. */
@Composable
private fun AddCardRow(onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(Shapes.button)
            .drawBehind {
                drawRoundRect(
                    color = colors.borderStrong,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp, 32.dp).background(colors.surfaceSunken, Shapes.xs), contentAlignment = Alignment.Center) {
            Icon(SkypassIcons.Plus, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.payment_add_card), style = SkypassTheme.typography.label, color = colors.ink)
            Text(stringResource(R.string.payment_add_card_detail), style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
        }
    }
}

/**
 * The board's navy card. Changing method flips it over: it turns edge-on, the face swaps, and it
 * turns back.
 */
@Composable
private fun CardPreview(method: PaymentMethod, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(method) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(method) {
        if (method == shown) return@LaunchedEffect
        turn.animateTo(90f, tween(170, easing = Motion.EmphasizedAccelerate))
        shown = method
        turn.snapTo(-90f)
        turn.animateTo(0f, tween(260, easing = Motion.EmphasizedDecelerate))
    }
    SkypassCardFace(
        network = shown.type,
        number = shown.last4?.let { "••••  ••••  ••••  $it" } ?: shown.label,
        holder = shown.holder,
        expiry = shown.expiry ?: "—",
        modifier = modifier.graphicsLayer {
            rotationY = turn.value
            cameraDistance = 12 * density
        },
    )
}

@Composable
private fun MethodRow(method: PaymentMethod, selected: Boolean, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val border by animateColorAsState(if (selected) colors.ink else colors.border, tween(Durations.FAST), label = "methodBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.surface, Shapes.button)
            .border(1.5.dp, border, Shapes.button)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaymentLogo(method.type)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(method.label, style = SkypassTheme.typography.label, color = colors.ink)
                if (method.isDefault) StatusPill(stringResource(R.string.payment_default))
            }
            Text(method.detail, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
        }
        Box(Modifier.size(22.dp).border(2.dp, if (selected) colors.ink else colors.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
            val dot by androidx.compose.animation.core.animateFloatAsState(
                if (selected) 1f else 0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.5f),
                label = "radio",
            )
            Box(Modifier.size(10.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(colors.ink, CircleShape))
        }
    }
}

/** Dashed promo row; an applied code becomes a mint pill with a remove button. */
@Composable
private fun PromoRow(uiState: PaymentUiState, onEvent: (PaymentEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedContent(uiState.promo, transitionSpec = { fadeIn(tween(Durations.SLOW)) togetherWith fadeOut(tween(Durations.FAST)) }, label = "promo") { promo ->
            if (promo != null) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).background(colors.mint, Shapes.button).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(promo.code, style = SkypassTheme.typography.label, color = colors.brandDeep)
                        Text(promo.description, style = SkypassTheme.typography.caption, color = colors.brandDeep, maxLines = 1)
                    }
                    IconCircleButton(SkypassIcons.Close, stringResource(R.string.payment_remove_promo), { onEvent(PaymentEvent.RemovePromo) }, container = Color.Transparent, tint = colors.brandDeep, iconSize = 16.dp)
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = colors.borderStrong,
                                cornerRadius = CornerRadius(18.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                            )
                        }
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (uiState.promoInput.isEmpty()) Text(stringResource(R.string.payment_promo), style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Normal), color = colors.text2)
                        BasicTextField(
                            value = uiState.promoInput,
                            onValueChange = { onEvent(PaymentEvent.PromoChanged(it)) },
                            singleLine = true,
                            textStyle = SkypassTheme.typography.label.copy(color = colors.ink, letterSpacing = 0.06.em),
                            cursorBrush = SolidColor(colors.brand),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onEvent(PaymentEvent.ApplyPromo) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (uiState.promoChecking) {
                        Spinner(Modifier.padding(horizontal = 16.dp))
                    } else {
                        SkypassTextButton(stringResource(R.string.payment_apply), { onEvent(PaymentEvent.ApplyPromo) }, color = colors.brand)
                    }
                }
            }
        }
        uiState.promoProblem?.let {
            Text(
                stringResource(if (it == PromoProblem.Invalid) R.string.payment_promo_invalid else R.string.payment_promo_network),
                style = SkypassTheme.typography.caption,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun Summary(uiState: PaymentUiState, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val p = uiState.price
    Column(
        modifier.fillMaxWidth().softShadow(SoftShadow.Low, Shapes.card).background(colors.surface, Shapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Line(pluralStringResource(R.plurals.payment_fare, uiState.passengers, uiState.passengers), usd(p.fareUsd))
        uiState.promo?.let { Line(stringResource(R.string.payment_promo_line, it.code), "−" + usd(p.discountUsd), colors.brand) }
        Line(stringResource(R.string.payment_taxes), usd(p.taxesUsd))
        Line(
            stringResource(R.string.payment_seats, uiState.seats.joinToString(", ")),
            if (p.seatFeesUsd == 0) stringResource(R.string.payment_included) else usd(p.seatFeesUsd),
        )
        Line(pluralStringResource(R.plurals.payment_bags, uiState.bags, uiState.bags), usd(p.bagsUsd))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSoft))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.payment_total), style = SkypassTheme.typography.label, color = colors.ink, modifier = Modifier.weight(1f))
            Text(usd(animateCountUp(p.totalUsd, 450)), style = SkypassTheme.typography.figureSm, color = colors.ink)
        }
        // Shown in another currency: say what's actually charged.
        if (LocalCurrency.current != DisplayCurrency.USD) {
            Text(
                stringResource(R.string.payment_charged_usd, DisplayCurrency.USD.format(p.totalUsd)),
                style = SkypassTheme.typography.caption,
                color = colors.text2,
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String, valueColor: Color = SkypassTheme.colors.ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = SkypassTheme.typography.labelMd, color = valueColor)
    }
}

@Composable
private fun PayErrorBanner(error: BookingError, onDismiss: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.peach, Shapes.card).clickable(onClick = onDismiss).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(
                when (error) {
                    BookingError.SeatTaken -> R.string.payment_error_seat
                    BookingError.InvalidPromo -> R.string.payment_error_promo
                    BookingError.CardDeclined -> R.string.payment_error_card
                    BookingError.Network -> R.string.payment_error_network
                    BookingError.Unknown -> R.string.payment_error_other
                },
            ),
            style = SkypassTheme.typography.bodySm,
            color = colors.danger,
        )
    }
}

/** Sticky footer: Pay $X → spinner "Processing…" → "Paid ✓" in teal; lock note underneath. */
@Composable
private fun Footer(uiState: PaymentUiState, onPay: () -> Unit) {
    val colors = SkypassTheme.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by animateColorAsState(
        when {
            uiState.payState == PayState.Paid -> colors.brand
            !uiState.canPay && uiState.payState == PayState.Idle -> colors.borderStrong
            else -> colors.ink
        },
        tween(Durations.SLOW),
        label = "payBg",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction)
                .height(56.dp)
                .background(bg, Shapes.button)
                .clickable(interaction, null, enabled = uiState.canPay, role = Role.Button, onClick = onPay),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(uiState.payState, transitionSpec = { fadeIn(tween(Durations.BASE)) togetherWith fadeOut(tween(Durations.FAST)) }, label = "pay") { state ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (state) {
                        PayState.Idle -> Text(stringResource(R.string.payment_pay, usd(uiState.price.totalUsd)), style = SkypassTheme.typography.label, color = colors.onInk)
                        PayState.Processing -> {
                            Spinner(color = colors.onInk, track = colors.onInk.copy(alpha = 0.35f))
                            Text(stringResource(R.string.payment_processing), style = SkypassTheme.typography.label, color = colors.onInk)
                        }
                        PayState.Paid -> Text(stringResource(R.string.payment_paid), style = SkypassTheme.typography.label, color = colors.onBrand)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(SkypassIcons.Lock, contentDescription = null, tint = colors.text2, modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.payment_secure), style = SkypassTheme.typography.caption.copy(fontSize = 11.sp), color = colors.text2)
        }
    }
}
