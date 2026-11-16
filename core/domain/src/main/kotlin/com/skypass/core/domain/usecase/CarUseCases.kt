package com.skypass.core.domain.usecase

import com.skypass.core.domain.repository.CarRepository
import com.skypass.core.domain.repository.TripRepository
import com.skypass.core.model.Airport
import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.CarType
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.RentalPlace
import com.skypass.core.model.ReservationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** "Auckland Airport (AKL)", the board's pick-up label. */
fun Airport.rentalPlace(): RentalPlace = RentalPlace("$city Airport ($code)", code)

/**
 * The board's trip card: pick up at the arrival airport at 14:00 on the day you land, and return
 * it there at 10:00 a week later. With no trip: Auckland, in 5 days.
 */
class ResolveRentalUseCase @Inject constructor(
    private val trips: TripRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(airports: List<Airport>): Rental {
        val trip = withTimeoutOrNull(TRIP_WAIT_MS) { trips.upcomingTrip().first() }
        val today = LocalDate.now(clock)
        val code: String
        val day: LocalDate
        if (trip != null) {
            val lands = trip.departure.plusMinutes(trip.durationMinutes.toLong()).toLocalDate()
            code = trip.destinationCode
            day = if (lands.isBefore(today)) today else lands
        } else {
            code = DEFAULT_AIRPORT
            day = today.plusDays(CreateDefaultSearchUseCase.DEFAULT_LEAD_DAYS)
        }
        val airport = airports.firstOrNull { it.code == code } ?: airports.firstOrNull { it.code == DEFAULT_AIRPORT }
        val place = airport?.rentalPlace() ?: RentalPlace("Auckland Airport (AKL)", DEFAULT_AIRPORT)
        return Rental(
            pickup = place,
            dropoff = place,
            pickupAt = day.atTime(PICKUP_TIME),
            dropoffAt = day.plusDays(DEFAULT_DAYS).atTime(DROPOFF_TIME),
        )
    }

    companion object {
        const val DEFAULT_AIRPORT = "AKL"
        const val DEFAULT_DAYS = 7L
        val PICKUP_TIME: LocalTime = LocalTime.of(14, 0)
        val DROPOFF_TIME: LocalTime = LocalTime.of(10, 0)
        private const val TRIP_WAIT_MS = 1_500L
    }
}

/**
 * Where the car can go back: the pick-up airport, the city centre (the board's
 * "Auckland CBD, Queen St"), or another airport in the same country.
 */
class DropOffPlacesUseCase @Inject constructor() {
    operator fun invoke(pickup: RentalPlace, airports: List<Airport>): List<RentalPlace> {
        val from = airports.firstOrNull { it.code == pickup.airportCode } ?: return listOf(pickup)
        val centre = RentalPlace(if (from.code == "AKL") "Auckland CBD, Queen St" else "${from.city} city centre")
        val others = airports
            .filter { it.country == from.country && it.code != from.code }
            .sortedBy { it.city }
            .map { it.rentalPlace() }
        return listOf(pickup, centre) + others
    }
}

class GetCarsUseCase @Inject constructor(private val cars: CarRepository) {
    suspend operator fun invoke(airportCode: String): Outcome<List<Car>, AppError> = cars.cars(airportCode)
}

class FilterCarsUseCase @Inject constructor() {
    operator fun invoke(all: List<Car>, type: CarType): List<Car> = all.filter { it.type == type }
}

data class RentalPrice(val days: Int, val carUsd: Int, val oneWayFeeUsd: Int) {
    val totalUsd: Int get() = carUsd + oneWayFeeUsd
}

/** The server's sum: daily rate × started days, plus the one-way fee when returned elsewhere. */
class PriceRentalUseCase @Inject constructor() {
    operator fun invoke(car: Car, rental: Rental): RentalPrice = RentalPrice(
        days = rental.days,
        carUsd = car.pricePerDayUsd * rental.days,
        oneWayFeeUsd = if (rental.sameReturn) 0 else Rental.ONE_WAY_FEE_USD,
    )
}

enum class RentalProblem { PickupInPast, DropoffBeforePickup, TooLong }

class ValidateRentalUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(rental: Rental): RentalProblem? = when {
        rental.pickupAt.toLocalDate().isBefore(LocalDate.now(clock)) -> RentalProblem.PickupInPast
        !rental.dropoffAt.isAfter(rental.pickupAt) -> RentalProblem.DropoffBeforePickup
        rental.days > Rental.MAX_DAYS -> RentalProblem.TooLong
        else -> null
    }
}

class ReserveCarUseCase @Inject constructor(private val cars: CarRepository) {
    suspend operator fun invoke(car: Car, rental: Rental, attemptId: String): Outcome<CarReservation, ReservationError> =
        cars.reserve(car, rental, attemptId)
}
