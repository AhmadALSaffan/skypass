package com.skypass.feature.saved

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.IconCircleButton
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.model.SavedDestination
import com.skypass.core.model.SavedFlight
import com.skypass.core.model.SavedHotel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun usd(amount: Int): String = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(amount)

private val WhenFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

private fun duration(minutes: Int) = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

@Composable
fun SavedRoute(
    onBookFlight: (instanceId: String) -> Unit,
    onOpenHotels: (city: String?) -> Unit,
    onSearchFlights: () -> Unit,
    onOpenExplore: () -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val book by rememberUpdatedState(onBookFlight)
    val hotels by rememberUpdatedState(onOpenHotels)
    val search by rememberUpdatedState(onSearchFlights)
    val explore by rememberUpdatedState(onOpenExplore)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SavedEffect.BookFlight -> book(effect.instanceId)
                is SavedEffect.OpenHotels -> hotels(effect.city)
                SavedEffect.SearchFlights -> search()
                SavedEffect.OpenExplore -> explore()
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(SavedEvent.Refresh)
        onPauseOrDispose { }
    }
    SavedScreen(state, viewModel::onEvent)
}

@Composable
fun SavedScreen(state: SavedUiState, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val items = state.items
    Box(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.saved_title),
                    style = SkypassTheme.typography.headline,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.saved_subtitle), style = SkypassTheme.typography.bodySm, color = colors.text2)
            }
            SegmentedTabs(
                options = SavedTab.entries.map { tab ->
                    val count = when (tab) {
                        SavedTab.Flights -> items?.flights?.size
                        SavedTab.Stays -> items?.hotels?.size
                        SavedTab.Destinations -> items?.destinations?.size
                    }
                    val label = stringResource(tab.label)
                    if (count != null && count > 0) "$label · $count" else label
                },
                selectedIndex = state.tab.ordinal,
                onSelect = { onEvent(SavedEvent.SelectTab(SavedTab.entries[it])) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(Motion.SCREEN)) togetherWith fadeOut(tween(Motion.EXIT)) },
                label = "savedTab",
                modifier = Modifier.weight(1f),
            ) { tab ->
                when {
                    items == null && state.loading -> Skeletons()
                    items == null -> Message(SkypassIcons.CloudOff, R.string.saved_offline_title, R.string.saved_offline_body, R.string.saved_retry) {
                        onEvent(SavedEvent.Retry)
                    }
                    tab == SavedTab.Flights -> if (items.flights.isEmpty()) {
                        Message(SkypassIcons.Bookmark, R.string.saved_empty_flights, R.string.saved_empty_flights_body, R.string.saved_find_flights) { onEvent(SavedEvent.FindMore) }
                    } else {
                        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                            items(items.flights, key = { it.instanceId }) { flight ->
                                FlightCard(flight, bookable = flight.isBookable(state.now), onEvent = onEvent, modifier = Modifier.animateItem())
                            }
                        }
                    }
                    tab == SavedTab.Stays -> if (items.hotels.isEmpty()) {
                        Message(SkypassIcons.Bed, R.string.saved_empty_stays, R.string.saved_empty_stays_body, R.string.saved_find_stays) { onEvent(SavedEvent.FindMore) }
                    } else {
                        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                            items(items.hotels, key = { it.hotel.id }) { hotel -> HotelCard(hotel, onEvent, Modifier.animateItem()) }
                        }
                    }
                    else -> if (items.destinations.isEmpty()) {
                        Message(SkypassIcons.Compass, R.string.saved_empty_destinations, R.string.saved_empty_destinations_body, R.string.saved_find_destinations) {
                            onEvent(SavedEvent.FindMore)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = ListPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items.destinations, key = { it.destination.id }) { d -> DestinationCard(d, onEvent, Modifier.animateItem()) }
                        }
                    }
                }
            }
        }
        UndoBar(state, onEvent, Modifier.align(Alignment.BottomCenter))
    }
}

private val ListPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp)

private val SavedTab.label: Int
    get() = when (this) {
        SavedTab.Flights -> R.string.saved_tab_flights
        SavedTab.Stays -> R.string.saved_tab_stays
        SavedTab.Destinations -> R.string.saved_tab_destinations
    }

@Composable
private fun CardFrame(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(Shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSoft, Shapes.card)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
    ) { content() }
}

/** The filled bookmark or heart: tapping it un-saves. */
@Composable
private fun UnsaveButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val colors = SkypassTheme.colors
    IconCircleButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        modifier = modifier,
        container = if (onImage) Color.White else colors.bg,
        tint = if (icon == SkypassIcons.HeartFilled) colors.alert else colors.ink,
        size = 36.dp,
        iconSize = 18.dp,
    )
}

