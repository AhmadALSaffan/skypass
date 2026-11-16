package com.skypass.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.AnzWordmark
import com.skypass.core.designsystem.component.DashedRoute
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.Perforation
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.component.stripes
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.Destination
import com.skypass.core.model.FlightStatus
import com.skypass.core.model.UpcomingTrip
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TripDate = DateTimeFormatter.ofPattern("EEE d MMM")
private val Clock24 = DateTimeFormatter.ofPattern("HH:mm")

/** In the traveller's currency (Settings), from US dollars. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun usd(amount: Int): String = com.skypass.core.designsystem.theme.money(amount)

internal fun durationLabel(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"

// ---------------------------------------------------------------------------------------------
// Upcoming trip
// ---------------------------------------------------------------------------------------------

/**
 * The board's upcoming-trip ticket. Gate and status are live (Realtime); when the gate changes the
 * value flashes teal, the same beat as onboarding slide 3.
 */
@Composable
internal fun UpcomingTripCard(trip: UpcomingTrip, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val statusText = statusLabel(trip)
    val description = stringResource(
        R.string.home_trip_description,
        trip.airlineName, trip.flightNumber, trip.originCity, trip.destinationCity,
        trip.departure.format(TripDate), trip.boardingTime.format(Clock24), trip.gate, trip.seat, statusText,
    )
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(SoftShadow.Low, Shapes.ticket)
            .background(colors.surface, Shapes.ticket)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AirlineLogo(trip.airlineCode, Color(trip.airlineColor), size = 36.dp, logoUrl = trip.airlineLogoUrl)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (trip.airlineCode == "NZ") {
                    AnzWordmark(height = 11.dp, tint = colors.ink)
                } else {
                    Text(trip.airlineName, style = SkypassTheme.typography.labelSm, color = colors.ink)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${trip.flightNumber} · ${trip.departure.format(TripDate)}",
                    style = SkypassTheme.typography.monoSm,
                    color = colors.text2,
                )
            }
            StatusBadge(trip.status, statusText)
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Endpoint(trip.originCity, trip.originCode, Alignment.Start)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(durationLabel(trip.durationMinutes), style = SkypassTheme.typography.monoSm, color = colors.text2)
                DashedRoute(Modifier.fillMaxWidth(), color = colors.ink)
            }
            Endpoint(trip.destinationCity, trip.destinationCode, Alignment.End)
        }

        Perforation(notchColor = colors.bg, height = 18.dp)

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PassValue(stringResource(R.string.home_boards), trip.boardingTime.format(Clock24))
            LiveGate(trip.bookingId, trip.gate)
            PassValue(stringResource(R.string.home_seat), trip.seat, Alignment.End)
        }
    }
}

@Composable
private fun Endpoint(city: String, code: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(city, style = SkypassTheme.typography.caption, color = SkypassTheme.colors.text2, maxLines = 1)
        Text(code, style = SkypassTheme.typography.figureSm, color = SkypassTheme.colors.ink)
    }
}

@Composable
private fun PassValue(label: String, value: String, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Eyebrow(label, small = true)
        Text(value, style = SkypassTheme.typography.label, color = SkypassTheme.colors.ink)
    }
}

