package com.skypass.feature.cars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skypass.core.designsystem.component.ContactFields
import com.skypass.core.designsystem.component.Eyebrow
import com.skypass.core.designsystem.component.FormSection
import com.skypass.core.designsystem.component.Hairline
import com.skypass.core.designsystem.component.InfoCard
import com.skypass.core.designsystem.component.LabeledValue
import com.skypass.core.designsystem.component.PolicyNote
import com.skypass.core.designsystem.component.PriceLine
import com.skypass.core.designsystem.component.PrimaryButton
import com.skypass.core.designsystem.component.SegmentedTabs
import com.skypass.core.designsystem.component.SkypassTextField
import com.skypass.core.designsystem.component.SkypassToggle
import com.skypass.core.designsystem.component.carImageRes
import com.skypass.core.designsystem.theme.Shapes
import com.skypass.core.designsystem.theme.SkypassTheme
import com.skypass.core.domain.usecase.ContactProblem
import com.skypass.core.model.Car
import com.skypass.core.model.CarExtra
import com.skypass.core.model.DriverAge
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError

/** "Driver & extras": who drives, their flight, add-ons, and the full price before booking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckoutSheet(sheet: CheckoutSheet, car: Car, rental: Rental, state: CarsUiState, onEvent: (CarsEvent) -> Unit) {
    val colors = SkypassTheme.colors
    val details = sheet.details
    val price = sheet.price
    ModalBottomSheet(
        onDismissRequest = { onEvent(CarsEvent.DismissCheckout) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.cars_checkout_title), style = SkypassTheme.typography.figure, color = colors.ink)
                Text("${car.name} · ${car.supplier}", style = SkypassTheme.typography.bodySm, color = colors.text2)
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RentalSummary(car, rental)

                FormSection(stringResource(R.string.cars_driver)) {
                    ContactFields(
                        contact = details.driver,
                        onChange = { onEvent(CarsEvent.DriverChanged(it)) },
                        nameError = stringResource(R.string.cars_problem_name).takeIf { ContactProblem.NameMissing in sheet.problems },
                        emailError = stringResource(R.string.cars_problem_email).takeIf { ContactProblem.EmailInvalid in sheet.problems },
                        phoneError = stringResource(R.string.cars_problem_phone).takeIf { ContactProblem.PhoneInvalid in sheet.problems },
                    )
                    Eyebrow(stringResource(R.string.cars_driver_age))
                    SegmentedTabs(
                        options = DriverAge.entries.map { it.label },
                        selectedIndex = details.age.ordinal,
                        onSelect = { onEvent(CarsEvent.SelectAge(DriverAge.entries[it])) },
                    )
                    if (details.age == DriverAge.Young) {
                        Text(
                            stringResource(R.string.cars_young_driver, usd(DriverAge.Young.feePerDayUsd)),
                            style = SkypassTheme.typography.caption,
                            color = colors.text2,
                        )
                    }
                    SkypassTextField(
                        value = details.flightNumber,
                        onValueChange = { onEvent(CarsEvent.FlightChanged(it)) },
                        label = stringResource(R.string.cars_flight),
                        placeholder = stringResource(R.string.cars_flight_hint),
                        error = stringResource(R.string.cars_problem_flight).takeIf { sheet.flightInvalid },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    )
                    Text(stringResource(R.string.cars_flight_note), style = SkypassTheme.typography.caption, color = colors.text2)
                }

                FormSection(stringResource(R.string.cars_extras)) {
                    CarExtra.entries.forEach { extra ->
                        ExtraRow(extra, checked = extra in details.extras, onToggle = { onEvent(CarsEvent.ToggleExtra(extra)) })
                    }
                }

                FormSection(stringResource(R.string.cars_price)) {
                    InfoCard {
                        PriceLine(
                            car.name,
                            usd(price.carUsd),
                            detail = pluralStringResource(R.plurals.cars_rate_days, price.days, usd(car.pricePerDayUsd), price.days),
                        )
                        details.extras.sortedBy { it.ordinal }.forEach { extra ->
                            PriceLine(
                                extra.label,
                                usd(extra.perDayUsd * price.days),
                                detail = pluralStringResource(R.plurals.cars_rate_days, price.days, usd(extra.perDayUsd), price.days),
                            )
                        }
                        if (price.youngDriverFeeUsd > 0) PriceLine(stringResource(R.string.cars_young_fee), usd(price.youngDriverFeeUsd))
                        if (price.oneWayFeeUsd > 0) PriceLine(stringResource(R.string.cars_one_way), usd(price.oneWayFeeUsd))
                        Hairline()
                        PriceLine(stringResource(R.string.cars_total), usd(price.totalUsd), total = true)
                    }
                    if (car.freeCancellation) {
                        PolicyNote(stringResource(R.string.cars_policy_free, rental.pickupAt.minusHours(48).rentalLabel()), positive = true)
                    }
                    PolicyNote(stringResource(R.string.cars_policy_licence), positive = false)
                    PolicyNote(stringResource(R.string.cars_policy_pay), positive = false)
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.reserveError?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                ReservationError.InvalidDates -> R.string.cars_error_dates
                                ReservationError.InvalidDetails -> R.string.cars_error_details
                                ReservationError.Network -> R.string.cars_error_network
                                else -> R.string.cars_error_unknown
                            },
                        ),
                        style = SkypassTheme.typography.bodySm,
                        color = colors.danger,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.cars_confirm, usd(price.totalUsd)),
                    onClick = { onEvent(CarsEvent.Confirm) },
                    loading = state.reserve == CarReserveState.Reserving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RentalSummary(car: Car, rental: Rental) {
    val colors = SkypassTheme.colors
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 84.dp, height = 56.dp).clip(Shapes.tile).background(Color(car.stageColor)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(carImageRes(car.imagePath)), contentDescription = null, modifier = Modifier.padding(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(car.name, style = SkypassTheme.typography.label, color = colors.ink)
                Text(
                    stringResource(R.string.cars_specs, car.type.label, car.seats, car.bags, car.transmission),
                    style = SkypassTheme.typography.caption,
                    color = colors.text2,
                )
            }
        }
        Hairline()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue(stringResource(R.string.cars_pickup), rental.pickupAt.rentalLabel(), Modifier.weight(1f), detail = rental.pickup.label)
            LabeledValue(stringResource(R.string.cars_dropoff), rental.dropoffAt.rentalLabel(), Modifier.weight(1f), detail = rental.dropoff.label)
        }
    }
}

/** An extra with its daily price and a toggle; the whole row is the touch target. */
@Composable
private fun ExtraRow(extra: CarExtra, checked: Boolean, onToggle: () -> Unit) {
    val colors = SkypassTheme.colors
    val border by animateColorAsState(if (checked) colors.ink else colors.border, tween(200), label = "extraBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.sm)
            .background(colors.surface)
            .border(1.5.dp, border, Shapes.sm)
            .toggleable(checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(extra.label, style = SkypassTheme.typography.label, color = colors.ink)
            Text(extra.detail, style = SkypassTheme.typography.caption, color = colors.text2)
        }
        Text(stringResource(R.string.cars_extra_per_day, usd(extra.perDayUsd)), style = SkypassTheme.typography.labelMd, color = colors.ink)
        Box(Modifier.width(48.dp).height(28.dp), contentAlignment = Alignment.Center) {
            SkypassToggle(checked = checked, onCheckedChange = null)
        }
    }
}
