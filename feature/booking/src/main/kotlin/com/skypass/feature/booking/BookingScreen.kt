package com.skypass.feature.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.CounterStepper
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SectionHeader
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.animateCountUp
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.domain.usecase.BagsRule
import com.skypass.core.domain.usecase.CalculateBookingTotalUseCase
import com.skypass.core.model.FlightOffer
import com.skypass.core.model.Gender
import com.skypass.core.model.Passenger
import com.skypass.core.model.SeatType
import com.skypass.feature.booking.navigation.PaymentRequest
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Time = DateTimeFormatter.ofPattern("HH:mm")
private val Day = DateTimeFormatter.ofPattern("d MMM")

internal fun usd(amount: Int): String =
    NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(amount)

private fun duration(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"

@Composable
fun BookingRoute(
    onBack: () -> Unit,
    onContinue: (PaymentRequest) -> Unit,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.booking_save_failed)
    val proceed by rememberUpdatedState(onContinue)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookingEffect.OpenPayment -> proceed(effect.request)
                BookingEffect.PassengerSaveFailed -> snackbar.showSnackbar(saveFailed)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        BookingScreen(uiState, viewModel::onEvent, onBack)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)) {
            Snackbar(it, containerColor = SkypassTheme.colors.ink, contentColor = SkypassTheme.colors.onInk, shape = Shapes.sm)
        }
    }
}

@Composable
internal fun BookingScreen(uiState: BookingUiState, onEvent: (BookingEvent) -> Unit, onBack: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        TopBar(onBack)
        AnimatedContent(
            targetState = when {
                uiState.loading -> 0
                uiState.error != null -> 1
                else -> 2
            },
            transitionSpec = { fadeIn(tween(Durations.SLOW)) togetherWith fadeOut(tween(Durations.FAST)) },
            label = "booking",
            modifier = Modifier.weight(1f),
        ) { slot ->
            when (slot) {
                0 -> Loading()
                1 -> ErrorState(onRetry = { onEvent(BookingEvent.Retry) })
                else -> Content(uiState, onEvent)
            }
        }
        Footer(uiState, onContinue = { onEvent(BookingEvent.Continue) })
    }
    uiState.editor?.let { PassengerSheet(it, onEvent) }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(SkypassIcons.ArrowLeft, "Back", onBack)
        Text(
            stringResource(R.string.booking_title),
            style = SkypassTheme.typography.topBar,
            color = SkypassTheme.colors.ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            Text(stringResource(R.string.booking_step), style = SkypassTheme.typography.monoSm, color = SkypassTheme.colors.text2)
        }
    }
}

@Composable
private fun Content(uiState: BookingUiState, onEvent: (BookingEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val leg = uiState.legs[uiState.activeLeg]
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item(key = "flights") { FlightSummary(uiState, Modifier.reveal(0)) }

        item(key = "passengers-header") {
            SectionHeader(
                stringResource(if (uiState.adults > 1) R.string.booking_passengers else R.string.booking_passenger),
                Modifier.padding(top = 22.dp, bottom = 10.dp).reveal(1),
            )
        }
        item(key = "passengers") {
            Column(
                Modifier
                    .reveal(1)
                    .softShadow(SoftShadow.Low, Shapes.card)
                    .background(colors.surface, Shapes.card),
            ) {
                uiState.travellers.forEachIndexed { i, p ->
                    if (i > 0) Divider()
                    PassengerRow(p, complete = i !in uiState.incomplete, onEdit = { onEvent(BookingEvent.EditPassenger(p)) })
                }
                if (uiState.missingTravellers > 0) {
                    if (uiState.travellers.isNotEmpty()) Divider()
                    MissingPassengersRow(uiState.missingTravellers, firstOne = uiState.travellers.isEmpty()) {
                        onEvent(BookingEvent.EditPassenger(null))
                    }
                }
            }
        }

        item(key = "seat-header") {
            Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.booking_choose_seat), style = SkypassTheme.typography.title, color = colors.ink, modifier = Modifier.weight(1f))
                SeatPill(leg.assignments[uiState.activeTraveller])
            }
        }
        if (uiState.isRoundTrip) {
            item(key = "legs") {
                SegmentedTabs(
                    options = uiState.legs.mapIndexed { i, l ->
                        val f = l.flight
                        val label = stringResource(if (i == 0) R.string.booking_outbound else R.string.booking_return)
                        if (f != null) "$label · ${f.originCode}→${f.destinationCode}" else label
                    },
                    selectedIndex = uiState.activeLeg,
                    onSelect = { onEvent(BookingEvent.SelectLeg(it)) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        if (uiState.adults > 1) {
            item(key = "travellers") {
                TravellerChips(uiState, leg, onSelect = { onEvent(BookingEvent.SelectTraveller(it)) })
            }
        }
        item(key = "legend") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeatLegend(showUpgrade = leg.map?.cabin?.isUpgradeCabin == true)
                SeatNote(leg, uiState.activeTraveller)
            }
        }

        // The whole plane, one lazy row at a time.
        seatMapItems(
            leg = leg,
            travellers = uiState.travellers,
            activeTraveller = uiState.activeTraveller,
            onSelect = { onEvent(BookingEvent.SelectSeat(it)) },
        )

        item(key = "bags") { BagsRow(uiState, onEvent, Modifier.padding(top = 18.dp)) }
    }
}

