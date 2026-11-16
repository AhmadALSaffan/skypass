package com.skypass.feature.hotels

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.CounterStepper
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.StayProblem
import com.skypass.core.model.ReservationError
import com.skypass.core.model.Stay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------------------------------------
// "Your stay": city, dates, guests
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StaySheet(draft: StayDraft, firstBookable: LocalDate, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    var pickingDates by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onEvent(HotelsEvent.DismissStay) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        AnimatedContent(
            targetState = draft.pickingCity,
            transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
            label = "staySheet",
        ) { pickingCity ->
            if (pickingCity) {
                CityPicker(draft, onEvent)
            } else {
                StayForm(draft.stay, draft.problem, onEvent, onPickDates = { pickingDates = true })
            }
        }
    }
    if (pickingDates) {
        StayDatesDialog(
            stay = draft.stay,
            firstBookable = firstBookable,
            onDismiss = { pickingDates = false },
            onPicked = { checkIn, checkOut ->
                pickingDates = false
                onEvent(HotelsEvent.UpdateDraft(draft.stay.copy(checkIn = checkIn, checkOut = checkOut)))
            },
        )
    }
}

@Composable
private fun StayForm(stay: Stay, problem: StayProblem?, onEvent: (HotelsEvent) -> Unit, onPickDates: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.hotels_your_stay), style = SkypassTheme.typography.headline, color = colors.ink)
        StayField(stringResource(R.string.hotels_city), stay.city, onClick = { onEvent(HotelsEvent.PickCity) })
        StayField(
            stringResource(R.string.hotels_dates),
            "${stay.dateRange()} · " + pluralStringResource(R.plurals.hotels_nights, stay.nights, stay.nights),
            onClick = onPickDates,
        )
        Row(
            Modifier.fillMaxWidth().background(colors.surface, Shapes.field).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.hotels_guests_label))
                Text(
                    pluralStringResource(R.plurals.hotels_guests, stay.guests, stay.guests),
                    style = SkypassTheme.typography.label,
                    color = colors.ink,
                )
            }
            CounterStepper(
                value = stay.guests,
                onDecrement = { onEvent(HotelsEvent.UpdateDraft(stay.copy(guests = stay.guests - 1))) },
                onIncrement = { onEvent(HotelsEvent.UpdateDraft(stay.copy(guests = stay.guests + 1))) },
                canDecrement = stay.guests > Stay.MIN_GUESTS,
                canIncrement = stay.guests < Stay.MAX_GUESTS,
                decrementLabel = stringResource(R.string.hotels_fewer_guests),
                incrementLabel = stringResource(R.string.hotels_more_guests),
            )
        }
        if (problem != null) {
            Text(
                stringResource(
                    when (problem) {
                        StayProblem.CheckInInPast -> R.string.hotels_problem_past
                        StayProblem.NoNights -> R.string.hotels_problem_nights
                        StayProblem.TooLong -> R.string.hotels_problem_long
                        StayProblem.TooManyGuests -> R.string.hotels_problem_guests
                    },
                ),
                style = SkypassTheme.typography.bodySm,
                color = colors.danger,
            )
        }
        PrimaryButton(stringResource(R.string.hotels_show_stays), onClick = { onEvent(HotelsEvent.ApplyStay) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StayField(label: String, value: String, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.field)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(label)
            Text(value, style = SkypassTheme.typography.label, color = colors.ink)
        }
        Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CityPicker(draft: StayDraft, onEvent: (HotelsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val focus = remember { FocusRequester() }
    Column(Modifier.heightIn(min = 420.dp)) {
        Text(
            stringResource(R.string.hotels_city),
            style = SkypassTheme.typography.headline,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.surface, Shapes.button)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(SkypassIcons.Search, contentDescription = null, tint = colors.text2, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (draft.cityQuery.isEmpty()) {
                    Text(stringResource(R.string.hotels_search_city), style = SkypassTheme.typography.bodySm, color = colors.text2)
                }
                BasicTextField(
                    value = draft.cityQuery,
                    onValueChange = { onEvent(HotelsEvent.CityQueryChanged(it)) },
                    singleLine = true,
                    textStyle = SkypassTheme.typography.bodyStrong.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.brand),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        if (draft.cities.isEmpty()) {
            Text(
                stringResource(R.string.hotels_no_city, draft.cityQuery),
                style = SkypassTheme.typography.bodySm,
                color = colors.text2,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
            items(draft.cities, key = { it.city }) { option ->
                val selected = option.city == draft.stay.city
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(Shapes.sm)
                        .clickable(role = Role.Button) { onEvent(HotelsEvent.CityPicked(option)) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.city, style = SkypassTheme.typography.label, color = colors.ink)
                        Text(option.country, style = SkypassTheme.typography.caption, color = colors.text2)
                    }
                    if (selected) Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StayDatesDialog(
    stay: Stay,
    firstBookable: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate, LocalDate) -> Unit,
) {
    val colors = SkypassTheme.colors
    val first = firstBookable.toUtcMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = stay.checkIn.toUtcMillis(),
        initialSelectedEndDateMillis = stay.checkOut.toUtcMillis(),
        yearRange = firstBookable.year..firstBookable.year + 1,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= first
        },
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.surface,
        selectedDayContainerColor = colors.ink,
        selectedDayContentColor = colors.onInk,
        todayDateBorderColor = colors.brand,
        dayInSelectionRangeContainerColor = colors.mint,
        dayInSelectionRangeContentColor = colors.onPastel,
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SkypassTextButton(
                stringResource(R.string.hotels_done),
                onClick = { if (start != null && end != null && end > start) onPicked(start.toLocalDate(), end.toLocalDate()) },
                color = if (start != null && end != null && end > start) colors.brand else colors.text3,
            )
        },
        dismissButton = { SkypassTextButton(stringResource(R.string.hotels_cancel), onDismiss) },
        colors = pickerColors,
        shape = Shapes.hero,
    ) {
        DateRangePicker(
            state = state,
            colors = pickerColors,
            showModeToggle = false,
            title = {
                Text(
                    stringResource(R.string.hotels_pick_dates),
                    style = SkypassTheme.typography.title,
                    color = colors.ink,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                )
            },
            modifier = Modifier.height(520.dp),
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
