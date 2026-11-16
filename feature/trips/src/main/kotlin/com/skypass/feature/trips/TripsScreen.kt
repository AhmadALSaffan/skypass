package com.skypass.feature.trips

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.component.carImageRes
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.domain.usecase.TripSection
import com.skypass.core.model.CarBooking
import com.skypass.core.model.FlightBooking
import com.skypass.core.model.ReservationStatus
import com.skypass.core.model.StayBooking
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun usd(amount: Int): String =
    NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(amount)

private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val WeekdayDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val WeekdayTime = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

internal fun LocalDate.weekdayDate(): String = format(WeekdayDate)
internal fun LocalDateTime.weekdayTime(): String = format(WeekdayTime)

/** "21–28 Nov" or "28 Nov – 3 Dec". */
internal fun dateRange(from: LocalDate, to: LocalDate): String =
    if (from.month == to.month && from.year == to.year) "${from.dayOfMonth}–${to.format(DayMonth)}"
    else "${from.format(DayMonth)} – ${to.format(DayMonth)}"

@Composable
fun TripsRoute(
    onBack: () -> Unit,
    onBoardingPass: (bookingId: String) -> Unit,
    onFindFlights: () -> Unit,
    onFindStays: () -> Unit,
    onFindCars: () -> Unit,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pass by rememberUpdatedState(onBoardingPass)
    val flights by rememberUpdatedState(onFindFlights)
    val stays by rememberUpdatedState(onFindStays)
    val cars by rememberUpdatedState(onFindCars)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TripsEffect.BoardingPass -> pass(effect.bookingId)
                TripsEffect.FindFlights -> flights()
                TripsEffect.FindStays -> stays()
                TripsEffect.FindCars -> cars()
                is TripsEffect.Directions -> {
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(effect.query)))
                    try {
                        context.startActivity(geo)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode(effect.query))))
                    }
                }
            }
        }
    }
    TripsScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun TripsScreen(state: TripsUiState, onEvent: (TripsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        SkypassTopBar(stringResource(R.string.trips_title), onBack = onBack, subtitle = stringResource(R.string.trips_subtitle))
        val trips = state.trips
        SegmentedTabs(
            options = TripsTab.entries.map { tab ->
                val label = stringResource(tab.label)
                val count = when (tab) {
                    TripsTab.Flights -> trips?.flights?.count
                    TripsTab.Stays -> trips?.stays?.count
                    TripsTab.Cars -> trips?.cars?.count
                }
                if (count != null && count > 0) "$label · $count" else label
            },
            selectedIndex = state.tab.ordinal,
            onSelect = { onEvent(TripsEvent.SelectTab(TripsTab.entries[it])) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        AnimatedContent(
            targetState = state.tab to (state.loading to (state.error != null)),
            transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
            label = "tripsTab",
            modifier = Modifier.weight(1f),
        ) { (tab, _) ->
            when {
                state.loading && trips == null -> Skeletons()
                state.error != null && trips == null -> ErrorState { onEvent(TripsEvent.Retry) }
                trips != null -> when (tab) {
                    TripsTab.Flights -> TabList(trips.flights, tab, onEvent) { FlightCard(it, past = it.trip.departure.toLocalDate().isBefore(state.today)) { onEvent(TripsEvent.OpenFlight(it)) } }
                    TripsTab.Stays -> TabList(trips.stays, tab, onEvent) { StayCard(it, state.today) { onEvent(TripsEvent.OpenStay(it)) } }
                    TripsTab.Cars -> TabList(trips.cars, tab, onEvent) { CarCard(it, state.now) { onEvent(TripsEvent.OpenCar(it)) } }
                }
            }
        }
    }
    when (val open = state.open) {
        is OpenBooking.Stay -> StayDetailSheet(open.booking, state, onEvent)
        is OpenBooking.Car -> CarDetailSheet(open.booking, state, onEvent)
        null -> Unit
    }
}

private val TripsTab.label: Int
    get() = when (this) {
        TripsTab.Flights -> R.string.trips_tab_flights
        TripsTab.Stays -> R.string.trips_tab_stays
        TripsTab.Cars -> R.string.trips_tab_cars
    }

@Composable
private fun <T> TabList(section: TripSection<T>, tab: TripsTab, onEvent: (TripsEvent) -> Unit, card: @Composable (T) -> Unit) {
    if (section.isEmpty) {
        EmptyState(tab) { onEvent(TripsEvent.FindMore) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        group(R.string.trips_upcoming, section.upcoming, card)
        group(R.string.trips_past, section.past, card)
    }
}

private fun <T> LazyListScope.group(title: Int, items: List<T>, card: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    item(key = "h$title") { Eyebrow(stringResource(title), modifier = Modifier.padding(top = 8.dp)) }
    items(items) { card(it) }
}

// ---------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------

@Composable
private fun TripCardFrame(onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(Shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSoft, Shapes.card)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun StatusChip(status: ReservationStatus, completed: Boolean) {
    val colors = SkypassTheme.colors
    when {
        status == ReservationStatus.Cancelled -> StatusPill(stringResource(R.string.trips_cancelled), container = colors.peach, content = colors.danger)
        completed -> StatusPill(stringResource(R.string.trips_completed), container = colors.surfaceSunken, content = colors.text2)
        else -> StatusPill(stringResource(R.string.trips_confirmed))
    }
}

@Composable
private fun FlightCard(flight: FlightBooking, past: Boolean, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val trip = flight.trip
    TripCardFrame(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AirlineLogo(trip.airlineCode, Color(trip.airlineColor), size = 44.dp, logoUrl = trip.airlineLogoUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${trip.originCode} → ${trip.destinationCode}",
                    style = SkypassTheme.typography.title,
                    color = colors.ink,
                )
                Text(
                    "${trip.originCity} to ${trip.destinationCity}",
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${trip.departure.weekdayTime()} · ${trip.flightNumber}",
                    style = SkypassTheme.typography.captionStrong,
                    color = colors.text2,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                trip.pnr?.let { Text(it, style = SkypassTheme.typography.mono, color = colors.ink) }
                Text(stringResource(R.string.trips_seat, trip.seat), style = SkypassTheme.typography.caption, color = colors.text2)
                if (!past) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.trips_pass), style = SkypassTheme.typography.captionStrong, color = colors.brand)
                        Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StayCard(stay: StayBooking, today: LocalDate, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    TripCardFrame(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(Shapes.tile).stripes(colors.mint)) {
                stay.hotel.imageUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stay.hotel.name, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stay.hotel.city} · ${dateRange(stay.checkIn, stay.checkOut)}",
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
                Text(
                    stay.room.label + " · " + pluralStringResource(R.plurals.trips_nights, stay.nights, stay.nights),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(stay.status, completed = stay.checkOut.isBefore(today))
                    Text(stay.reference, style = SkypassTheme.typography.monoSm, color = colors.text2)
                }
            }
            Text(usd(stay.totalUsd), style = SkypassTheme.typography.labelMd, color = colors.ink)
        }
    }
}

@Composable
private fun CarCard(car: CarBooking, now: LocalDateTime, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    TripCardFrame(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 88.dp, height = 72.dp).clip(Shapes.tile).background(Color(car.car.stageColor)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(carImageRes(car.car.imagePath)), contentDescription = null, modifier = Modifier.width(80.dp).padding(4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(car.car.name, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(car.pickupLocation, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateRange(car.pickupAt.toLocalDate(), car.dropoffAt.toLocalDate()) + " · " +
                        pluralStringResource(R.plurals.trips_days, car.days, car.days),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(car.status, completed = !car.dropoffAt.isAfter(now))
                    Text(car.reference, style = SkypassTheme.typography.monoSm, color = colors.text2)
                }
            }
            Text(usd(car.totalUsd), style = SkypassTheme.typography.labelMd, color = colors.ink)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------------------------

@Composable
private fun EmptyState(tab: TripsTab, onFind: () -> Unit) {
    val colors = SkypassTheme.colors
    val (icon: ImageVector, title, body, action) = when (tab) {
        TripsTab.Flights -> Quad(SkypassIcons.Plane, R.string.trips_empty_flights, R.string.trips_empty_flights_body, R.string.trips_find_flights)
        TripsTab.Stays -> Quad(SkypassIcons.Bed, R.string.trips_empty_stays, R.string.trips_empty_stays_body, R.string.trips_find_stays)
        TripsTab.Cars -> Quad(SkypassIcons.Car, R.string.trips_empty_cars, R.string.trips_empty_cars_body, R.string.trips_find_cars)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).background(colors.mint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(title), style = SkypassTheme.typography.titleLg, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(body),
            style = SkypassTheme.typography.bodySm,
            color = colors.text2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(action), onClick = onFind, modifier = Modifier.fillMaxWidth(), height = 52.dp)
    }
}

private data class Quad(val icon: ImageVector, val title: Int, val body: Int, val action: Int)

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.trips_offline_title), style = SkypassTheme.typography.titleLg, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.trips_offline_body), style = SkypassTheme.typography.bodySm, color = colors.text2)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.trips_retry), onClick = onRetry, height = 52.dp)
    }
}

@Composable
private fun Skeletons() {
    val colors = SkypassTheme.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(100.dp).clip(Shapes.card).stripes(colors.surfaceSunken, colors.surface.copy(alpha = 0.5f)))
        }
    }
}
