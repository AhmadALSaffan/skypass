package com.skypass.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Avatar
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.SectionHeader
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.preview.ThemePreviews
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.domain.usecase.DayPart
import com.skypass.core.model.Airport
import com.skypass.core.model.Cabin
import com.skypass.core.model.Destination
import com.skypass.core.model.FlightSearchQuery
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.TripType
import com.skypass.core.model.UpcomingTrip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun HomeRoute(
    onSearch: (FlightSearchQuery) -> Unit,
    onSeeAllTrips: () -> Unit,
    onSeeAllDestinations: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    NearestAirportPermission(onGranted = { viewModel.onEvent(HomeEvent.LocationPermissionGranted) })
    val search by rememberUpdatedState(onSearch)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.OpenResults -> search(effect.query)
                HomeEffect.ScrollToSearch -> listState.animateScrollToItem(SEARCH_ITEM)
            }
        }
    }
    HomeScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onSeeAllTrips = onSeeAllTrips,
        onSeeAllDestinations = onSeeAllDestinations,
        listState = listState,
    )
}

private const val SEARCH_ITEM = 2

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onSeeAllTrips: () -> Unit,
    onSeeAllDestinations: () -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(SkypassTheme.colors.bg),
        contentPadding = PaddingValues(top = top + 8.dp, bottom = 28.dp),
    ) {
        item(key = "greeting") { Greeting(uiState, Modifier.padding(horizontal = 20.dp).reveal(0)) }
        item(key = "title") {
            Text(
                stringResource(R.string.home_title),
                style = SkypassTheme.typography.headline,
                color = SkypassTheme.colors.ink,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)
                    .reveal(1)
                    .semantics { heading() },
            )
        }
        item(key = "search") {
            Box(Modifier.padding(horizontal = 20.dp).reveal(2)) {
                val search = uiState.search
                when {
                    search != null -> SearchCard(
                        search = search,
                        swapTurns = uiState.swapTurns,
                        cabinMenuOpen = uiState.cabinMenuOpen,
                        fromIsNearby = uiState.fromIsNearby,
                        onEvent = onEvent,
                        onLaunchSearch = { onEvent(HomeEvent.Search) },
                    )
                    uiState.loadFailed -> OfflineCard(onRetry = { onEvent(HomeEvent.Retry) })
                    else -> TripPlaceholder(Modifier.height(380.dp))
                }
            }
        }
        item(key = "upcoming") {
            Column(Modifier.padding(horizontal = 20.dp).reveal(3)) {
                SectionHeader(
                    stringResource(R.string.home_upcoming),
                    Modifier.padding(top = 26.dp, bottom = 10.dp),
                    action = stringResource(R.string.home_see_all).takeIf { uiState.trip != null },
                    onAction = onSeeAllTrips,
                )
                AnimatedContent(
                    targetState = when {
                        uiState.trip != null -> TripSlot.Trip
                        uiState.tripLoaded -> TripSlot.Empty
                        else -> TripSlot.Loading
                    },
                    transitionSpec = { fadeIn(tween(Motion.ENTER)) togetherWith fadeOut(tween(Motion.EXIT)) },
                    label = "trip",
                ) { slot ->
                    when (slot) {
                        TripSlot.Trip -> uiState.trip?.let { UpcomingTripCard(it) }
                        TripSlot.Empty -> NoTripCard()
                        TripSlot.Loading -> TripPlaceholder()
                    }
                }
            }
        }
        if (uiState.popular.isNotEmpty()) {
            item(key = "popular") {
                Column(Modifier.reveal(4)) {
                    SectionHeader(
                        stringResource(R.string.home_popular_from, uiState.homeCity),
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
                        action = stringResource(R.string.home_see_all),
                        onAction = onSeeAllDestinations,
                    )
                    PopularCarousel(uiState.popular, onTap = { onEvent(HomeEvent.DestinationTapped(it)) })
                }
            }
        }
    }

    uiState.airportPicker?.let { field ->
        AirportPickerSheet(
            field = field,
            query = uiState.airportQuery,
            results = uiState.airportResults,
            selectedCode = uiState.search?.let { if (field == AirportField.From) it.from.code else it.to.code },
            onEvent = onEvent,
        )
    }
    val search = uiState.search
    if (uiState.datePicker != null && search != null) {
        val isReturn = uiState.datePicker == DateField.Return
        TravelDatePicker(
            selected = if (isReturn) search.returnDate ?: search.departDate else search.departDate,
            earliest = if (isReturn) search.departDate else uiState.firstBookableDate,
            latest = uiState.lastBookableDate,
            onPicked = { onEvent(HomeEvent.DatePicked(it)) },
            onDismiss = { onEvent(HomeEvent.DismissDatePicker) },
        )
    }
}

