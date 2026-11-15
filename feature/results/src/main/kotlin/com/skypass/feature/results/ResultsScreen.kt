package com.skypass.feature.results

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassToggle
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.DayPrice
import com.skypass.core.model.DepartureWindow
import com.skypass.core.model.FlightFilters
import com.skypass.core.model.SortOrder
import com.skypass.core.model.TripLeg
import com.skypass.feature.results.navigation.FlightSelection
import com.skypass.feature.results.navigation.ResultsDestination
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Subtitle = DateTimeFormatter.ofPattern("d MMM")
private val LongDay = DateTimeFormatter.ofPattern("EEE d MMM")
private val DayOfWeek = DateTimeFormatter.ofPattern("EEE")

@Composable
fun ResultsRoute(
    onBack: () -> Unit,
    onPickReturn: (ResultsDestination) -> Unit,
    onFlightSelected: (FlightSelection) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.results_save_failed)
    val pickReturn by rememberUpdatedState(onPickReturn)
    val selected by rememberUpdatedState(onFlightSelected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ResultsEffect.PickReturn -> pickReturn(effect.destination)
                is ResultsEffect.OpenBooking -> selected(effect.selection)
                ResultsEffect.SaveFailed -> snackbar.showSnackbar(saveFailed)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        ResultsScreen(uiState, viewModel::onEvent, onBack)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Snackbar(it, containerColor = SkypassTheme.colors.ink, contentColor = SkypassTheme.colors.onInk, shape = Shapes.sm)
        }
    }
}

@Composable
internal fun ResultsScreen(
    uiState: ResultsUiState,
    onEvent: (ResultsEvent) -> Unit,
    onBack: () -> Unit,
) {
    val colors = SkypassTheme.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        TopBar(uiState, onBack, onFilters = { onEvent(ResultsEvent.OpenFilters) })
        DateStrip(uiState.strip, uiState.selectedDate, uiState.today, onSelect = { onEvent(ResultsEvent.SelectDate(it)) })
        SortRow(uiState, onEvent)

        AnimatedContent(
            targetState = when {
                uiState.loading -> ListState.Loading
                uiState.error != null -> ListState.Error
                uiState.visible.isEmpty() -> ListState.Empty
                else -> ListState.Content
            },
            transitionSpec = { fadeIn(tween(Durations.SLOW)) togetherWith fadeOut(tween(Durations.FAST)) },
            label = "results",
            modifier = Modifier.weight(1f),
        ) { state ->
            when (state) {
                ListState.Loading -> LoadingList()
                ListState.Error -> Message(
                    title = stringResource(R.string.results_error_title),
                    body = stringResource(R.string.results_error_body),
                    action = stringResource(R.string.results_retry),
                    onAction = { onEvent(ResultsEvent.Retry) },
                )
                ListState.Empty -> if (uiState.all.isNotEmpty()) {
                    Message(
                        title = stringResource(R.string.results_empty_filtered),
                        body = null,
                        action = stringResource(R.string.results_clear_filters),
                        onAction = { onEvent(ResultsEvent.ResetFilters) },
                    )
                } else {
                    Message(
                        title = stringResource(R.string.results_empty_title, uiState.selectedDate.format(LongDay)),
                        body = stringResource(R.string.results_empty_body),
                        action = null,
                        onAction = {},
                    )
                }
                ListState.Content -> {
                    val selectLabel = when {
                        uiState.leg == TripLeg.Outbound && uiState.isRoundTrip -> stringResource(R.string.results_select_outbound)
                        uiState.adults > 1 -> stringResource(R.string.results_select_adults, uiState.adults)
                        else -> stringResource(R.string.results_select)
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp + bottom),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(uiState.visible, key = { it.instanceId }) { flight ->
                            FlightTicketCard(
                                flight = flight,
                                cabin = uiState.cabin,
                                adults = uiState.adults,
                                expanded = flight.instanceId == uiState.expandedId,
                                saved = flight.instanceId in uiState.savedIds,
                                selectLabel = selectLabel,
                                onToggle = { onEvent(ResultsEvent.ToggleExpanded(flight.instanceId)) },
                                onToggleSaved = { onEvent(ResultsEvent.ToggleSaved(flight.instanceId)) },
                                onSelect = { onEvent(ResultsEvent.SelectFlight(flight.instanceId)) },
                                // Re-sorting slides cards to their new places.
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.filterSheetOpen) FilterSheet(uiState, onEvent)
}

private enum class ListState { Loading, Error, Empty, Content }

@Composable
private fun TopBar(uiState: ResultsUiState, onBack: () -> Unit, onFilters: () -> Unit) {
    val colors = SkypassTheme.colors
    val adults = pluralStringResource(R.plurals.results_adults, uiState.adults, uiState.adults)
    val subtitle = if (uiState.leg == TripLeg.Return) {
        stringResource(R.string.results_return_leg) + " · " + uiState.selectedDate.format(Subtitle)
    } else {
        stringResource(R.string.results_subtitle, uiState.selectedDate.format(Subtitle), adults, uiState.cabin.label)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(SkypassIcons.ArrowLeft, "Back", onBack)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiState.from, style = SkypassTheme.typography.topBar, color = colors.ink)
                Icon(SkypassIcons.Plane, contentDescription = "to", tint = colors.ink, modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = 90f })
                Text(uiState.to, style = SkypassTheme.typography.topBar, color = colors.ink)
            }
            AnimatedContent(subtitle, label = "subtitle") {
                Text(it, style = SkypassTheme.typography.caption, color = colors.text2)
            }
        }
        IconCircleButton(
            SkypassIcons.Filters,
            contentDescription = stringResource(R.string.results_filters),
            onClick = onFilters,
            iconSize = 19.dp,
            badge = uiState.filters.activeCount > 0,
        )
    }
}