/** Gate that flashes teal when Realtime moves it. */
@Composable
private fun LiveGate(tripId: String, gate: String) {
    val colors = SkypassTheme.colors
    val flash = remember { Animatable(0f) }
    val previous = remember(tripId) { arrayOf<String?>(null) }
    LaunchedEffect(tripId, gate) {
        val before = previous[0]
        previous[0] = gate
        if (before != null && before != gate) {
            flash.animateTo(1f, spring(dampingRatio = 0.5f))
            flash.animateTo(0f, tween(900, delayMillis = 900))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(stringResource(R.string.home_gate), small = true)
        Text(
            gate,
            modifier = Modifier
                .background(lerp(Color.Transparent, colors.mint, flash.value), RoundedCornerShape(6.dp))
                .padding(horizontal = (4 * flash.value).dp),
            style = SkypassTheme.typography.label,
            color = lerp(colors.ink, colors.brand, flash.value),
        )
    }
}

@Composable
private fun StatusBadge(status: FlightStatus, text: String) {
    val colors = SkypassTheme.colors
    val (container, content) = when (status) {
        FlightStatus.OnTime -> colors.mint to colors.brandDeep
        FlightStatus.Boarding -> colors.butter to colors.goldInk
        FlightStatus.Delayed, FlightStatus.Cancelled -> colors.peach to colors.danger
        FlightStatus.Departed -> colors.lavender to colors.onPastel
    }
    val bg by animateColorAsState(container, label = "statusBg")
    val fg by animateColorAsState(content, label = "statusFg")
    StatusPill(text, container = bg, content = fg)
}

@Composable
private fun statusLabel(trip: UpcomingTrip): String = when (trip.status) {
    FlightStatus.OnTime -> stringResource(R.string.home_on_time)
    FlightStatus.Delayed -> stringResource(R.string.home_delayed, trip.delayMinutes)
    FlightStatus.Boarding -> stringResource(R.string.home_boarding)
    FlightStatus.Departed -> stringResource(R.string.home_departed)
    FlightStatus.Cancelled -> stringResource(R.string.home_cancelled)
}

/** No booking yet: a dashed ticket outline that invites the first search. */
@Composable
internal fun NoTripCard(modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = colors.borderStrong,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).background(colors.mint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(SkypassIcons.Plane, contentDescription = null, tint = colors.onPastel, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 45f })
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_no_trip_title), style = SkypassTheme.typography.label, color = colors.ink)
            Text(stringResource(R.string.home_no_trip_body), style = SkypassTheme.typography.caption, color = colors.text2)
        }
    }
}

/** While the trip loads: a soft breathing ticket-shaped block. */
@Composable
internal fun TripPlaceholder(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(SkypassTheme.colors.surface, Shapes.ticket),
    )
}

// ---------------------------------------------------------------------------------------------
// Popular destinations
// ---------------------------------------------------------------------------------------------

/** The board's 150×190 striped cards. Tapping one puts it in the search card's TO. */
@Composable
internal fun PopularCarousel(
    destinations: List<Destination>,
    onTap: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(destinations, key = { it.id }) { destination ->
            DestinationCard(destination, onClick = { onTap(destination) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun DestinationCard(destination: Destination, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val tone = Color(destination.tone)
    val price = stringResource(R.string.home_from_price, usd(destination.fromPriceUsd))
    Box(
        modifier
            .pressScale(interaction)
            .size(150.dp, 190.dp)
            .clip(Shapes.ticket)
            .stripes(tone)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "${destination.city}, $price" },
    ) {
        if (destination.imageUrl != null) {
            // Photo fades in over the striped pastel placeholder.
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(destination.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // A soft top shade keeps the airport code legible on bright skies.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent)),
            )
            Text(
                destination.airportCode,
                modifier = Modifier.padding(12.dp),
                style = SkypassTheme.typography.mono,
                color = Color.White,
            )
        } else {
            Text(
                stringResource(R.string.home_photo_label, destination.airportCode),
                modifier = Modifier.padding(12.dp),
                style = SkypassTheme.typography.monoSm.copy(fontSize = SkypassTheme.typography.eyebrow.fontSize),
                color = SkypassTheme.colors.onPastel2,
            )
        }
        // Paper footer: white with navy ink in both themes, like the passes.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color.White, Shapes.sm)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                destination.city,
                style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Bold),
                color = SkypassTheme.colors.onPastel,
                maxLines = 1,
            )
            Text(price, style = SkypassTheme.typography.caption, color = SkypassTheme.colors.onPastel2)
        }
    }
}

/** Shown in place of the search card when the catalog can't load. */
@Composable
internal fun OfflineCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, Shapes.heroLg)
            .border(1.5.dp, colors.border, Shapes.heroLg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.home_offline_title), style = SkypassTheme.typography.label, color = colors.ink)
        Text(
            stringResource(R.string.home_offline_body),
            style = SkypassTheme.typography.bodySm,
            color = colors.text2,
            textAlign = TextAlign.Center,
        )
        com.skypass.core.designsystem.component.SkypassTextButton(
            stringResource(R.string.home_retry),
            onRetry,
            color = colors.brand,
        )
    }
}