/** One card per leg: logo, route, flight · date · stops, times and duration. */
@Composable
private fun FlightSummary(uiState: BookingUiState, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .softShadow(SoftShadow.Low, Shapes.card)
            .background(colors.surface, Shapes.card),
    ) {
        uiState.legs.forEachIndexed { i, leg ->
            val f = leg.flight ?: return@forEachIndexed
            if (i > 0) Divider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (uiState.isRoundTrip) Eyebrow(stringResource(if (i == 0) R.string.booking_outbound else R.string.booking_return))
                LegRow(f)
            }
        }
    }
}

@Composable
private fun LegRow(f: FlightOffer) {
    val colors = SkypassTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AirlineLogo(f.airlineCode, Color(f.airlineColor), size = 40.dp, logoUrl = f.airlineLogoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${f.originCode} → ${f.destinationCode}", style = SkypassTheme.typography.label, color = colors.ink)
            val stops = if (f.isNonstop) stringResource(R.string.booking_nonstop) else stringResource(R.string.booking_via, f.stopAirport.orEmpty())
            Text("${f.flightNumber} · ${f.departure.format(Day)} · $stops", style = SkypassTheme.typography.monoSm, color = colors.text2, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${f.departure.format(Time)} – ${f.arrival.format(Time)}${if (f.arrivalDayOffset > 0) " +${f.arrivalDayOffset}" else ""}",
                style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.ink,
            )
            Text(duration(f.durationMinutes), style = SkypassTheme.typography.caption, color = colors.text2)
        }
    }
}

@Composable
private fun PassengerRow(passenger: Passenger, complete: Boolean, onEdit: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(passenger.initials, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(passenger.fullName, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1)
            if (complete) {
                Text(
                    stringResource(R.string.booking_passport_gender, passenger.passportLast4.orEmpty(), genderLabel(passenger.gender)),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
            } else {
                Text(stringResource(R.string.booking_details_needed), style = SkypassTheme.typography.captionStrong, color = colors.danger)
            }
        }
        SkypassTextButton(
            stringResource(if (complete) R.string.booking_edit else R.string.booking_complete),
            onEdit,
            color = colors.brand,
        )
    }
}

@Composable
private fun genderLabel(gender: Gender?) = stringResource(
    when (gender) {
        Gender.Female -> R.string.booking_gender_female
        Gender.Male -> R.string.booking_gender_male
        else -> R.string.booking_gender_unspecified
    },
)

@Composable
private fun MissingPassengersRow(count: Int, firstOne: Boolean, onAdd: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.surfaceSunken, CircleShape), contentAlignment = Alignment.Center) {
            Icon(SkypassIcons.User, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (firstOne) stringResource(R.string.booking_add_traveller) else pluralStringResource(R.plurals.booking_more_passengers, count, count),
            style = SkypassTheme.typography.bodyStrong,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.booking_add_details), style = SkypassTheme.typography.labelSm, color = colors.brand, modifier = Modifier.padding(end = 8.dp))
    }
}

