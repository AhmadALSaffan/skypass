package com.skypass.feature.cars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.OutlinedButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.component.carImageRes
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.Rental
import com.skypass.core.model.RentalPlace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePickerSheet(picker: PlacePicker, rental: Rental?, onEvent: (CarsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    ModalBottomSheet(
        onDismissRequest = { onEvent(CarsEvent.DismissPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(Modifier.heightIn(min = 360.dp).navigationBarsPadding()) {
            Text(
                stringResource(if (picker is PlacePicker.Pickup) R.string.cars_pick_pickup else R.string.cars_pick_dropoff),
                style = SkypassTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))
            when (picker) {
                is PlacePicker.Pickup -> {
                    SearchBox(picker.query, onChange = { onEvent(CarsEvent.PickupQueryChanged(it)) })
                    if (picker.results.isEmpty()) {
                        Text(
                            stringResource(R.string.cars_no_airport, picker.query),
                            style = SkypassTheme.typography.bodySm,
                            color = colors.text2,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                        items(picker.results, key = { it.code }) { airport ->
                            PlaceOption(
                                title = "${airport.city} (${airport.code})",
                                subtitle = "${airport.name} · ${airport.country}",
                                selected = airport.code == rental?.pickup?.airportCode,
                                modifier = Modifier.animateItem(),
                            ) { onEvent(CarsEvent.PickupPicked(airport)) }
                        }
                    }
                }
                is PlacePicker.Dropoff -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                    items(picker.places, key = { it.label }) { place ->
                        PlaceOption(
                            title = if (place == rental?.pickup) stringResource(R.string.cars_same_as_pickup) else place.label,
                            subtitle = if (place == rental?.pickup) place.label else null,
                            selected = place == rental?.dropoff,
                        ) { onEvent(CarsEvent.DropoffPicked(place)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val colors = SkypassTheme.colors
    val focus = remember { FocusRequester() }
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
            if (query.isEmpty()) Text(stringResource(R.string.cars_search_airport), style = SkypassTheme.typography.bodySm, color = colors.text2)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = SkypassTheme.typography.bodyStrong.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun PlaceOption(title: String, subtitle: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SkypassTheme.typography.label, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = SkypassTheme.typography.caption, color = colors.text2)
        }
        if (selected) Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RentalDatesDialog(rental: Rental, firstBookable: LocalDate, onEvent: (CarsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val first = firstBookable.toUtcMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = rental.pickupAt.toLocalDate().toUtcMillis(),
        initialSelectedEndDateMillis = rental.dropoffAt.toLocalDate().toUtcMillis(),
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
    val valid = start != null && end != null && end > start
    DatePickerDialog(
        onDismissRequest = { onEvent(CarsEvent.DismissDates) },
        confirmButton = {
            SkypassTextButton(
                stringResource(R.string.cars_done),
                onClick = { if (valid) onEvent(CarsEvent.DatesPicked(start!!.toLocalDate(), end!!.toLocalDate())) },
                color = if (valid) colors.brand else colors.text3,
            )
        },
        dismissButton = { SkypassTextButton(stringResource(R.string.cars_cancel), { onEvent(CarsEvent.DismissDates) }) },
        colors = pickerColors,
        shape = Shapes.hero,
    ) {
        DateRangePicker(
            state = state,
            colors = pickerColors,
            showModeToggle = false,
            title = {
                Text(
                    stringResource(R.string.cars_pick_dates),
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

/** The car drives in on a spring, with a haptic, then the reference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReservedSheet(reserved: CarReserveState.Reserved, rental: Rental?, onEvent: (CarsEvent) -> Unit) {
    val onDone = { onEvent(CarsEvent.DismissReserved) }
    val colors = SkypassTheme.colors
    val haptics = LocalHapticFeedback.current
    val drive = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        drive.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f))
    }
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(Shapes.card)
                    .background(androidx.compose.ui.graphics.Color(reserved.car.stageColor)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(carImageRes(reserved.car.imagePath)),
                    contentDescription = reserved.car.name,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(110.dp)
                        .padding(bottom = 10.dp)
                        .graphicsLayer { translationX = drive.value * size.width * 1.4f },
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .background(colors.brand, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SkypassIcons.Check, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(18.dp))
                }
            }
            Text(stringResource(R.string.cars_reserved), style = SkypassTheme.typography.display, color = colors.ink, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.cars_reserved_body, reserved.reservation.reference, reserved.car.name),
                style = SkypassTheme.typography.body,
                color = colors.text2,
                textAlign = TextAlign.Center,
            )
            if (rental != null) {
                Text(
                    stringResource(R.string.cars_reserved_pickup, rental.pickupAt.rentalLabel(), rental.pickup.label),
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                usd(reserved.reservation.totalUsd),
                style = SkypassTheme.typography.price.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.ink,
            )
            reserved.details?.let { details ->
                Text(
                    stringResource(R.string.cars_reserved_driver, details.driver.name) +
                        details.extras.sortedBy { it.ordinal }.joinToString("") { " · " + it.label },
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.cars_saved_to_trips),
                style = SkypassTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.brand,
            )
            Spacer(Modifier.height(4.dp))
            PrimaryButton(stringResource(R.string.cars_view_trips), onClick = { onEvent(CarsEvent.OpenMyTrips) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(stringResource(R.string.cars_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
