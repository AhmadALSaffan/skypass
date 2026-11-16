package com.skypass.feature.trips

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.skypass.core.designsystem.component.FormSection
import com.skypass.core.designsystem.component.Hairline
import com.skypass.core.designsystem.component.InfoCard
import com.skypass.core.designsystem.component.LabeledValue
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PriceLine
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.component.carImageRes
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.repository.CancelError
import com.skypass.core.model.CarBooking
import com.skypass.core.model.ContactDetails
import com.skypass.core.model.ReservationStatus
import com.skypass.core.model.StayBooking
import com.skypass.core.model.StayDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(onEvent: (TripsEvent) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(TripsEvent.CloseDetail) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SkypassTheme.colors.bg,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun Heading(title: String, subtitle: String, reference: String, status: ReservationStatus, completed: Boolean) {
    val colors = SkypassTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = SkypassTheme.typography.figure, color = colors.ink)
        Text(starred(subtitle), style = SkypassTheme.typography.bodySm, color = colors.text2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                status == ReservationStatus.Cancelled -> StatusPill(stringResource(R.string.trips_cancelled), container = colors.peach, content = colors.danger)
                completed -> StatusPill(stringResource(R.string.trips_completed), container = colors.surfaceSunken, content = colors.text2)
                else -> StatusPill(stringResource(R.string.trips_confirmed))
            }
            Text(stringResource(R.string.trips_reference, reference), style = SkypassTheme.typography.mono, color = colors.ink)
        }
    }
}

