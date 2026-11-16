package com.skypass.feature.hotels

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.skypass.core.designsystem.component.ContactFields
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.FormSection
import com.skypass.core.designsystem.component.Hairline
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.InfoCard
import com.skypass.core.designsystem.component.LabeledValue
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PriceLine
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.ContactProblem
import com.skypass.core.model.ArrivalWindow
import com.skypass.core.model.Hotel
import com.skypass.core.model.ReservationError
import com.skypass.core.model.Stay
import com.skypass.core.model.StayDetails
import com.skypass.core.model.StayRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WeekdayDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

internal fun LocalDate.weekdayDate(): String = format(WeekdayDate)

// ---------------------------------------------------------------------------------------------
// The booking sheet: choose a room → guest details → booked
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomsSheet(sheet: RoomsSheet, stay: Stay, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    ModalBottomSheet(
        onDismissRequest = { onEvent(HotelsEvent.DismissRooms) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        val page = when {
            sheet.state is ReserveState.Reserved -> 2
            sheet.step == RoomsStep.Details -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == 2) {
                    fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT))
                } else {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(Motion.SCREEN, easing = Motion.EmphasizedDecelerate)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(Motion.SCREEN))) togetherWith
                        (slideOutHorizontally(tween(Motion.EXIT)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(Motion.EXIT)))
                }
            },
            label = "roomsSheet",
        ) { target ->
            when (target) {
                2 -> Reserved(sheet, stay, onEvent)
                1 -> GuestDetails(sheet, stay, onEvent)
                else -> RoomChooser(sheet, stay, onEvent)
            }
        }
    }
}

@Composable
private fun RoomChooser(sheet: RoomsSheet, stay: Stay, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.hotels_choose_room), style = SkypassTheme.typography.headline, color = colors.ink)
            Text(
                "${sheet.hotel.name} · ${stay.dateRange()} · " + pluralStringResource(R.plurals.hotels_guests, stay.guests, stay.guests),
                style = SkypassTheme.typography.bodySm,
                color = colors.text2,
            )
        }
        sheet.rooms.forEach { price ->
            val selected = price.room == sheet.selected
            val border by animateColorAsState(if (selected) colors.ink else colors.border, tween(200), label = "roomBorder")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(colors.surface)
                    .border(BorderStroke(if (selected) 2.dp else 1.5.dp, border), Shapes.card)
                    .selectable(selected, role = Role.RadioButton) { onEvent(HotelsEvent.SelectRoom(price.room)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(price.room.label, style = SkypassTheme.typography.label, color = colors.ink)
                    Text(stringResource(R.string.hotels_sleeps, price.room.capacity), style = SkypassTheme.typography.caption, color = colors.text2)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${usd(price.perNightUsd)} ${stringResource(R.string.hotels_per_night)}", style = SkypassTheme.typography.labelMd, color = colors.ink)
                    Text(
                        pluralStringResource(R.plurals.hotels_nights_total, stay.nights, usd(price.totalUsd), stay.nights),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                    )
                }
                RadioDot(selected)
            }
        }
        PolicyNote(cancellationText(sheet.hotel, stay.checkIn), positive = sheet.hotel.freeCancellation)
        val total = sheet.selectedPrice?.totalUsd ?: 0
        PrimaryButton(
            text = stringResource(R.string.hotels_continue, usd(total)),
            onClick = { onEvent(HotelsEvent.ContinueToDetails) },
            trailingIcon = SkypassIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = SkypassTheme.colors
    Box(
        Modifier.size(22.dp).border(2.dp, if (selected) colors.ink else colors.borderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).background(colors.ink, CircleShape))
    }
}

@Composable
private fun cancellationText(hotel: Hotel, checkIn: LocalDate): String =
    if (hotel.freeCancellation) {
        stringResource(R.string.hotels_policy_free, checkIn.minusDays(1).weekdayDate())
    } else {
        stringResource(R.string.hotels_policy_non_refundable)
    }