private enum class TripSlot { Trip, Empty, Loading }

@Composable
private fun Greeting(uiState: HomeUiState, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(uiState.initials.ifEmpty { "·" }, size = 46.dp, ring = true)
        Spacer(Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(
                    when (uiState.dayPart) {
                        DayPart.Morning -> R.string.home_good_morning
                        DayPart.Afternoon -> R.string.home_good_afternoon
                        DayPart.Evening -> R.string.home_good_evening
                    },
                ),
                style = SkypassTheme.typography.caption,
                color = colors.text2,
            )
            Text(
                uiState.travellerName ?: if (uiState.isGuest) stringResource(R.string.home_guest) else "",
                style = SkypassTheme.typography.label,
                color = colors.ink,
                maxLines = 1,
            )
        }
        IconCircleButton(
            SkypassIcons.Bell,
            contentDescription = stringResource(R.string.home_notifications),
            onClick = {},
            badge = uiState.trip?.status?.let { it != FlightStatus.OnTime } == true,
        )
    }
}

// ---------------------------------------------------------------------------------------------

private val PreviewJfk = Airport("JFK", "New York", "United States", "John F. Kennedy International")
private val PreviewLax = Airport("LAX", "Los Angeles", "United States", "Los Angeles International")

private fun previewState(withTrip: Boolean) = HomeUiState(
    travellerName = "Tina Anderson",
    initials = "TA",
    search = FlightSearchQuery(TripType.OneWay, PreviewJfk, PreviewLax, LocalDate.of(2026, 11, 20), null, 1, Cabin.Economy),
    tripLoaded = true,
    trip = if (withTrip) {
        UpcomingTrip(
            "b1", "SKP7Q2", "14C", Cabin.Economy, "i1", "NZ", "Air New Zealand", "NZ 2",
            "JFK", "New York", "AKL", "Auckland", LocalDateTime.of(2026, 11, 17, 19, 0), 1070,
            "B22", LocalTime.of(18, 20), FlightStatus.OnTime, 0,
        )
    } else {
        null
    },
    homeCity = "New York",
    popular = listOf(
        Destination("1", "Lisbon", "Portugal", "LIS", "Cities", 412, 0xFFCDEBE6),
        Destination("2", "Reykjavík", "Iceland", "KEF", "Cities", 389, 0xFFDEDCF6),
        Destination("3", "Mexico City", "Mexico", "MEX", "Cities", 298, 0xFFF9DCD2),
    ),
)

@ThemePreviews
@Composable
private fun HomePreview() {
    SkypassTheme { HomeScreen(previewState(withTrip = true), {}, {}, {}) }
}

@ThemePreviews
@Composable
private fun HomeNoTripPreview() {
    SkypassTheme { HomeScreen(previewState(withTrip = false), {}, {}, {}) }
}

/**
 * Asks once for approximate location so FROM can default to the nearest airport. Declining is fine:
 * FROM falls back to the profile's home airport.
 */
@Composable
private fun NearestAirportPermission(onGranted: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok -> if (ok) granted() }
    LaunchedEffect(Unit) {
        val has = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!has && !asked) {
            asked = true
            launcher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
