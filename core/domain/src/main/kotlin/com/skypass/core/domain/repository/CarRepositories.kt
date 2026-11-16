package com.skypass.core.domain.repository

import com.skypass.core.model.AppError
import com.skypass.core.model.Car
import com.skypass.core.model.CarReservation
import com.skypass.core.model.Outcome
import com.skypass.core.model.Rental
import com.skypass.core.model.ReservationError

interface CarRepository {
    /** Cars to pick up at [airportCode], in the board's order. */
    suspend fun cars(airportCode: String): Outcome<List<Car>, AppError>

    /** Books [car] for [rental]. Priced on the server; the same [attemptId] never books twice. */
    suspend fun reserve(car: Car, rental: Rental, attemptId: String): Outcome<CarReservation, ReservationError>
}
