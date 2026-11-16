package com.skypass.core.testing

import com.skypass.core.domain.repository.CarRepository
import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.DriverDetails
import com.skypass.core.model.CarType
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError

/** The board's eight Auckland cars. */
object TestCars {
    private fun car(id: String, type: CarType, name: String, seats: Int, bags: Int, usd: Int, supplier: String, file: String) =
        Car(id, "AKL", name, type, seats, bags, "Auto", usd, supplier, true, 0xFFF9DCD2, "cars/$file.png")

    val CERATO = car("c1", CarType.Compact, "Kia Cerato", 5, 2, 41, "Kiwi Rentals", "kia-cerato")
    val NOTE = car("c2", CarType.Compact, "Nissan Note", 5, 1, 36, "Harbour Car Hire", "nissan-note")
    val WRANGLER = car("c3", CarType.SUV, "Jeep Wrangler", 4, 2, 92, "Southern Drive", "jeep-wrangler")
    val JUKE = car("c4", CarType.SUV, "Nissan Juke", 5, 2, 58, "Kiwi Rentals", "nissan-juke")
    val MODEL_3 = car("c5", CarType.Electric, "Tesla Model 3", 5, 2, 84, "Volt NZ", "tesla-model-3")
    val MODEL_S = car("c6", CarType.Electric, "Tesla Model S", 5, 3, 112, "Volt NZ", "tesla-model-s")
    val MUSTANG = car("c7", CarType.Premium, "Ford Mustang", 4, 2, 129, "Harbour Car Hire", "ford-mustang")
    val CAMARO = car("c8", CarType.Premium, "Chevrolet Camaro", 4, 2, 119, "Kiwi Rentals", "chevrolet-camaro")
    val auckland = listOf(CERATO, NOTE, WRANGLER, JUKE, MODEL_3, MODEL_S, MUSTANG, CAMARO)
}

class FakeCarRepository(
    var cars: Map<String, List<Car>> = mapOf("AKL" to TestCars.auckland),
    var failure: AppError? = null,
) : CarRepository {
    var reserveError: ReservationError? = null
    val reservations = linkedMapOf<String, CarReservation>()

    /** What each rental was booked with, by attempt. */
    val details = linkedMapOf<String, DriverDetails>()
    val requestedAirports = mutableListOf<String>()

    override suspend fun cars(airportCode: String): Outcome<List<Car>, AppError> {
        requestedAirports += airportCode
        return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(cars[airportCode].orEmpty())
    }

    override suspend fun reserve(
        car: Car,
        rental: Rental,
        details: DriverDetails,
        attemptId: String,
    ): Outcome<CarReservation, ReservationError> {
        reserveError?.let { return Outcome.Failure(it) }
        reservations[attemptId]?.let { return Outcome.Success(it) }
        val fee = if (rental.sameReturn) 0 else Rental.ONE_WAY_FEE_USD
        val extras = details.extras.sumOf { it.perDayUsd } * rental.days
        val young = details.age.feePerDayUsd * rental.days
        return CarReservation(
            reference = "SKC${reservations.size + 1}AB",
            totalUsd = car.pricePerDayUsd * rental.days + fee + extras + young,
            days = rental.days,
            oneWayFeeUsd = fee,
            extrasUsd = extras,
            youngDriverFeeUsd = young,
        ).also { reservations[attemptId] = it; this.details[attemptId] = details }
            .let { Outcome.Success(it) }
    }
}