@Composable
private fun FlightCard(flight: SavedFlight, bookable: Boolean, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    CardFrame(onClick = if (bookable) ({ onEvent(SavedEvent.OpenFlight(flight)) }) else null, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AirlineLogo(flight.airlineCode, Color(flight.airlineColor), size = 40.dp, logoUrl = flight.airlineLogoUrl)
                Column(Modifier.weight(1f)) {
                    Text(flight.airlineName, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(flight.flightNumber, style = SkypassTheme.typography.monoSm, color = colors.text2)
                }
                UnsaveButton(SkypassIcons.BookmarkFilled, stringResource(R.string.saved_remove_flight), { onEvent(SavedEvent.RemoveFlight(flight)) })
            }
            Row(Modifier.alpha(if (bookable) 1f else 0.55f), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(flight.originCity, style = SkypassTheme.typography.caption, color = colors.text2)
                    Text(flight.originCode, style = SkypassTheme.typography.code, color = colors.ink)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(duration(flight.durationMinutes), style = SkypassTheme.typography.monoSm, color = colors.text2)
                    Icon(SkypassIcons.Plane, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Text(
                        if (flight.stops == 0) stringResource(R.string.saved_nonstop) else stringResource(R.string.saved_stops, flight.stops),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text(flight.destinationCity, style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
                    Text(flight.destinationCode, style = SkypassTheme.typography.code, color = colors.ink)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSoft))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flight.departure.format(WhenFormat), style = SkypassTheme.typography.captionStrong, color = colors.text2, modifier = Modifier.weight(1f))
                if (bookable) {
                    Text(
                        buildAnnotatedString {
                            append(usd(flight.fareUsd))
                            pushStyle(SpanStyle(color = colors.text2, fontSize = SkypassTheme.typography.caption.fontSize))
                            append(stringResource(R.string.saved_per_adult))
                            pop()
                        },
                        style = SkypassTheme.typography.title,
                        color = colors.ink,
                    )
                } else {
                    StatusPill(stringResource(R.string.saved_departed), container = colors.surfaceSunken, content = colors.text2)
                }
            }
        }
    }
}

@Composable
private fun HotelCard(saved: SavedHotel, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val hotel = saved.hotel
    CardFrame(onClick = { onEvent(SavedEvent.OpenHotel(saved)) }, modifier = modifier) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp).clip(Shapes.tile).stripes(colors.mint)) {
                hotel.imageUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hotel.name, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${hotel.city} · ${hotel.area}", style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(color = colors.star))
                        append("★".repeat(hotel.stars))
                        pop()
                        append("  ${hotel.rating}")
                    },
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildAnnotatedString {
                        append(usd(hotel.pricePerNightUsd))
                        pushStyle(SpanStyle(color = colors.text2, fontSize = SkypassTheme.typography.caption.fontSize))
                        append(stringResource(R.string.saved_per_night))
                        pop()
                    },
                    style = SkypassTheme.typography.labelMd,
                    color = colors.ink,
                )
            }
            UnsaveButton(SkypassIcons.HeartFilled, stringResource(R.string.saved_remove_hotel), { onEvent(SavedEvent.RemoveHotel(saved)) }, Modifier.align(Alignment.Top))
        }
    }
}

@Composable
private fun DestinationCard(saved: SavedDestination, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val d = saved.destination
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button) { onEvent(SavedEvent.OpenDestination(saved)) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f).clip(Shapes.card).stripes(Color(d.tone))) {
            d.imageUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            UnsaveButton(
                SkypassIcons.HeartFilled,
                stringResource(R.string.saved_remove_destination, d.city),
                { onEvent(SavedEvent.RemoveDestination(saved)) },
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                onImage = true,
            )
        }
        Column {
            Text(d.city, style = SkypassTheme.typography.label, color = colors.ink)
            Text(stringResource(R.string.saved_destination_from, d.country, usd(d.fromPriceUsd)), style = SkypassTheme.typography.caption, color = colors.text2)
        }
    }
}

/** "Removed from Saved · Undo", for four seconds. */
@Composable
private fun UndoBar(state: SavedUiState, onEvent: (SavedEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    LaunchedEffect(state.removed) {
        if (state.removed != null) {
            delay(UNDO_MS)
            onEvent(SavedEvent.DismissUndo)
        }
    }
    AnimatedVisibility(
        visible = state.removed != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.sm)
                .background(colors.ink)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.saved_removed), style = SkypassTheme.typography.labelMd, color = colors.onInk, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.saved_undo),
                style = SkypassTheme.typography.label,
                color = if (colors.isDark) colors.brand else colors.mint,
                modifier = Modifier
                    .clip(Shapes.chip)
                    .clickable(role = Role.Button) { onEvent(SavedEvent.Undo) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

private const val UNDO_MS = 4_000L

@Composable
private fun Message(icon: ImageVector, title: Int, body: Int, action: Int, onAction: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).background(colors.mint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(title), style = SkypassTheme.typography.titleLg, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(action), onClick = onAction, modifier = Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun Skeletons() {
    val colors = SkypassTheme.colors
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(150.dp).clip(Shapes.card).stripes(colors.surfaceSunken, colors.surface.copy(alpha = 0.5f))) }
    }
}
