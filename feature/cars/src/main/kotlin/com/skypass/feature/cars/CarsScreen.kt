package com.skypass.feature.cars

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.FilterChip
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SkypassTextButton
import com.skypass.core.designsystem.component.SkypassToggle
import com.skypass.core.designsystem.component.SkypassTopBar
import com.skypass.core.designsystem.icon.SkypassIcons
import com.skypass.core.designsystem.component.carImageRes
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.designsystem.theme.animateCountUp
import com.skypass.core.designsystem.theme.pressScale
import com.skypass.core.designsystem.theme.reveal
import com.skypass.core.domain.usecase.RentalProblem
import com.skypass.core.model.Car
import com.skypass.core.model.CarType
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun usd(amount: Int): String =
    NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(amount)

private val WhenFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

/** "Fri 20 Nov · 14:00", as on the board. */
internal fun LocalDateTime.rentalLabel(): String = format(WhenFormat)

@Composable
fun CarsRoute(onBack: () -> Unit, onOpenMyTrips: () -> Unit, viewModel: CarsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openTrips by rememberUpdatedState(onOpenMyTrips)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CarsEffect.OpenMyTrips -> openTrips()
            }
        }
    }
    CarsScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun CarsScreen(state: CarsUiState, onEvent: (CarsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        SkypassTopBar(stringResource(R.string.cars_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Always present (even before the rental resolves) so the list never opens scrolled.
            item(key = "trip") {
                val rental = state.rental
                if (rental != null) {
                    TripCard(rental, state.problem, onEvent, Modifier.reveal(0))
                } else {
                    Box(Modifier.fillMaxWidth().height(236.dp).clip(Shapes.ticket).background(SkypassTheme.colors.surface))
                }
            }
            item(key = "types") { TypeChips(state.type, onEvent, Modifier.reveal(1)) }
            when {
                state.error != null -> item(key = "error") { OfflineCard(onRetry = { onEvent(CarsEvent.Retry) }) }
                state.loading -> items(2, key = { "skeleton-$it" }) { SkeletonCard(it) }
                state.isEmpty -> item(key = "empty") {
                    Text(
                        stringResource(R.string.cars_empty),
                        style = SkypassTheme.typography.bodySm,
                        color = colors.text2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).animateItem(),
                    )
                }
                else -> items(state.cars, key = { it.id }) { car ->
                    CarCard(
                        car = car,
                        selected = car.id == state.selected?.id,
                        onSelect = { onEvent(CarsEvent.SelectCar(car)) },
                        modifier = Modifier.animateItem().reveal(2),
                    )
                }
            }
        }
        Footer(state, onEvent)
    }

    state.picker?.let { PlacePickerSheet(it, state.rental, onEvent) }
    if (state.pickingDates) state.rental?.let { RentalDatesDialog(it, state.firstBookableDate, onEvent) }
    state.checkout?.let { sheet ->
        val car = state.selected
        val rental = state.rental
        if (car != null && rental != null) CheckoutSheet(sheet, car, rental, state, onEvent)
    }
    (state.reserve as? CarReserveState.Reserved)?.let { ReservedSheet(it, state.rental, onEvent) }
}

// ---------------------------------------------------------------------------------------------
// Trip card (board 10)
// ---------------------------------------------------------------------------------------------

@Composable
private fun TripCard(rental: Rental, problem: RentalProblem?, onEvent: (CarsEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val divider = colors.borderSoft
    Column(modifier.fillMaxWidth().clip(Shapes.ticket).background(colors.surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
        PlaceRow(stringResource(R.string.cars_pickup), rental.pickup.label, filled = false) { onEvent(CarsEvent.PickPickup) }
        HorizontalDivider(color = divider)
        PlaceRow(
            stringResource(R.string.cars_dropoff),
            if (rental.sameReturn) stringResource(R.string.cars_same_as_pickup) else rental.dropoff.label,
            filled = true,
        ) { onEvent(CarsEvent.PickDropoff) }
        HorizontalDivider(color = divider)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.xs)
                .clickable(role = Role.Button) { onEvent(CarsEvent.PickDates) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WhenCell(stringResource(R.string.cars_from), rental.pickupAt.rentalLabel(), Modifier.weight(1f))
            WhenCell(stringResource(R.string.cars_until), rental.dropoffAt.rentalLabel(), Modifier.weight(1f))
        }
        AnimatedVisibility(problem != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                stringResource(
                    when (problem) {
                        RentalProblem.PickupInPast -> R.string.cars_problem_past
                        RentalProblem.DropoffBeforePickup -> R.string.cars_problem_order
                        else -> R.string.cars_problem_long
                    },
                ),
                style = SkypassTheme.typography.caption,
                color = colors.danger,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider(color = divider)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.cars_same_return),
                style = SkypassTheme.typography.labelMd,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            SkypassToggle(rental.sameReturn, onCheckedChange = { onEvent(CarsEvent.SetSameReturn(it)) })
        }
    }
}

