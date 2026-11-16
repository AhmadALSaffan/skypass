package com.skypass.feature.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.component.AirlineLogo
import com.skypass.core.designsystem.component.AnzWordmark
import com.skypass.core.designsystem.component.Barcode
import com.skypass.core.designsystem.component.DashedRoute
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.Perforation
import com.skypass.core.designsystem.component.StatusPill
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.Cabin
import com.skypass.core.model.FlightOffer
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Time = DateTimeFormatter.ofPattern("HH:mm")
private val ShortDate = DateTimeFormatter.ofPattern("d MMM")

/** In the traveller's currency (Settings), from US dollars. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun usd(amount: Int): String = com.skypass.core.designsystem.theme.money(amount)

internal fun duration(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"

/**
 * The board's result card: every flight is a boarding pass. Tap to open it; the open card shows
 * perks and the select button with the total for everyone travelling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlightTicketCard(
    flight: FlightOffer,
    cabin: Cabin,
    adults: Int,
    expanded: Boolean,
    saved: Boolean,
    selectLabel: String,
    onToggle: () -> Unit,
    onToggleSaved: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val stopsText = if (flight.isNonstop) {
        stringResource(R.string.results_nonstop)
    } else {
        pluralStringResource(R.plurals.results_stops, flight.stops, flight.stops, flight.stopAirport.orEmpty())
    }
    val description = stringResource(
        R.string.results_card_description,
        flight.airlineName, flight.flightNumber, flight.departure.format(Time), flight.originCity,
        flight.arrival.format(Time), flight.destinationCity, duration(flight.durationMinutes), stopsText, usd(flight.priceUsd),
    )

    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .softShadow(SoftShadow.Card, Shapes.ticket)
            .clip(Shapes.ticket)
            .background(colors.surface)
            .clickable(interaction, null, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = description },
    ) {
        // Airline row
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AirlineLogo(flight.airlineCode, Color(flight.airlineColor), size = 40.dp, logoUrl = flight.airlineLogoUrl)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (flight.airlineCode == "NZ") {
                    AnzWordmark(height = 12.dp, tint = colors.ink)
                } else {
                    Text(flight.airlineName, style = SkypassTheme.typography.titleSm.copy(fontSize = 15.sp), color = colors.ink, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Text("${flight.flightNumber} · ${flight.aircraft}", style = SkypassTheme.typography.monoSm, color = colors.text2)
            }
            if (flight.isBestValue) StatusPill(stringResource(R.string.results_best_value))
            BookmarkButton(saved, onToggleSaved)
        }

        // Route
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(flight.departure.format(Time), style = SkypassTheme.typography.figure, color = colors.ink)
                Text("${flight.originCode} · ${flight.originCity}", style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(duration(flight.durationMinutes), style = SkypassTheme.typography.monoSm, color = colors.text2)
                DashedRoute(Modifier.fillMaxWidth(), color = colors.ink)
                Text(
                    stopsText,
                    style = SkypassTheme.typography.pill,
                    color = if (flight.isNonstop) colors.brand else colors.danger,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(flight.arrival.format(Time), style = SkypassTheme.typography.figure, color = colors.ink)
                    if (flight.arrivalDayOffset > 0) {
                        Text("+${flight.arrivalDayOffset}", style = SkypassTheme.typography.pill, color = colors.danger)
                    }
                }
                Text("${flight.destinationCode} · ${flight.destinationCity}", style = SkypassTheme.typography.caption, color = colors.text2, maxLines = 1)
            }
        }

        Perforation(notchColor = colors.bg, height = 20.dp, notchRadius = 10.dp)

        // Pass grid
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            GridValue(stringResource(R.string.results_date), flight.departure.format(ShortDate), Modifier.weight(1f))
            GridValue(stringResource(R.string.results_boards), flight.boardingTime.format(Time), Modifier.weight(1f))
            GridValue(stringResource(R.string.results_gate), flight.gate, Modifier.weight(1f))
            GridValue(stringResource(R.string.results_class), cabin.short, Modifier.weight(1f))
        }

        // Barcode, rating, price
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Barcode(Modifier.size(width = 116.dp, height = 30.dp), color = colors.ink)
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(SkypassIcons.Star, contentDescription = null, tint = colors.star, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(String.format(Locale.US, "%.1f", flight.rating), style = SkypassTheme.typography.pill, color = colors.ink)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(usd(flight.priceUsd), style = SkypassTheme.typography.price, color = colors.ink)
                Text(stringResource(R.string.results_per_person), style = SkypassTheme.typography.caption, color = colors.text2)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(Durations.SLOW, easing = Motion.EmphasizedDecelerate)) + fadeIn(tween(Durations.SLOW)),
            exit = shrinkVertically(tween(Durations.BASE, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Durations.FAST)),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSoft))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    flight.perks.forEach { perk ->
                        Text(
                            perk,
                            modifier = Modifier.background(colors.surfaceSunken, Shapes.chip).padding(horizontal = 10.dp, vertical = 6.dp),
                            style = SkypassTheme.typography.captionStrong,
                            color = colors.ink,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SelectButton(selectLabel, usd(flight.priceUsd * adults), onSelect)
            }
        }
    }
}

@Composable
private fun GridValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Eyebrow(label, small = true)
        Text(value, style = SkypassTheme.typography.label, color = SkypassTheme.colors.ink)
    }
}

/** Bookmark that pops when it fills. */
@Composable
private fun BookmarkButton(saved: Boolean, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val inspection = LocalInspectionMode.current
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(saved) {
        if (saved && !inspection) {
            bounce.snapTo(0.6f)
            bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    val label = stringResource(if (saved) R.string.results_unsave else R.string.results_save)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick)
            .semantics { stateDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) SkypassIcons.BookmarkFilled else SkypassIcons.Bookmark,
            contentDescription = label,
            tint = colors.ink,
            modifier = Modifier.size(20.dp).graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            },
        )
    }
}

@Composable
private fun SelectButton(label: String, total: String, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .height(52.dp)
            .clip(Shapes.sm)
            .background(colors.ink)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = SkypassTheme.typography.label, color = colors.onInk)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(total, style = SkypassTheme.typography.label.copy(fontWeight = FontWeight.ExtraBold), color = colors.onInk)
            Icon(SkypassIcons.ArrowRight, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(16.dp))
        }
    }
}

/** Breathing ticket-shaped placeholder while results load. */
@Composable
internal fun TicketSkeleton(alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(SkypassTheme.colors.surface, Shapes.ticket),
    )
}
