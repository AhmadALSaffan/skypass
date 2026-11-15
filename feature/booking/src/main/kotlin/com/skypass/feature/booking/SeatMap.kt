package com.skypass.feature.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.theme.Durations
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.model.Cabin
import com.skypass.core.model.Passenger
import com.skypass.core.model.Seat
import com.skypass.core.model.SeatMap
import com.skypass.core.model.SeatType

private val NoseShape = RoundedCornerShape(topStart = 140.dp, topEnd = 140.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
private val TailShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 36.dp, bottomEnd = 36.dp)
private val AisleWidth = 18.dp
private val SeatGap = 4.dp

/**
 * The whole cabin as one fuselage, one lazy item per row so even an A380's 460 seats scroll
 * smoothly inside the booking list. Seats show each traveller's initials once taken by them.
 */
internal fun LazyListScope.seatMapItems(
    leg: LegState,
    travellers: List<Passenger>,
    activeTraveller: Int,
    onSelect: (String) -> Unit,
) {
    val map = leg.map ?: return
    item(key = "fuselage-nose-${leg.instanceId}") { FuselageNose(map) }
    items(map.rows, key = { "row-${leg.instanceId}-$it" }) { row ->
        SeatRowItem(map, row, leg, travellers, activeTraveller, onSelect)
    }
    item(key = "fuselage-tail-${leg.instanceId}") {
        Box(Modifier.fillMaxWidth().height(28.dp).background(SkypassTheme.colors.surface, TailShape))
    }
}

/** Rounded nose with the cabin name and the column letters. */
@Composable
private fun FuselageNose(map: SeatMap) {
    val colors = SkypassTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, NoseShape)
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.booking_cabin_rows, map.cabin.label, map.rows.first(), map.rows.last()),
            style = SkypassTheme.typography.eyebrow,
            color = colors.text2,
        )
        LetterRow(map)
    }
}

@Composable
private fun LetterRow(map: SeatMap) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val seatSize = seatSize(maxWidth, map)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            map.blocks.forEachIndexed { b, block ->
                if (b > 0) Spacer(Modifier.width(AisleWidth))
                block.forEachIndexed { i, letter ->
                    if (i > 0) Spacer(Modifier.width(SeatGap))
                    Box(Modifier.width(seatSize), contentAlignment = Alignment.Center) {
                        Text(letter, style = SkypassTheme.typography.monoSm, color = SkypassTheme.colors.text2)
                    }
                }
            }
        }
    }
}

/** Seat edge so the widest row fits the screen: between 24 and 40dp. */
private fun seatSize(width: Dp, map: SeatMap): Dp {
    val seats = map.blocks.sumOf { it.size }
    val gaps = SeatGap * (seats - map.blocks.size) + AisleWidth * (map.blocks.size - 1)
    return min(40.dp, (width - gaps - 36.dp) / seats).coerceAtLeast(24.dp)
}