/** Who the next seat tap is for: a chip per traveller with their seat. */
@Composable
private fun TravellerChips(uiState: BookingUiState, leg: LegState, onSelect: (Int) -> Unit) {
    val colors = SkypassTheme.colors
    LazyRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(List(uiState.adults) { it }) { i, _ ->
            val p = uiState.travellers.getOrNull(i)
            val active = i == uiState.activeTraveller
            val bg by animateColorAsState(if (active) colors.ink else colors.surface, tween(Durations.BASE), label = "chip")
            val fg by animateColorAsState(if (active) colors.onInk else colors.ink, tween(Durations.BASE), label = "chipText")
            Row(
                Modifier
                    .background(bg, Shapes.pill)
                    .border(1.5.dp, if (active) colors.ink else colors.border, Shapes.pill)
                    .selectable(active, role = Role.Tab) { onSelect(i) }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(p?.initials ?: "${i + 1}", size = 28.dp)
                Text(p?.firstName ?: "#${i + 1}", style = SkypassTheme.typography.labelSm, color = fg)
                Text(leg.assignments[i] ?: "—", style = SkypassTheme.typography.monoSm, color = fg)
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(SkypassTheme.colors.borderSoft))
}

/** Navy pill with the active traveller's seat; the code flips when it changes. */
@Composable
private fun SeatPill(seat: String?) {
    val colors = SkypassTheme.colors
    AnimatedContent(
        targetState = seat,
        transitionSpec = {
            (slideInVertically(tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate)) { it } + fadeIn()) togetherWith
                (slideOutVertically(tween(Motion.EXIT)) { -it } + fadeOut())
        },
        label = "seatPill",
    ) { code ->
        if (code != null) {
            Text(
                code,
                modifier = Modifier.background(colors.ink, Shapes.pill).padding(horizontal = 12.dp, vertical = 5.dp),
                style = SkypassTheme.typography.mono.copy(fontSize = SkypassTheme.typography.caption.fontSize),
                color = colors.onInk,
            )
        }
    }
}

@Composable
private fun SeatNote(leg: LegState, traveller: Int) {
    val seat = leg.seatOf(traveller)
    val text = when {
        seat == null -> return
        seat.type == SeatType.Legroom -> stringResource(R.string.booking_seat_legroom, seat.code, usd(seat.extraFeeUsd))
        seat.type == SeatType.Standard -> stringResource(R.string.booking_seat_standard, seat.code)
        else -> stringResource(R.string.booking_seat_cabin, seat.code, seat.type.name.lowercase())
    }
    Text(text, style = SkypassTheme.typography.captionStrong, color = SkypassTheme.colors.brand)
}

@Composable
private fun BagsRow(uiState: BookingUiState, onEvent: (BookingEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .softShadow(SoftShadow.Low, Shapes.card)
            .background(colors.surface, Shapes.card)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.booking_bags), style = SkypassTheme.typography.label, color = colors.ink)
            Text(
                stringResource(if (uiState.isRoundTrip) R.string.booking_bags_note_round else R.string.booking_bags_note, usd(BagsRule.PER_BAG_USD)),
                style = SkypassTheme.typography.caption,
                color = colors.text2,
            )
        }
        CounterStepper(
            value = uiState.bags,
            onDecrement = { onEvent(BookingEvent.RemoveBag) },
            onIncrement = { onEvent(BookingEvent.AddBag) },
            canDecrement = uiState.bags > 0,
            canIncrement = uiState.bags < CalculateBookingTotalUseCase.MAX_BAGS,
            decrementLabel = stringResource(R.string.booking_fewer_bags),
            incrementLabel = stringResource(R.string.booking_more_bags),
        )
    }
}

/** Sticky white footer: total (counts to the new amount) and Continue to payment. */
@Composable
private fun Footer(uiState: BookingUiState, onContinue: () -> Unit) {
    val colors = SkypassTheme.colors
    val total = animateCountUp(uiState.price.totalUsd, durationMs = 450)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(stringResource(R.string.booking_total), style = SkypassTheme.typography.caption, color = colors.text2)
            Text(if (uiState.loading) "—" else usd(total), style = SkypassTheme.typography.figure, color = colors.ink)
        }
        PrimaryButton(
            text = stringResource(R.string.booking_continue),
            onClick = onContinue,
            enabled = uiState.canContinue,
            height = 54.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Loading() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(76, 64, 300, 64).forEach { h ->
            Box(Modifier.fillMaxWidth().height(h.dp).graphicsLayer { this.alpha = alpha }.background(SkypassTheme.colors.surface, Shapes.card))
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = SkypassTheme.colors.text2, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.booking_error_title), style = SkypassTheme.typography.title, color = SkypassTheme.colors.ink)
        Text(stringResource(R.string.booking_error_body), style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2)
        SkypassTextButton(stringResource(R.string.booking_retry), onRetry, color = SkypassTheme.colors.brand)
    }
}