@Composable
private fun PlaceRow(label: String, value: String, filled: Boolean, onClick: () -> Unit) {
    val colors = SkypassTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.xs)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .then(if (filled) Modifier.background(colors.ink, CircleShape) else Modifier.border(2.5.dp, colors.ink, CircleShape)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(label)
            Text(value, style = SkypassTheme.typography.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(SkypassIcons.ChevronRight, contentDescription = null, tint = colors.text3, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun WhenCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        Text(value, style = SkypassTheme.typography.labelMd.copy(fontWeight = FontWeight.Bold), color = SkypassTheme.colors.ink)
    }
}

@Composable
private fun TypeChips(selected: CarType, onEvent: (CarsEvent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CarType.entries.forEach { type ->
            FilterChip(type.label, selected = type == selected, onClick = { onEvent(CarsEvent.SelectType(type)) }, modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Car card
// ---------------------------------------------------------------------------------------------

@Composable
private fun CarCard(car: Car, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SkypassTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val border by animateColorAsState(if (selected) colors.brand else colors.surface, tween(150), label = "carBorder")
    // The selected car settles onto its shadow.
    val lift by animateFloatAsState(if (selected) 0f else 4f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "carLift")
    Column(
        modifier
            .pressScale(interaction, pressed = 0.98f)
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(2.dp, border, Shapes.card)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onSelect),
    ) {
        Box(
            Modifier
                .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(car.stageColor)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // A soft, blurred shadow under the wheels (drawn, so it works below Android 12 too).
            Canvas(Modifier.fillMaxWidth(0.72f).height(16.dp).padding(bottom = 0.dp).align(Alignment.BottomCenter).graphicsLayer { translationY = -16.dp.toPx() }) {
                scale(scaleX = size.width / size.height, scaleY = 1f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color(0x661C2550),
                            1f to Color.Transparent,
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.height / 2,
                        ),
                        radius = size.height / 2,
                    )
                }
            }
            Image(
                painter = painterResource(carImageRes(car.imagePath)),
                contentDescription = car.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(128.dp)
                    .padding(bottom = 12.dp)
                    .graphicsLayer { translationY = -lift.dp.toPx() },
            )
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(car.name, style = SkypassTheme.typography.titleSm, color = colors.ink)
                    Text(
                        stringResource(
                            R.string.cars_or_similar,
                            car.seats,
                            car.transmission,
                            pluralStringResource(R.plurals.cars_bags, car.bags, car.bags),
                        ),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(usd(car.pricePerDayUsd), style = SkypassTheme.typography.figureSm, color = colors.ink)
                    Text(stringResource(R.string.cars_per_day), style = SkypassTheme.typography.caption, color = colors.text2, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.cars_supplier_free, car.supplier),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                    modifier = Modifier.weight(1f),
                )
                val bg by animateColorAsState(if (selected) colors.brand else colors.bg, tween(150), label = "selBg")
                val fg by animateColorAsState(if (selected) colors.onBrand else colors.ink, tween(150), label = "selFg")
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(13.dp)).background(bg).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (selected) R.string.cars_selected else R.string.cars_select),
                        style = SkypassTheme.typography.labelSm,
                        color = fg,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sticky footer
// ---------------------------------------------------------------------------------------------

@Composable
private fun Footer(state: CarsUiState, onEvent: (CarsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val car = state.selected
    val price = state.price
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = colors.border)
        state.reserveError?.takeIf { state.checkout == null }?.let { error ->
            Text(
                stringResource(
                    when (error) {
                        ReservationError.InvalidDates -> R.string.cars_error_dates
                        ReservationError.Network -> R.string.cars_error_network
                        else -> R.string.cars_error_unknown
                    },
                ),
                style = SkypassTheme.typography.caption,
                color = colors.danger,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp).clickable { onEvent(CarsEvent.DismissReserveError) },
            )
        }
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (car != null && price != null) {
                    Text(
                        pluralStringResource(R.plurals.cars_days, price.days, car.name, price.days),
                        style = SkypassTheme.typography.caption,
                        color = colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(usd(animateCountUp(price.totalUsd, durationMs = 450)), style = SkypassTheme.typography.figure, color = colors.ink)
                    if (price.oneWayFeeUsd > 0) {
                        Text(stringResource(R.string.cars_one_way_fee, usd(price.oneWayFeeUsd)), style = SkypassTheme.typography.caption, color = colors.text2)
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.cars_reserve),
                onClick = { onEvent(CarsEvent.Reserve) },
                enabled = state.canReserve,
                height = 54.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------------------------

@Composable
private fun SkeletonCard(index: Int) {
    val colors = SkypassTheme.colors
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = index * 150), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = pulse }.clip(Shapes.card).background(colors.surface).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(if (index == 0) colors.peach else colors.sky))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(140.dp).height(14.dp).background(colors.border, Shapes.xs))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(200.dp).height(10.dp).background(colors.borderSoft, Shapes.xs))
    }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit) {
    val colors = SkypassTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.surface, Shapes.heroLg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SkypassIcons.CloudOff, contentDescription = null, tint = colors.text2, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.cars_offline_title), style = SkypassTheme.typography.label, color = colors.ink)
        Text(stringResource(R.string.cars_offline_body), style = SkypassTheme.typography.bodySm, color = colors.text2, textAlign = TextAlign.Center)
        SkypassTextButton(stringResource(R.string.cars_retry), onRetry, color = colors.brand)
    }
}
