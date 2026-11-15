package com.skypass.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.Airport
import java.time.LocalDate
import java.time.ZoneOffset

/** Bottom sheet: search field + airport list. The current pick is ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AirportPickerSheet(
    field: AirportField,
    query: String,
    results: List<Airport>,
    selectedCode: String?,
    onEvent: (HomeEvent) -> Unit,
) {
    val colors = SkypassTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = { onEvent(HomeEvent.DismissAirportPicker) },
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(Modifier.fillMaxHeight(0.85f).imePadding()) {
            Text(
                stringResource(if (field == AirportField.From) R.string.home_pick_from else R.string.home_pick_to),
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
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.home_pick_search), style = SkypassTheme.typography.bodySm, color = colors.text2)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { onEvent(HomeEvent.AirportQueryChanged(it)) },
                        singleLine = true,
                        textStyle = SkypassTheme.typography.bodyStrong.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.brand),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.home_pick_empty, query),
                    style = SkypassTheme.typography.bodySm,
                    color = colors.text2,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(results, key = { it.code }) { airport ->
                    AirportOption(airport, selected = airport.code == selectedCode, modifier = Modifier.animateItem()) {
                        onEvent(HomeEvent.AirportPicked(airport))
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportOption(airport: Airport, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(if (selected) colors.ink else colors.surface, Shapes.tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                airport.code,
                style = SkypassTheme.typography.labelSm.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) colors.onInk else colors.ink,
            )
        }
        Column(Modifier.weight(1f)) {
            Text("${airport.city}, ${airport.country}", style = SkypassTheme.typography.bodyStrong, color = colors.ink, maxLines = 1)
            Text(airport.name, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
        }
        if (selected) Icon(SkypassIcons.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
    }
}

/** Material date picker limited to the bookable window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TravelDatePicker(
    selected: LocalDate,
    earliest: LocalDate,
    latest: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SkypassTheme.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toLocalDate()
                return !date.isBefore(earliest) && !date.isAfter(latest)
            }

            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
        },
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.surface,
        selectedDayContainerColor = colors.ink,
        selectedDayContentColor = colors.onInk,
        todayDateBorderColor = colors.brand,
        todayContentColor = colors.brand,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SkypassTextButton(
                stringResource(R.string.home_ok),
                { state.selectedDateMillis?.let { onPicked(it.toLocalDate()) } ?: onDismiss() },
                color = colors.brand,
            )
        },
        dismissButton = { SkypassTextButton(stringResource(R.string.home_cancel), onDismiss) },
        colors = pickerColors,
        shape = Shapes.hero,
    ) {
        DatePicker(state = state, colors = pickerColors, showModeToggle = false)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