@Composable
private fun SeatRowItem(
    map: SeatMap,
    row: Int,
    leg: LegState,
    travellers: List<Passenger>,
    activeTraveller: Int,
    onSelect: (String) -> Unit,
) {
    val colors = SkypassTheme.colors
    val exit = map.seats.any { it.row == row && it.exitRow }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        val seatSize = seatSize(maxWidth, map)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ExitMark(exit)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                map.blocks.forEachIndexed { b, block ->
                    if (b > 0) {
                        Box(Modifier.width(AisleWidth), contentAlignment = Alignment.Center) {
                            // Row number in the first aisle, like the board.
                            if (b == 1) Text(row.toString(), style = SkypassTheme.typography.eyebrowSm, color = colors.text2)
                        }
                    }
                    block.forEachIndexed { i, letter ->
                        if (i > 0) Spacer(Modifier.width(SeatGap))
                        val seat = map.seat("$row$letter")
                        if (seat == null) {
                            Spacer(Modifier.size(seatSize))
                        } else {
                            val holder = leg.travellerIn(seat.code)
                            SeatButton(
                                seat = seat,
                                size = seatSize,
                                holderInitials = holder?.let { travellers.getOrNull(it)?.initials ?: "${it + 1}" },
                                isActiveHolder = holder == activeTraveller,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
            ExitMark(exit)
        }
    }
}

/** Small teal EXIT tab on both walls of an exit row. */
@Composable
private fun ExitMark(exit: Boolean) {
    Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
        if (exit) {
            Text(
                "EXIT",
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
                style = SkypassTheme.typography.eyebrowSm.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                color = SkypassTheme.colors.brand,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SeatButton(
    seat: Seat,
    size: Dp,
    holderInitials: String?,
    isActiveHolder: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = SkypassTheme.colors
    val haptics = LocalHapticFeedback.current
    val inspection = LocalInspectionMode.current
    val mine = holderInitials != null
    val upgrade = seat.type == SeatType.Premium || seat.type == SeatType.Business || seat.type == SeatType.First
    val (bg, border, text) = when {
        mine && isActiveHolder -> Triple(colors.ink, colors.ink, colors.onInk)
        mine -> Triple(colors.brand, colors.brand, colors.onBrand)
        seat.taken -> Triple(colors.border, colors.border, Color.Transparent)
        seat.type == SeatType.Legroom -> Triple(colors.mintPale, colors.mintBorder, colors.onPastel2)
        upgrade -> Triple(colors.lavender, colors.lavender, colors.onPastel2)
        else -> Triple(colors.surface, colors.borderStrong, colors.text2)
    }
    val bgAnim by animateColorAsState(bg, tween(Durations.FAST), label = "seatBg")
    val borderAnim by animateColorAsState(border, tween(Durations.FAST), label = "seatBorder")
    // Taking a seat makes it "sit down": a quick squash and spring back.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(mine, isActiveHolder) {
        if (mine && !inspection) {
            bounce.snapTo(0.8f)
            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    val state = when {
        mine -> stringResource(R.string.booking_seat_selected_by, holderInitials.orEmpty())
        seat.taken -> stringResource(R.string.booking_seat_taken)
        seat.extraFeeUsd > 0 -> stringResource(R.string.booking_seat_free_legroom, usd(seat.extraFeeUsd))
        else -> stringResource(R.string.booking_seat_free)
    }
    val description = stringResource(R.string.booking_seat_description, seat.code, state)
    Box(
        Modifier
            .size(width = size, height = size * 1.05f)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clip(Shapes.seat)
            .background(bgAnim)
            .border(1.5.dp, borderAnim, Shapes.seat)
            .selectable(selected = mine, enabled = !seat.taken || mine, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(seat.code)
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val label = holderInitials ?: seat.letter.takeIf { !seat.taken }
        if (label != null) {
            Text(
                label,
                style = SkypassTheme.typography.pill.copy(fontSize = (size.value * 0.34f).sp),
                color = text,
                maxLines = 1,
            )
        }
    }
}

/** Free · Legroom · Yours · Taken swatches. */
@Composable
internal fun SeatLegend(showUpgrade: Boolean, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (showUpgrade) {
            LegendItem(colors.lavender, colors.lavender, stringResource(R.string.booking_free))
        } else {
            LegendItem(colors.surface, colors.borderStrong, stringResource(R.string.booking_free))
            LegendItem(colors.mintPale, colors.mintBorder, stringResource(R.string.booking_legroom))
        }
        LegendItem(colors.ink, colors.ink, stringResource(R.string.booking_yours))
        LegendItem(colors.border, colors.border, stringResource(R.string.booking_taken))
    }
}

@Composable
private fun LegendItem(fill: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(fill, RoundedCornerShape(4.dp)).border(1.5.dp, border, RoundedCornerShape(4.dp)))
        Text(label, style = SkypassTheme.typography.caption, color = SkypassTheme.colors.text2)
    }
}

internal val Cabin.isUpgradeCabin: Boolean get() = this != Cabin.Economy
