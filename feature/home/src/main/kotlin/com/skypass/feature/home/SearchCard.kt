package com.skypass.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.CounterStepper
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.theme.Motion
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.SoftShadow
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.softShadow
import com.skypass.core.model.Airport
import com.skypass.core.model.FlightSearchQuery
import com.skypass.core.model.TripType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FieldDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

internal fun LocalDate.fieldLabel(): String = format(FieldDate)

/** The board's search card: trip type, route with swap, dates, adults, cabin, search. */
@Composable
internal fun SearchCard(
    search: FlightSearchQuery,
    swapTurns: Int,
    cabinMenuOpen: Boolean,
    fromIsNearby: Boolean,
    onEvent: (HomeEvent) -> Unit,
    onLaunchSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(SoftShadow.Hero, Shapes.heroLg)
            .background(SkypassTheme.colors.surface, Shapes.heroLg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedTabs(
            options = listOf(
                stringResource(R.string.home_one_way),
                stringResource(R.string.home_round_trip),
                stringResource(R.string.home_multi_city),
            ),
            selectedIndex = search.tripType.ordinal,
            onSelect = { onEvent(HomeEvent.TripTypeSelected(TripType.entries[it])) },
        )

        RouteBox(search.from, search.to, swapTurns, fromIsNearby, onEvent)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.home_depart), Modifier.weight(1f), onClick = { onEvent(HomeEvent.OpenDatePicker(DateField.Depart)) }) {
                AnimatedValue(search.departDate.fieldLabel())
            }
            val returnDate = search.returnDate
            Field(
                stringResource(R.string.home_return),
                Modifier.weight(1f),
                onClick = { onEvent(if (returnDate == null) HomeEvent.AddReturn else HomeEvent.OpenDatePicker(DateField.Return)) },
            ) {
                AnimatedContent(
                    targetState = returnDate,
                    transitionSpec = { fadeIn(tween(Motion.ENTER)) togetherWith fadeOut(tween(Motion.EXIT)) },
                    label = "return",
                ) { date ->
                    if (date == null) {
                        Text(stringResource(R.string.home_add_return), style = SkypassTheme.typography.label, color = SkypassTheme.colors.brand)
                    } else {
                        Text(date.fieldLabel(), style = SkypassTheme.typography.label, color = SkypassTheme.colors.ink)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.home_adults), Modifier.weight(1.25f), compact = true) {
                CounterStepper(
                    value = search.adults,
                    onDecrement = { onEvent(HomeEvent.RemoveAdult) },
                    onIncrement = { onEvent(HomeEvent.AddAdult) },
                    canDecrement = search.adults > FlightSearchQuery.MIN_ADULTS,
                    canIncrement = search.adults < FlightSearchQuery.MAX_ADULTS,
                    decrementLabel = stringResource(R.string.home_fewer_adults),
                    incrementLabel = stringResource(R.string.home_more_adults),
                )
            }
            CabinField(search.cabin, cabinMenuOpen, onEvent, Modifier.weight(1f))
        }

        TakeoffSearchButton(
            text = stringResource(R.string.home_search),
            enabled = search.isValid,
            onTakeoff = onLaunchSearch,
        )
    }
}

/** FROM / TO stacked in one outlined box, with the navy swap button riding the divider. */
@Composable
private fun RouteBox(from: Airport, to: Airport, swapTurns: Int, fromIsNearby: Boolean, onEvent: (HomeEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val description = stringResource(R.string.home_route_description, from.city, from.code, to.city, to.code)
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, colors.border, Shapes.fieldGroup)
            .clip(Shapes.fieldGroup),
    ) {
        Column(Modifier.semantics { contentDescription = description }) {
            val fromLabel = stringResource(R.string.home_from) + if (fromIsNearby) " · " + stringResource(R.string.home_near_you) else ""
            AirportRow(fromLabel, from, rollsUp = true) { onEvent(HomeEvent.OpenAirportPicker(AirportField.From)) }
            Box(Modifier.fillMaxWidth().padding(end = 76.dp).height(1.5.dp).background(colors.border))
            AirportRow(stringResource(R.string.home_to), to, rollsUp = false) { onEvent(HomeEvent.OpenAirportPicker(AirportField.To)) }
        }
        SwapButton(
            turns = swapTurns,
            onClick = { onEvent(HomeEvent.Swap) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        )
    }
}

@Composable
private fun AirportRow(label: String, airport: Airport, rollsUp: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Eyebrow(label)
        Spacer(Modifier.height(2.dp))
        // On swap the codes roll past each other, like a split-flap board.
        AnimatedContent(
            targetState = airport,
            transitionSpec = {
                val dir = if (rollsUp) 1 else -1
                (slideInVertically(tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate)) { dir * it } + fadeIn()) togetherWith
                    (slideOutVertically(tween(Motion.EXIT)) { -dir * it } + fadeOut(tween(Motion.EXIT)))
            },
            contentKey = { it.code },
            label = "airport",
        ) { a ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(a.code, style = SkypassTheme.typography.code, color = SkypassTheme.colors.ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    a.city,
                    style = SkypassTheme.typography.bodySm,
                    color = SkypassTheme.colors.text2,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 44dp navy circle with a 4dp white ring; each tap adds half a turn with a little overshoot. */
@Composable
private fun SwapButton(turns: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val rotation by animateFloatAsState(turns * 180f, tween(400, easing = Motion.Overshoot), label = "swap")
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.home_swap)
    Box(
        modifier
            .pressScale(interaction, 0.9f)
            .size(44.dp)
            .border(4.dp, colors.surface, CircleShape)
            .clip(CircleShape)
            .background(colors.ink)
            .clickable(interaction, null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SkypassIcons.Swap,
            contentDescription = null,
            tint = colors.onInk,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

/** Outlined field with a DM Mono label (DEPART, RETURN, ADULTS, CABIN). */
@Composable
internal fun Field(
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .border(1.5.dp, SkypassTheme.colors.border, Shapes.field)
            .clip(Shapes.field)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = if (compact) 6.dp else 14.dp, top = 11.dp, bottom = if (compact) 6.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp),
    ) {
        Eyebrow(label)
        content()
    }
}

/** Value that slides up when it changes (dates, cabin). */
@Composable
private fun AnimatedValue(text: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(Motion.ENTER, easing = Motion.EmphasizedDecelerate)) { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically(tween(Motion.EXIT)) { -it / 2 } + fadeOut(tween(Motion.EXIT)))
        },
        label = "value",
        modifier = modifier,
    ) { value ->
        Text(value, style = SkypassTheme.typography.label, color = SkypassTheme.colors.ink, maxLines = 1)
    }
}