/** Five days around the searched date, cheapest fare on each. Past days are disabled. */
@Composable
private fun DateStrip(days: List<DayPrice>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            val isSelected = day.date == selected
            val enabled = !day.date.isBefore(today)
            val bg by animateColorAsState(if (isSelected) colors.ink else colors.surface, tween(Durations.BASE), label = "dayBg")
            val fg by animateColorAsState(if (isSelected) colors.onInk else colors.ink, tween(Durations.BASE), label = "dayFg")
            Column(
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(Shapes.button)
                    .background(bg)
                    .selectable(isSelected, enabled = enabled, role = Role.Tab) { onSelect(day.date) }
                    .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(day.date.format(DayOfWeek), style = SkypassTheme.typography.pill.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg.copy(alpha = 0.8f))
                Text(day.date.dayOfMonth.toString(), style = SkypassTheme.typography.titleLg, color = fg)
                Text(day.minPriceUsd?.let { usd(it) } ?: "—", style = SkypassTheme.typography.eyebrow, color = fg)
            }
        }
    }
}

@Composable
private fun SortRow(uiState: ResultsUiState, onEvent: (ResultsEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            SortOrder.Cheapest to R.string.results_cheapest,
            SortOrder.Fastest to R.string.results_fastest,
            SortOrder.Earliest to R.string.results_earliest,
        ).forEach { (order, label) ->
            FilterChip(stringResource(label), uiState.sort == order, onClick = { onEvent(ResultsEvent.SelectSort(order)) })
        }
        Spacer(Modifier.weight(1f))
        if (!uiState.loading) {
            Text(
                pluralStringResource(R.plurals.results_count, uiState.visible.size, uiState.visible.size),
                style = SkypassTheme.typography.monoSm,
                color = SkypassTheme.colors.text2,
            )
        }
    }
}

@Composable
private fun LoadingList() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) { TicketSkeleton(alpha) }
    }
}

@Composable
private fun Message(title: String, body: String?, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(SkypassIcons.Plane, contentDescription = null, tint = SkypassTheme.colors.borderStrong, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = 45f })
        Spacer(Modifier.height(6.dp))
        Text(title, style = SkypassTheme.typography.title, color = SkypassTheme.colors.ink, textAlign = TextAlign.Center)
        body?.let { Text(it, style = SkypassTheme.typography.bodySm, color = SkypassTheme.colors.text2, textAlign = TextAlign.Center) }
        action?.let { SkypassTextButton(it, onAction, color = SkypassTheme.colors.brand) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(uiState: ResultsUiState, onEvent: (ResultsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val filters = uiState.filters
    fun update(f: FlightFilters) = onEvent(ResultsEvent.UpdateFilters(f))
    ModalBottomSheet(
        onDismissRequest = { onEvent(ResultsEvent.CloseFilters) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.results_filters), style = SkypassTheme.typography.headline, color = colors.ink)

            Section(stringResource(R.string.results_filter_stops)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.card)
                        .background(colors.surface)
                        .clickable(role = Role.Switch) { update(filters.copy(nonstopOnly = !filters.nonstopOnly)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.results_filter_nonstop), style = SkypassTheme.typography.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                    SkypassToggle(filters.nonstopOnly, { update(filters.copy(nonstopOnly = it)) })
                }
            }

            if (uiState.airlines.isNotEmpty()) {
                Section(stringResource(R.string.results_filter_airlines)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.airlines.forEach { airline ->
                            val on = airline.code in filters.airlines
                            FilterChip(airline.name, on, onClick = {
                                update(filters.copy(airlines = if (on) filters.airlines - airline.code else filters.airlines + airline.code))
                            })
                        }
                    }
                }
            }

            Section(stringResource(R.string.results_filter_time)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DepartureWindow.Morning to R.string.results_morning,
                        DepartureWindow.Afternoon to R.string.results_afternoon,
                        DepartureWindow.Evening to R.string.results_evening,
                    ).forEach { (window, label) ->
                        val on = window in filters.windows
                        FilterChip(stringResource(label), on, onClick = {
                            update(filters.copy(windows = if (on) filters.windows - window else filters.windows + window))
                        })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkypassTextButton(stringResource(R.string.results_reset), { onEvent(ResultsEvent.ResetFilters) })
                PrimaryButton(
                    text = pluralStringResource(R.plurals.results_show, uiState.visible.size, uiState.visible.size),
                    onClick = { onEvent(ResultsEvent.CloseFilters) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.skypass.core.designsystem.component.Eyebrow(title)
        content()
    }
}