/** Step two: who's staying, when they arrive, what they'd like, and the full price. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuestDetails(sheet: RoomsSheet, stay: Stay, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val price = sheet.selectedPrice ?: return
    val details = sheet.details
    Column(Modifier.navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircleButton(SkypassIcons.ArrowLeft, stringResource(R.string.hotels_back_to_rooms), { onEvent(HotelsEvent.BackToRooms) }, size = 40.dp, iconSize = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hotels_guest_details), style = SkypassTheme.typography.figure, color = colors.ink)
                Text("${sheet.hotel.name} · ${price.room.label}", style = SkypassTheme.typography.bodySm, color = colors.text2)
            }
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StaySummary(sheet.hotel, stay, price.room.label)

            FormSection(stringResource(R.string.hotels_lead_guest)) {
                ContactFields(
                    contact = details.guest,
                    onChange = { onEvent(HotelsEvent.GuestChanged(it)) },
                    nameError = stringResource(R.string.hotels_problem_name).takeIf { ContactProblem.NameMissing in sheet.problems },
                    emailError = stringResource(R.string.hotels_problem_email).takeIf { ContactProblem.EmailInvalid in sheet.problems },
                    phoneError = stringResource(R.string.hotels_problem_phone).takeIf { ContactProblem.PhoneInvalid in sheet.problems },
                )
            }

            FormSection(stringResource(R.string.hotels_arrival)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArrivalWindow.entries.forEach { window ->
                        FilterChip(window.label, selected = window == details.arrival, onClick = { onEvent(HotelsEvent.SelectArrival(window)) })
                    }
                }
            }

            FormSection(stringResource(R.string.hotels_requests)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StayRequest.entries.forEach { request ->
                        FilterChip(request.label, selected = request in details.requests, onClick = { onEvent(HotelsEvent.ToggleRequest(request)) })
                    }
                }
                Text(stringResource(R.string.hotels_requests_note), style = SkypassTheme.typography.caption, color = colors.text2)
                SkypassTextField(
                    value = details.note,
                    onValueChange = { onEvent(HotelsEvent.NoteChanged(it)) },
                    label = stringResource(R.string.hotels_note),
                    placeholder = stringResource(R.string.hotels_note_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            FormSection(stringResource(R.string.hotels_price)) {
                InfoCard {
                    PriceLine(
                        label = price.room.label,
                        amount = usd(price.totalUsd),
                        detail = pluralStringResource(R.plurals.hotels_rate_nights, stay.nights, usd(price.perNightUsd), stay.nights),
                    )
                    PriceLine(stringResource(R.string.hotels_taxes), stringResource(R.string.hotels_included), amountColor = colors.brand)
                    Hairline()
                    PriceLine(stringResource(R.string.hotels_total), usd(price.totalUsd), total = true)
                }
                PolicyNote(cancellationText(sheet.hotel, stay.checkIn), positive = sheet.hotel.freeCancellation)
                PolicyNote(stringResource(R.string.hotels_pay_at_hotel), positive = false)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sheet.error?.let { ReserveError(it) }
            PrimaryButton(
                text = stringResource(R.string.hotels_confirm, usd(price.totalUsd)),
                onClick = { onEvent(HotelsEvent.Reserve) },
                loading = sheet.state == ReserveState.Reserving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StaySummary(hotel: Hotel, stay: Stay, roomLabel: String) {
    val colors = SkypassTheme.colors
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HotelThumb(hotel, Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hotel.name, style = SkypassTheme.typography.label, color = colors.ink)
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.star))
                        append("★".repeat(hotel.stars))
                        pop()
                        append(" · ${hotel.area}")
                    },
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
            }
        }
        Hairline()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue(
                stringResource(R.string.hotels_check_in),
                stay.checkIn.weekdayDate(),
                Modifier.weight(1f),
                detail = stringResource(R.string.hotels_from_time, StayDetails.CHECK_IN_FROM),
            )
            LabeledValue(
                stringResource(R.string.hotels_check_out),
                stay.checkOut.weekdayDate(),
                Modifier.weight(1f),
                detail = stringResource(R.string.hotels_until_time, StayDetails.CHECK_OUT_UNTIL),
            )
        }
        Text(
            pluralStringResource(R.plurals.hotels_nights, stay.nights, stay.nights) + " · " +
                pluralStringResource(R.plurals.hotels_guests, stay.guests, stay.guests) + " · " + roomLabel,
            style = SkypassTheme.typography.captionStrong,
            color = colors.text2,
        )
    }
}

@Composable
internal fun HotelThumb(hotel: Hotel, modifier: Modifier = Modifier) {
    Box(modifier.clip(Shapes.tile).stripes(SkypassTheme.colors.mint)) {
        if (hotel.imageUrl != null) {
            AsyncImage(
                model = hotel.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun ReserveError(error: ReservationError) {
    Text(
        stringResource(
            when (error) {
                ReservationError.InvalidDates -> R.string.hotels_error_dates
                ReservationError.TooManyGuests -> R.string.hotels_error_guests
                ReservationError.InvalidDetails -> R.string.hotels_error_details
                ReservationError.Network -> R.string.hotels_error_network
                ReservationError.Unknown -> R.string.hotels_error_unknown
            },
        ),
        style = SkypassTheme.typography.bodySm,
        color = SkypassTheme.colors.danger,
    )
}

/** The check pops in with a spring and a haptic, then a receipt of what was booked. */
@Composable
private fun Reserved(sheet: RoomsSheet, stay: Stay, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val reservation = (sheet.state as? ReserveState.Reserved)?.reservation ?: return
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .background(colors.mint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(64.dp).background(colors.brand, CircleShape), contentAlignment = Alignment.Center) {
                Icon(SkypassIcons.Check, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(30.dp))
            }
        }
        Text(stringResource(R.string.hotels_reserved), style = SkypassTheme.typography.display, color = colors.ink)
        Text(
            stringResource(R.string.hotels_reserved_body, reservation.reference, sheet.hotel.name),
            style = SkypassTheme.typography.body,
            color = colors.text2,
        )
        InfoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(stringResource(R.string.hotels_check_in), stay.checkIn.weekdayDate(), Modifier.weight(1f))
                LabeledValue(stringResource(R.string.hotels_check_out), stay.checkOut.weekdayDate(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(stringResource(R.string.hotels_room), sheet.selected.label, Modifier.weight(1f))
                LabeledValue(stringResource(R.string.hotels_guest), sheet.details.guest.name, Modifier.weight(1f))
            }
            Hairline()
            PriceLine(
                pluralStringResource(R.plurals.hotels_nights, reservation.nights, reservation.nights),
                usd(reservation.totalUsd),
                total = true,
            )
        }
        Text(
            stringResource(R.string.hotels_saved_to_trips),
            style = SkypassTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.brand,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.hotels_view_trips), onClick = { onEvent(HotelsEvent.OpenMyTrips) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(stringResource(R.string.hotels_done), onClick = { onEvent(HotelsEvent.DismissRooms) }, modifier = Modifier.fillMaxWidth())
    }
}