@Composable
private fun ContactBlock(title: String, contact: ContactDetails, extra: List<Pair<String, String>> = emptyList()) {
    FormSection(title) {
        InfoCard {
            LabeledValue(stringResource(R.string.trips_name), contact.name)
            LabeledValue(stringResource(R.string.trips_email), contact.email.ifBlank { "—" })
            (listOf(stringResource(R.string.trips_phone) to contact.phone.ifBlank { "—" }) + extra).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (label, value) -> LabeledValue(label, value, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Directions, and Cancel with an inline "are you sure" so nothing is cancelled by a stray tap. */
@Composable
private fun Actions(canCancel: Boolean, state: TripsUiState, onEvent: (TripsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    AnimatedContent(
        targetState = state.cancel != CancelStep.None,
        transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
        label = "tripActions",
    ) { confirming ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (confirming) {
                Text(stringResource(R.string.trips_cancel_confirm), style = SkypassTheme.typography.label, color = colors.ink)
                state.cancelError?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                CancelError.NotCancellable -> R.string.trips_cancel_too_late
                                CancelError.Network -> R.string.trips_cancel_network
                                CancelError.Unknown -> R.string.trips_cancel_unknown
                            },
                        ),
                        style = SkypassTheme.typography.bodySm,
                        color = colors.danger,
                    )
                }
                PrimaryButton(
                    stringResource(R.string.trips_cancel_yes),
                    onClick = { onEvent(TripsEvent.ConfirmCancel) },
                    loading = state.cancel == CancelStep.Cancelling,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(stringResource(R.string.trips_cancel_keep), onClick = { onEvent(TripsEvent.KeepBooking) }, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    stringResource(R.string.trips_directions),
                    onClick = { onEvent(TripsEvent.Directions) },
                    leadingIcon = SkypassIcons.Map,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canCancel) {
                    OutlinedButton(
                        stringResource(R.string.trips_cancel),
                        onClick = { onEvent(TripsEvent.AskCancel) },
                        contentColor = colors.danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StayDetailSheet(stay: StayBooking, state: TripsUiState, onEvent: (TripsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    DetailSheet(onEvent) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(Shapes.card).stripes(colors.mint)) {
            stay.hotel.imageUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Heading(
            title = stay.hotel.name,
            subtitle = "${"★".repeat(stay.hotel.stars)} · ${stay.hotel.area}, ${stay.hotel.city}",
            reference = stay.reference,
            status = stay.status,
            completed = stay.checkOut.isBefore(state.today),
        )
        InfoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(
                    stringResource(R.string.trips_check_in),
                    stay.checkIn.weekdayDate(),
                    Modifier.weight(1f),
                    detail = stringResource(R.string.trips_from_time, StayDetails.CHECK_IN_FROM),
                )
                LabeledValue(
                    stringResource(R.string.trips_check_out),
                    stay.checkOut.weekdayDate(),
                    Modifier.weight(1f),
                    detail = stringResource(R.string.trips_until_time, StayDetails.CHECK_OUT_UNTIL),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(stringResource(R.string.trips_room), stay.room.label, Modifier.weight(1f))
                LabeledValue(stringResource(R.string.trips_guests), pluralStringResource(R.plurals.trips_guests, stay.guests, stay.guests), Modifier.weight(1f))
            }
        }
        stay.details?.let { details ->
            ContactBlock(
                stringResource(R.string.trips_lead_guest),
                details.guest,
                listOf(stringResource(R.string.trips_arrival) to details.arrival.label),
            )
            if (details.requests.isNotEmpty() || details.note.isNotBlank()) {
                FormSection(stringResource(R.string.trips_requests)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.requests.sortedBy { it.ordinal }.forEach { StatusPill(it.label, container = colors.lavender, content = colors.onPastel) }
                    }
                    if (details.note.isNotBlank()) Text("“${details.note}”", style = SkypassTheme.typography.body, color = colors.text2)
                }
            }
        }
        FormSection(stringResource(R.string.trips_price)) {
            InfoCard {
                PriceLine(
                    stay.room.label,
                    usd(stay.totalUsd),
                    detail = pluralStringResource(R.plurals.trips_rate_nights, stay.nights, usd(stay.nightlyUsd), stay.nights),
                )
                PriceLine(stringResource(R.string.trips_taxes), stringResource(R.string.trips_included), amountColor = colors.brand)
                Hairline()
                PriceLine(stringResource(R.string.trips_total), usd(stay.totalUsd), total = true)
            }
            when {
                stay.status == ReservationStatus.Cancelled -> PolicyNote(stringResource(R.string.trips_cancelled_note), positive = false)
                stay.freeCancellationUntil != null && stay.canCancel(state.today) ->
                    PolicyNote(stringResource(R.string.trips_free_until, stay.freeCancellationUntil!!.weekdayDate()), positive = true)
                !stay.hotel.freeCancellation -> PolicyNote(stringResource(R.string.trips_non_refundable), positive = false)
            }
            if (stay.status != ReservationStatus.Cancelled) PolicyNote(stringResource(R.string.trips_pay_at_hotel), positive = false)
        }
        if (stay.status != ReservationStatus.Cancelled) Actions(canCancel = stay.canCancel(state.today), state, onEvent)
    }
}

@Composable
internal fun CarDetailSheet(car: CarBooking, state: TripsUiState, onEvent: (TripsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    DetailSheet(onEvent) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(Shapes.card).background(Color(car.car.stageColor)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(carImageRes(car.car.imagePath)),
                contentDescription = car.car.name,
                modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 12.dp),
            )
        }
        Heading(
            title = car.car.name,
            subtitle = "${car.car.supplier} · ${car.car.type.label} · ${car.car.transmission}",
            reference = car.reference,
            status = car.status,
            completed = !car.dropoffAt.isAfter(state.now),
        )
        InfoCard {
            LabeledValue(stringResource(R.string.trips_pickup), car.pickupAt.weekdayTime(), detail = car.pickupLocation)
            Hairline()
            LabeledValue(stringResource(R.string.trips_dropoff), car.dropoffAt.weekdayTime(), detail = car.dropoffLocation)
        }
        car.details?.let { details ->
            val extra = buildList {
                add(stringResource(R.string.trips_driver_age) to details.age.label)
                add(stringResource(R.string.trips_flight) to details.flightNumber.ifBlank { "—" })
            }
            ContactBlock(stringResource(R.string.trips_driver), details.driver, extra)
        }
        FormSection(stringResource(R.string.trips_price)) {
            InfoCard {
                PriceLine(
                    car.car.name,
                    usd(car.dailyUsd * car.days),
                    detail = pluralStringResource(R.plurals.trips_rate_days, car.days, usd(car.dailyUsd), car.days),
                )
                car.details?.extras?.sortedBy { it.ordinal }?.forEach { extra ->
                    PriceLine(
                        extra.label,
                        usd(extra.perDayUsd * car.days),
                        detail = pluralStringResource(R.plurals.trips_rate_days, car.days, usd(extra.perDayUsd), car.days),
                    )
                }
                if (car.youngDriverFeeUsd > 0) PriceLine(stringResource(R.string.trips_young_fee), usd(car.youngDriverFeeUsd))
                if (car.oneWayFeeUsd > 0) PriceLine(stringResource(R.string.trips_one_way), usd(car.oneWayFeeUsd))
                Hairline()
                PriceLine(stringResource(R.string.trips_total), usd(car.totalUsd), total = true)
            }
            when {
                car.status == ReservationStatus.Cancelled -> PolicyNote(stringResource(R.string.trips_cancelled_note), positive = false)
                car.freeCancellationUntil != null && car.canCancel(state.now) ->
                    PolicyNote(stringResource(R.string.trips_free_until, car.freeCancellationUntil!!.weekdayTime()), positive = true)
            }
            if (car.status != ReservationStatus.Cancelled) PolicyNote(stringResource(R.string.trips_licence), positive = false)
        }
        if (car.status != ReservationStatus.Cancelled) Actions(canCancel = car.canCancel(state.now), state, onEvent)
    }
}

/** Paints the ★ characters of [text] in the board's star yellow. */
@Composable
internal fun starred(text: String): androidx.compose.ui.text.AnnotatedString {
    val star = SkypassTheme.colors.star
    return androidx.compose.ui.text.buildAnnotatedString {
        text.forEach { c ->
            if (c == '★') {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = star))
                append(c)
                pop()
            } else {
                append(c)
            }
        }
    }
}
